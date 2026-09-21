package org.tribunal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tribunal.model.ArchiveMember;
import org.tribunal.model.BinaryKind;
import org.tribunal.model.ElfFile;
import org.tribunal.model.ParsedBinary;
import org.tribunal.model.PlatformPolicy;
import org.tribunal.model.Resolution;
import org.tribunal.model.ResolutionResult;
import org.tribunal.parser.ArchiveParser;
import org.tribunal.parser.ElfParser;
import org.tribunal.support.ElfFixtureBuilder;
import org.tribunal.support.ElfFixtureBuilder.Binding;
import org.tribunal.support.ElfFixtureBuilder.SymbolType;
import org.tribunal.support.ElfFixtureBuilder.Visibility;

class LinkResolverTest {
    private final LinkResolver resolver = new LinkResolver();

    @Test
    void strongDefinitionBeatsWeakAndDynamic() throws Exception {
        ElfFile weak = parse("weak.o", new ElfFixtureBuilder().text()
                .define("fn", ".text", Binding.WEAK, Visibility.DEFAULT, SymbolType.FUNC, 0x10, 8).build());
        ElfFile strong = parse("strong.o", new ElfFixtureBuilder().text()
                .define("fn", ".text", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.FUNC, 0x20, 8).build());
        ElfFile requester = parse("main.o", new ElfFixtureBuilder().text()
                .reference("fn", Binding.GLOBAL, 0).build());
        ElfFile shared = parse("libfn.so", new ElfFixtureBuilder().name("libfn.so").shared().soname("libfn.so")
                .define("fn", ".text", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.FUNC, 0x30, 8).build());
        ResolutionResult result = resolver.resolve(List.of(loaded(1, weak), loaded(2, strong), loaded(3, requester), loaded(4, shared)),
                PlatformPolicy.gnuLike());
        Resolution resolution = result.resolutions().get(0);
        assertThat(resolution.status()).isEqualTo("RESOLVED");
        assertThat(resolution.selectedProviderKeys()).singleElement().asString().contains("strong.o");
        assertThat(resolution.candidates()).filteredOn(candidate -> !candidate.selected())
                .anyMatch(candidate -> candidate.unitName().equals("weak.o") && candidate.rejectionReasons().stream().anyMatch(reason -> reason.contains("GLOBAL")));
    }

    @Test
    void versionedRequestRestoresVersionAndRejectsBaseOrNonDefault() throws Exception {
        ElfFile provider = parse("libver.so", new ElfFixtureBuilder().name("libver.so").shared()
                .verdef("LIBVER_1.0").defineVersioned("api", ".text", Binding.GLOBAL, 0x40, 2).build());
        ElfFile nonDefault = parse("libold.so", new ElfFixtureBuilder().name("libold.so").shared()
                .verdef("LIBVER_0.9").nonDefaultVersion().defineVersioned("api", ".text", Binding.GLOBAL, 0x50, 2).build());
        ElfFile requester = parse("vermain.o", new ElfFixtureBuilder().name("vermain.o").text()
                .verneed("libver.so", "LIBVER_1.0").reference("api", Binding.GLOBAL, 2).build());
        ResolutionResult result = resolver.resolve(List.of(loaded(1, provider), loaded(2, nonDefault), loaded(3, requester)),
                PlatformPolicy.gnuLike());
        Resolution resolution = result.resolutions().get(0);
        assertThat(resolution.requestedVersion()).isEqualTo("LIBVER_1.0");
        assertThat(resolution.status()).isEqualTo("RESOLVED");
        assertThat(resolution.selectedProviderKeys()).singleElement().asString().contains("libver.so");
        assertThat(resolution.candidates()).anySatisfy(candidate -> assertThat(candidate.rejectionReasons())
                .anyMatch(reason -> reason.contains("非默认版本")));
    }

    @Test
    void hiddenAndProtectedVisibilityAreExplained() throws Exception {
        ElfFile hidden = parse("hidden.o", new ElfFixtureBuilder().text()
                .define("secret", ".text", Binding.GLOBAL, Visibility.HIDDEN, SymbolType.FUNC, 0x60, 8).build());
        ElfFile protectedSymbol = parse("protected.so", new ElfFixtureBuilder().name("protected.so").shared()
                .define("secret", ".text", Binding.GLOBAL, Visibility.PROTECTED, SymbolType.FUNC, 0x70, 8).build());
        ElfFile requester = parse("need-secret.o", new ElfFixtureBuilder().text()
                .reference("secret", Binding.GLOBAL, 0).build());
        Resolution resolution = resolver.resolve(List.of(loaded(1, hidden), loaded(2, protectedSymbol), loaded(3, requester)),
                PlatformPolicy.gnuLike()).resolutions().get(0);
        assertThat(resolution.status()).isEqualTo("RESOLVED");
        assertThat(resolution.selectedProviderKeys()).singleElement().asString().contains("protected.so");
        assertThat(resolution.candidates()).anySatisfy(candidate -> assertThat(candidate.rejectionReasons())
                .anyMatch(reason -> reason.contains("HIDDEN")));
    }

    @Test
    void commonSymbolsMergeToLargestCommonAndRealDefinitionWins() throws Exception {
        ElfFile a = parse("a.o", new ElfFixtureBuilder().bss().common("state", 16, Binding.GLOBAL).build());
        ElfFile b = parse("b.o", new ElfFixtureBuilder().bss().common("state", 32, Binding.GLOBAL).build());
        ElfFile requester = parse("common-main.o", new ElfFixtureBuilder().text().reference("state", Binding.GLOBAL, 0).build());
        Resolution commonResult = resolver.resolve(List.of(loaded(1, a), loaded(2, b), loaded(3, requester)),
                PlatformPolicy.gnuLike()).resolutions().get(0);
        assertThat(commonResult.status()).isEqualTo("RESOLVED");
        assertThat(commonResult.selectedProviderKeys()).singleElement().asString().contains("b.o");

        ElfFile defined = parse("defined.o", new ElfFixtureBuilder().bss()
                .define("state", ".bss", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.OBJECT, 0x80, 8).build());
        Resolution definedResult = resolver.resolve(List.of(loaded(1, a), loaded(2, b), loaded(3, defined), loaded(4, requester)),
                PlatformPolicy.gnuLike()).resolutions().get(0);
        assertThat(definedResult.selectedProviderKeys()).singleElement().asString().contains("defined.o");
    }

    @Test
    void comdatSectionsWithSameSignatureAreDeduplicated() throws Exception {
        ElfFile a = parse("inline-a.o", new ElfFixtureBuilder().group("vfunc")
                .define("vfunc", ".text.vfunc", Binding.WEAK, Visibility.DEFAULT, SymbolType.FUNC, 0x90, 4).build());
        ElfFile b = parse("inline-b.o", new ElfFixtureBuilder().group("vfunc")
                .define("vfunc", ".text.vfunc", Binding.WEAK, Visibility.DEFAULT, SymbolType.FUNC, 0xa0, 4).build());
        ElfFile requester = parse("inline-main.o", new ElfFixtureBuilder().text().reference("vfunc", Binding.GLOBAL, 0).build());
        Resolution resolution = resolver.resolve(List.of(loaded(1, a), loaded(2, b), loaded(3, requester)),
                PlatformPolicy.gnuLike()).resolutions().get(0);
        assertThat(resolution.status()).isEqualTo("RESOLVED");
        assertThat(resolution.candidates()).hasSize(1);
        assertThat(resolution.candidates().get(0).rejectionReasons()).anyMatch(reason -> reason.contains("COMDAT"));
    }

    @Test
    void archiveIsLazilyExtractedAndCircularMembersSettleTogether() throws Exception {
        byte[] leftBytes = new ElfFixtureBuilder().name("left.o").text()
                .define("left", ".text", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.FUNC, 0xb0, 8)
                .reference("right", Binding.GLOBAL, 0).build();
        byte[] rightBytes = new ElfFixtureBuilder().name("right.o").text()
                .define("right", ".text", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.FUNC, 0xc0, 8)
                .reference("left", Binding.GLOBAL, 0).build();
        byte[] archiveBytes = ElfFixtureBuilder.archive(Map.entry("left.o", leftBytes), Map.entry("right.o", rightBytes));
        List<ArchiveMember> members = new ArchiveParser().parse("binary:libcycle.a", "libcycle.a", archiveBytes);
        ElfFile main = parse("cycle-main.o", new ElfFixtureBuilder().text().reference("left", Binding.GLOBAL, 0).build());
        ParsedBinary archive = new ParsedBinary(9, "libcycle.a", "ARCHIVE", "", "", archiveBytes.length, true, null, null, members);
        ResolutionResult result = resolver.resolve(List.of(
                new LinkResolver.LoadedBinary(archive, null, members, 0), loaded(10, main)),
                PlatformPolicy.gnuLike());
        assertThat(result.orderedUnitIds()).contains("binary:libcycle.a#0", "binary:libcycle.a#1", "binary:cycle-main.o");
        assertThat(result.steps()).filteredOn(step -> step.phase().equals("archive-extract")).hasSize(2);
        assertThat(result.resolutions()).allSatisfy(resolution -> assertThat(resolution.status()).isEqualTo("RESOLVED"));
    }

    @Test
    void duplicateDynamicCandidatesRemainAmbiguous() throws Exception {
        ElfFile first = parse("one.so", new ElfFixtureBuilder().name("one.so").shared()
                .define("dup", ".text", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.FUNC, 0xd0, 8).build());
        ElfFile second = parse("two.so", new ElfFixtureBuilder().name("two.so").shared()
                .define("dup", ".text", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.FUNC, 0xe0, 8).build());
        ElfFile requester = parse("dup-main.o", new ElfFixtureBuilder().text().reference("dup", Binding.GLOBAL, 0).build());
        Resolution resolution = resolver.resolve(List.of(loaded(1, first), loaded(2, second), loaded(3, requester)),
                PlatformPolicy.strict()).resolutions().get(0);
        assertThat(resolution.status()).isEqualTo("AMBIGUOUS");
        assertThat(resolution.selectedProviderKeys()).hasSize(2);
    }

    @Test
    void unsatisfiedVersionAndCopyRelocationAreReported() throws Exception {
        ElfFile requester = parse("need-missing.o", new ElfFixtureBuilder().text()
                .verneed("missing.so", "MISSING_9").reference("api", Binding.GLOBAL, 2).build());
        Resolution missing = resolver.resolve(List.of(loaded(1, requester)), PlatformPolicy.gnuLike())
                .resolutions().get(0);
        assertThat(missing.status()).isEqualTo("UNRESOLVED");
        assertThat(missing.reason()).contains("版本");

        ElfFile shared = parse("data.so", new ElfFixtureBuilder().name("data.so").shared()
                .define("data", ".text", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.OBJECT, 0xf0, 8).build());
        ElfFile executable = parse("app", new ElfFixtureBuilder().name("app").executable()
                .copyReference("data").build());
        Resolution copy = resolver.resolve(List.of(loaded(2, shared), loaded(3, executable)),
                PlatformPolicy.gnuLike()).resolutions().get(0);
        assertThat(copy.copyRelocation()).isTrue();
        assertThat(copy.affectedRelocations()).singleElement().satisfies(relocation -> assertThat(relocation.typeName()).isEqualTo("R_COPY"));
    }

    private ElfFile parse(String name, byte[] bytes) throws Exception {
        return new ElfParser("binary:" + name, name).parse(bytes);
    }

    private LinkResolver.LoadedBinary loaded(long id, ElfFile elf) {
        ParsedBinary binary = new ParsedBinary(id, elf.unitName(), elf.kind().name(), "", "", 0, true, null, elf, List.of());
        return new LinkResolver.LoadedBinary(binary, elf, List.of(), (int) id);
    }
}
