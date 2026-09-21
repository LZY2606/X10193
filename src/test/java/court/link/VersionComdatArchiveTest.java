package court.link;

import court.elf.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersionComdatArchiveTest {

    private final BinaryLoader loader = new BinaryLoader();
    private final LinkEngine engine = new LinkEngine(loader, new UnitExtractor());

    private LoadedFile f(int order, String name, byte[] d) { return loader.load(order, name, d); }
    private LinkReport run(LinkPolicy p, LoadedFile... fs) {
        return engine.resolve(new ArrayList<>(List.of(fs)), "t", p);
    }
    private Verdict v(LinkReport r, String n, String ver) {
        return r.verdicts.stream().filter(x -> x.name.equals(n)
                && (ver == null ? x.requestedVersion.isEmpty() : x.requestedVersion.equals(ver)))
                .findFirst().orElseThrow(() -> new AssertionError("missing " + n + "@" + ver));
    }

    @Test
    void versionAliasBindsOnlyMatchingVersion() {
        byte[] so = ElfBuilder.dyn().soname("libx.so")
                .dynDef("api", Const.STB_GLOBAL, Const.STT_FUNC, "V2", Const.STV_DEFAULT)
                .dynDef("api", Const.STB_GLOBAL, Const.STT_FUNC, "V1", Const.STV_DEFAULT)
                .verDef("V2", List.of()).verDef("V1", List.of())
                .build();
        byte[] wantV1 = ElfBuilder.dyn().needed("libx.so")
                .dynUndef("api", Const.STB_GLOBAL, "V1")
                .verNeed("libx.so", "V1", false).build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "c.so", wantV1), f(1, "libx.so", so));
        Verdict win = v(r, "api", "V1");
        assertEquals("RESOLVED", win.outcome);
        assertEquals("V1", win.winner.version);
        assertTrue(win.candidates.stream().anyMatch(c -> c.status.equals("ELIMINATED")
                && c.version.equals("V2") && c.reason.contains("版本不匹配")));
    }

    @Test
    void unversionedReferenceDoesNotBindNonDefaultVersion() {
        byte[] so = ElfBuilder.dyn()
                .dynDef("only_versioned", Const.STB_GLOBAL, Const.STT_FUNC, "V1", Const.STV_DEFAULT)
                .verDef("V1", List.of()).build();
        byte[] user = ElfBuilder.rel().undef("only_versioned", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "u.o", user), f(1, "lib.so", so));
        assertEquals("UNRESOLVED", v(r, "only_versioned", null).outcome);
    }

    @Test
    void unmetVersionIsDistinctOutcome() {
        byte[] so = ElfBuilder.dyn()
                .dynDef("api", Const.STB_GLOBAL, Const.STT_FUNC, "V9", Const.STV_DEFAULT)
                .verDef("V9", List.of()).build();
        byte[] client = ElfBuilder.dyn().needed("libapi.so")
                .dynUndef("api", Const.STB_GLOBAL, "V1")
                .verNeed("libapi.so", "V1", false).build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "client.so", client), f(1, "libapi.so", so));
        Verdict verdict = v(r, "api", "V1");
        assertEquals("UNMET_VERSION", verdict.outcome);
        assertTrue(verdict.candidates.stream().anyMatch(c -> c.reason != null
                && c.reason.contains("V9")));
    }

    @Test
    void comdatGroupsAreDeduplicatedNotOverridden() {
        byte[] a = ElfBuilder.rel().def("inline_fn", Const.STB_WEAK, Const.STT_FUNC, 1)
                .comdat("inline_fn").build();
        byte[] b = ElfBuilder.rel().def("inline_fn", Const.STB_WEAK, Const.STT_FUNC, 1)
                .comdat("inline_fn").build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "a.o", a), f(1, "b.o", b));
        long inlineProviders = r.units.stream().flatMap(u -> u.providers.stream())
                .filter(p -> p.name.equals("inline_fn")).count();
        assertEquals(1, inlineProviders, "one COMDAT copy must be folded");
        assertTrue(r.warnings.stream().anyMatch(w -> w.contains("COMDAT")));
        assertTrue(r.steps.stream().anyMatch(s -> s.phase.equals("COMDAT") && s.title.contains("折叠")));
    }

    @Test
    void archiveMembersAreExtractedLazilyAndRepeatResolvesCircular() {
        byte[] a = ElfBuilder.rel().def("ca", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .undef("cb", Const.STB_GLOBAL).build();
        byte[] b = ElfBuilder.rel().def("cb", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .undef("ca", Const.STB_GLOBAL).build();
        byte[] ar = new ArBuilder().add("a.o", a, "ca").add("b.o", b, "cb").build();
        byte[] main = ElfBuilder.rel().undef("ca", Const.STB_GLOBAL).build();

        LinkPolicy repeat = LinkPolicy.x86_64();
        LinkReport rep = run(repeat, f(0, "main.o", main), f(1, "libc.a", ar));
        assertEquals("RESOLVED", v(rep, "ca", null).outcome);
        assertEquals("RESOLVED", v(rep, "cb", null).outcome);
        long extracted = rep.units.stream().filter(u -> u.fromArchive && u.extracted).count();
        assertEquals(2, extracted);
        assertTrue(rep.steps.stream().anyMatch(s -> s.phase.equals("EXTRACT")));

        LinkPolicy single = LinkPolicy.x86_64();
        single.archivePolicy = "SINGLE_PASS";
        LinkReport sp = run(single, f(0, "main.o", main), f(1, "libc.a", ar));
        // circular back-reference cannot be satisfied in one pass
        assertEquals("UNRESOLVED", v(sp, "cb", null).outcome);
    }

    @Test
    void unusedArchiveMemberIsNotExtracted() {
        byte[] used = ElfBuilder.rel().def("used_fn", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] dead = ElfBuilder.rel().def("dead_fn", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] ar = new ArBuilder().add("used.o", used, "used_fn").add("dead.o", dead, "dead_fn").build();
        byte[] main = ElfBuilder.rel().undef("used_fn", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "main.o", main), f(1, "lib.a", ar));
        assertTrue(r.units.stream().noneMatch(u -> u.unitId.contains("dead.o")));
        assertTrue(r.units.stream().anyMatch(u -> u.unitId.contains("used.o")));
    }
}
