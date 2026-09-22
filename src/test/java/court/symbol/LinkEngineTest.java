package court.symbol;

import court.symbol.elf.BinaryParser;
import court.symbol.elf.ElfReadException;
import court.symbol.elf.ParsedBinary;
import court.symbol.engine.LinkEngine;
import court.symbol.fixture.ArchiveFixtureBuilder;
import court.symbol.fixture.ElfFixtureBuilder;
import court.symbol.model.PlatformPolicy;
import court.symbol.model.ResolutionDecision;
import court.symbol.model.ResolutionInput;
import court.symbol.model.Scheme;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkEngineTest {
    private final LinkEngine engine = new LinkEngine();
    private final BinaryParser parser = new BinaryParser();

    @Test
    void strongDefinitionBeatsWeakDefinition() {
        ParsedBinary user = objectWithUndefined("user.o", "fn", 1);
        ParsedBinary weak = new ElfFixtureBuilder().object()
                .section(".text", new byte[8]).defined("fn", ".text", 2, 0, 0x20, 8)
                .buildParsed("weak.o");
        ParsedBinary strong = new ElfFixtureBuilder().object()
                .section(".text", new byte[8]).defined("fn", ".text", 1, 0, 0x10, 8)
                .buildParsed("strong.o");
        ResolutionDecision decision = decide(Map.of("user", user, "weak", weak, "strong", strong),
                List.of("user", "weak", "strong"), "fn", "");
        assertEquals("RESOLVED", decision.status());
        assertEquals("strong.o", decision.chosen().get(0).unit().sourceFile());
        assertTrue(decision.rejected().stream().anyMatch(item -> item.provider().unit().sourceFile().equals("weak.o")));
    }

    @Test
    void weakUndefinedReferenceRemainsWeakWhenNoArchiveIsExtracted() {
        ParsedBinary user = objectWithUndefined("user.o", "helper", 2);
        byte[] archive = new ArchiveFixtureBuilder().member("helper.o", new ElfFixtureBuilder().object()
                .section(".text", new byte[8]).defined("helper", ".text", 2, 0, 0x20, 8).build()).build();
        ParsedBinary parsedArchive = parser.parse(archive, "libweak.a");
        ResolutionDecision decision = decide(Map.of("user", user, "archive", parsedArchive),
                List.of("user", "archive"), "helper", "");
        assertEquals("WEAK_UNDEFINED", decision.status());
        assertFalse(parsedArchive.archiveMembers.isEmpty());
    }

    @Test
    void versionAliasesResolveDefaultAndNonDefaultRequestsIndependently() {
        ParsedBinary library = new ElfFixtureBuilder().shared("libv.so.1")
                .section(".data", new byte[8])
                .dynamicDefined("api", ".data", 1, 0, 0x100, 8, "LIBV_1.0", true)
                .dynamicDefined("api", ".data", 1, 0, 0x110, 8, "LIBV_1.1", false)
                .buildParsed("libv.so.1");
        ParsedBinary caller = new ElfFixtureBuilder().shared("caller.so")
                .needed("libv.so.1")
                .section(".text", new byte[16])
                .dynamicNeed("api", "libv.so.1", "LIBV_1.1", false)
                .dynamicReloc(".text", "api", 7, 0, 0)
                .buildParsed("caller.so");
        Scheme scheme = run(Map.of("library", library, "caller", caller),
                List.of("caller", "library"), PlatformPolicy.GNU_LOAD_ORDER);
        ResolutionDecision decision = decision(scheme, "api", "LIBV_1.1");
        assertEquals("RESOLVED", decision.status());
        assertEquals("LIBV_1.1", decision.chosen().get(0).versionName());
        assertTrue(decision.rejected().stream().anyMatch(item -> item.provider().versionName().equals("LIBV_1.0")));
    }

    @Test
    void hiddenSymbolIsLocalToDefiningObjectAndRejectedFromOthers() {
        ParsedBinary owner = new ElfFixtureBuilder().object()
                .section(".text", new byte[8])
                .defined("secret", ".text", 1, 2, 0x30, 8)
                .reloc(".text", "secret", 2, 0, 0)
                .buildParsed("owner.o");
        ParsedBinary other = objectWithUndefined("other.o", "secret", 1);
        Scheme sameUnitScheme = run(Map.of("owner", owner), List.of("owner"), PlatformPolicy.GNU_LOAD_ORDER);
        assertEquals("RESOLVED", decision(sameUnitScheme, "secret", "").status());
        Scheme crossUnitScheme = run(Map.of("owner", owner, "other", other),
                List.of("owner", "other"), PlatformPolicy.GNU_LOAD_ORDER);
        ResolutionDecision cross = crossUnitScheme.decisions.stream()
                .filter(item -> item.name().equals("secret"))
                .filter(item -> item.references().get(0).unit().sourceFile().equals("other.o"))
                .findFirst().orElseThrow();
        assertEquals("UNRESOLVED", cross.status());
        assertTrue(cross.rejected().get(0).reason().contains("hidden"));
    }

    @Test
    void strongDefinitionOverridesCommonSymbol() {
        ParsedBinary user = objectWithUndefined("user.o", "state", 1);
        ParsedBinary common = new ElfFixtureBuilder().object().common("state", 32, 1).buildParsed("common.o");
        ParsedBinary strong = new ElfFixtureBuilder().object()
                .section(".bss", new byte[0]).defined("state", ".bss", 1, 0, 0x50, 32)
                .buildParsed("strong.o");
        ResolutionDecision decision = decide(Map.of("user", user, "common", common, "strong", strong),
                List.of("user", "common", "strong"), "state", "");
        assertEquals("RESOLVED", decision.status());
        assertEquals("strong.o", decision.chosen().get(0).unit().sourceFile());
    }

    @Test
    void comdatDuplicatesAreDeduplicatedWithoutSwallowingEarlierProvider() {
        ParsedBinary user = objectWithUndefined("user.o", "inline_fn", 1);
        ParsedBinary first = comdatObject("first.o", 0x100);
        ParsedBinary second = comdatObject("second.o", 0x200);
        ResolutionDecision decision = decide(Map.of("user", user, "first", first, "second", second),
                List.of("user", "first", "second"), "inline_fn", "");
        assertEquals("RESOLVED", decision.status());
        assertEquals("first.o", decision.chosen().get(0).unit().sourceFile());
        assertTrue(decision.rejected().stream().anyMatch(item -> item.reason().contains("COMDAT")));
    }

    @Test
    void archiveResolvesCyclicMemberDependenciesByRepeatedLazyScans() {
        ParsedBinary user = objectWithUndefined("user.o", "start", 1);
        ParsedBinary a = new ElfFixtureBuilder().object().section(".text", new byte[8])
                .defined("start", ".text", 1, 0, 0x10, 8).undefined("middle", 1)
                .reloc(".text", "middle", 2, 0, 0).buildParsed("a.o");
        ParsedBinary b = new ElfFixtureBuilder().object().section(".text", new byte[8])
                .defined("middle", ".text", 1, 0, 0x20, 8).undefined("finish", 1)
                .reloc(".text", "finish", 2, 0, 0).undefined("start", 1).buildParsed("b.o");
        ParsedBinary c = new ElfFixtureBuilder().object().section(".text", new byte[8])
                .defined("finish", ".text", 1, 0, 0x30, 8).buildParsed("c.o");
        byte[] archive = new ArchiveFixtureBuilder().member("a.o", rebuild(a))
                .member("b.o", rebuild(b)).member("c.o", rebuild(c)).build();
        ParsedBinary parsedArchive = parser.parse(archive, "libcycle.a");
        Scheme scheme = run(Map.of("user", user, "archive", parsedArchive), List.of("user", "archive"),
                PlatformPolicy.GNU_LOAD_ORDER);
        assertEquals(3, scheme.activeUnits.size() - 1);
        assertEquals("RESOLVED", decision(scheme, "middle", "").status());
        assertEquals("RESOLVED", decision(scheme, "finish", "").status());
    }

    @Test
    void unsatisfiedVersionRequirementIsNotBoundToDifferentVersion() {
        ParsedBinary library = new ElfFixtureBuilder().shared("libv.so.1")
                .section(".text", new byte[8]).dynamicDefined("api", ".text", 1, 0, 0x100, 8,
                        "LIBV_1.0", true).buildParsed("libv.so.1");
        ParsedBinary caller = new ElfFixtureBuilder().shared("caller.so").needed("libv.so.1")
                .section(".text", new byte[8]).dynamicNeed("api", "libv.so.1", "LIBV_9.9", false)
                .dynamicReloc(".text", "api", 7, 0, 0).buildParsed("caller.so");
        ResolutionDecision decision = decide(Map.of("library", library, "caller", caller),
                List.of("caller", "library"), "api", "LIBV_9.9");
        assertEquals("UNRESOLVED", decision.status());
        assertTrue(decision.rejected().get(0).reason().contains("请求版本"));
    }

    @Test
    void equalStrongCandidatesRemainAmbiguous() {
        ParsedBinary user = objectWithUndefined("user.o", "dup", 1);
        ParsedBinary a = new ElfFixtureBuilder().object().section(".text", new byte[8])
                .defined("dup", ".text", 1, 0, 0x10, 8).buildParsed("a.o");
        ParsedBinary b = new ElfFixtureBuilder().object().section(".text", new byte[8])
                .defined("dup", ".text", 1, 0, 0x20, 8).buildParsed("b.o");
        ResolutionDecision decision = decide(Map.of("user", user, "a", a, "b", b),
                List.of("user", "a", "b"), "dup", "");
        assertEquals("AMBIGUOUS_DUPLICATE_STRONG", decision.status());
        assertEquals(2, decision.chosen().size());
    }

    @Test
    void truncatedElfHasBoundsErrorAndCorruptArchiveMemberIsIsolated() {
        assertThrows(ElfReadException.class, () -> parser.parse(new byte[]{0x7f, 'E', 'L', 'F', 2, 1}, "bad"));
        byte[] archive = new ArchiveFixtureBuilder()
                .member("bad.o", new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1})
                .member("good.o", new ElfFixtureBuilder().object().section(".text", new byte[4]).build())
                .build();
        ParsedBinary parsed = parser.parse(archive, "mixed.a");
        assertEquals(1, parsed.archiveMembers.size());
        assertEquals(1, parsed.memberErrors.size());
    }

    private ParsedBinary objectWithUndefined(String name, String symbol, int binding) {
        ElfFixtureBuilder builder = new ElfFixtureBuilder().object().section(".text", new byte[8])
                .undefined(symbol, binding);
        return builder.reloc(".text", symbol, 2, 0, 0).buildParsed(name);
    }

    private ParsedBinary comdatObject(String name, long value) {
        return new ElfFixtureBuilder().object()
                .section(".text", new byte[8])
                .defined("inline_fn", ".text", 1, 0, value, 8)
                .group("inline_group", true, List.of(".text"))
                .buildParsed(name);
    }

    private byte[] rebuild(ParsedBinary parsed) {
        return parsed.data;
    }

    private Scheme run(Map<String, ParsedBinary> binaries, List<String> order, PlatformPolicy policy) {
        return engine.resolve(new ResolutionInput("test", policy, order), binaries);
    }

    private ResolutionDecision decide(Map<String, ParsedBinary> binaries, List<String> order,
                                      String name, String version) {
        return decision(run(binaries, order, PlatformPolicy.GNU_LOAD_ORDER), name, version);
    }

    private ResolutionDecision decision(Scheme scheme, String name, String version) {
        return scheme.decisions.stream()
                .filter(decision -> decision.name().equals(name)
                        && ((version == null || version.isEmpty())
                        ? decision.requestedVersion().isEmpty()
                        : decision.requestedVersion().equals(version)))
                .findFirst().orElseThrow(() -> new AssertionError("缺少裁决: " + name + "@" + version));
    }
}
