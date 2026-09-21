package com.example.tribunal;

import com.example.tribunal.core.elf.ArchiveParser;
import com.example.tribunal.core.elf.ElfModels.*;
import com.example.tribunal.core.elf.ElfParser;
import com.example.tribunal.core.fixture.ArchiveFixtureBuilder;
import com.example.tribunal.core.fixture.ElfFixtureBuilder;
import com.example.tribunal.core.model.TribunalModels.*;
import com.example.tribunal.core.resolution.BinaryLoader;
import com.example.tribunal.core.resolution.ResolutionEngine;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResolutionEngineTest {
    private Scheme resolve(String id, BinaryLoader.LoadedBinary... binaries) {
        return new ResolutionEngine(List.of(binaries), PlatformPolicy.gnu(), id)
                .resolve(id, "now", List.of());
    }

    private BinaryLoader.LoadedBinary load(String id, String fileName, byte[] bytes, int order) {
        return new BinaryLoader().load(id, fileName, bytes, order);
    }

    private SymbolResolution resolution(Scheme scheme, String name) {
        return scheme.resolutions().stream().filter(r -> r.key().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void strongDefeatsWeakAndCommon() {
        ElfFixtureBuilder main = ElfFixtureBuilder.relocatable("main.o");
        int strong = main.undefined("fn", ElfFixtureBuilder.STB_GLOBAL);
        int weak = main.undefined("weakly", ElfFixtureBuilder.STB_WEAK);
        int commonRef = main.undefined("counter", ElfFixtureBuilder.STB_GLOBAL);
        main.relocation(".text", strong, ElfFixtureBuilder.R_X86_64_PC32, 0, -4);
        main.relocation(".text", weak, ElfFixtureBuilder.R_X86_64_PC32, 4, -4);
        main.relocation(".text", commonRef, ElfFixtureBuilder.R_X86_64_PC32, 8, -4);

        ElfFixtureBuilder provider = ElfFixtureBuilder.relocatable("provider.o");
        provider.defined("fn", ElfFixtureBuilder.STB_WEAK, ElfFixtureBuilder.STT_FUNC, ElfFixtureBuilder.STV_DEFAULT, 1, 0x10, 4);
        provider.defined("weakly", ElfFixtureBuilder.STB_WEAK, ElfFixtureBuilder.STT_OBJECT, ElfFixtureBuilder.STV_DEFAULT, 1, 0x20, 4);
        provider.common("counter", 16, ElfFixtureBuilder.STB_GLOBAL);

        ElfFixtureBuilder strongProvider = ElfFixtureBuilder.relocatable("strong.o");
        strongProvider.defined("fn", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_FUNC, ElfFixtureBuilder.STV_DEFAULT, 1, 0x30, 8);

        Scheme scheme = resolve("s1", load("in-1", "main.o", main.build(), 1),
                load("in-2", "provider.o", provider.build(), 2),
                load("in-3", "strong.o", strongProvider.build(), 3));
        assertEquals(Outcome.RESOLVED, resolution(scheme, "fn").outcome());
        assertEquals("in-3", resolution(scheme, "fn").selected().module().inputId());
        assertEquals(Outcome.RESOLVED, resolution(scheme, "weakly").outcome());
        assertEquals(Outcome.RESOLVED, resolution(scheme, "counter").outcome());
    }

    @Test
    void hiddenSymbolIsNotCrossObjectCandidate() {
        ElfFixtureBuilder main = ElfFixtureBuilder.relocatable("main.o");
        int ref = main.undefined("secret", ElfFixtureBuilder.STB_GLOBAL);
        main.relocation(".text", ref, ElfFixtureBuilder.R_X86_64_PC32, 0, -4);
        ElfFixtureBuilder hidden = ElfFixtureBuilder.relocatable("hidden.o");
        hidden.defined("secret", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_OBJECT, ElfFixtureBuilder.STV_HIDDEN, 1, 8, 4);
        Scheme scheme = resolve("hidden", load("in-1", "main.o", main.build(), 1), load("in-2", "hidden.o", hidden.build(), 2));
        SymbolResolution result = resolution(scheme, "secret");
        assertEquals(Outcome.UNRESOLVED, result.outcome());
        assertTrue(result.candidates().stream().anyMatch(c -> c.eliminationReason() == EliminationReason.HIDDEN_NOT_EXPORTED));
    }

    @Test
    void lazyArchiveExtractionHandlesCircularMembers() {
        ElfFixtureBuilder root = ElfFixtureBuilder.relocatable("root.o");
        int needsA = root.undefined("a", ElfFixtureBuilder.STB_GLOBAL);
        root.relocation(".text", needsA, ElfFixtureBuilder.R_X86_64_PC32, 0, -4);

        ElfFixtureBuilder a = ElfFixtureBuilder.relocatable("a.o");
        int needsB = a.undefined("b", ElfFixtureBuilder.STB_GLOBAL);
        a.defined("a", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_FUNC, ElfFixtureBuilder.STV_DEFAULT, 1, 0, 1);
        a.relocation(".text", needsB, ElfFixtureBuilder.R_X86_64_PC32, 1, -4);

        ElfFixtureBuilder b = ElfFixtureBuilder.relocatable("b.o");
        b.defined("b", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_FUNC, ElfFixtureBuilder.STV_DEFAULT, 1, 0, 1);

        byte[] archive = new ArchiveFixtureBuilder().add("a.o", a.build()).add("b.o", b.build()).build();
        Scheme scheme = resolve("archive-cycle", load("in-1", "root.o", root.build(), 1),
                load("in-2", "libcycle.a", archive, 2));
        assertTrue(scheme.modules().stream().filter(m -> m.kind() == ModuleKind.ARCHIVE_MEMBER).allMatch(m -> m.archiveExtracted()));
        assertEquals(Outcome.RESOLVED, resolution(scheme, "a").outcome());
        assertEquals(Outcome.RESOLVED, resolution(scheme, "b").outcome());
    }

    @Test
    void duplicateEqualStaticCandidatesRemainAmbiguous() {
        ElfFixtureBuilder main = ElfFixtureBuilder.relocatable("main.o");
        int ref = main.undefined("dup", ElfFixtureBuilder.STB_GLOBAL);
        main.relocation(".text", ref, ElfFixtureBuilder.R_X86_64_PC32, 0, -4);
        ElfFixtureBuilder first = ElfFixtureBuilder.relocatable("first.o");
        first.defined("dup", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_OBJECT, ElfFixtureBuilder.STV_DEFAULT, 1, 0x10, 4);
        ElfFixtureBuilder second = ElfFixtureBuilder.relocatable("second.o");
        second.defined("dup", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_OBJECT, ElfFixtureBuilder.STV_DEFAULT, 1, 0x20, 4);
        Scheme scheme = resolve("dup", load("in-1", "main.o", main.build(), 1),
                load("in-2", "first.o", first.build(), 2), load("in-3", "second.o", second.build(), 3));
        assertEquals(Outcome.AMBIGUOUS, resolution(scheme, "dup").outcome());
        assertNull(resolution(scheme, "dup").selected());
    }

    @Test
    void parserRejectsOutOfBoundsRelocationSymbolAndTruncatedObject() {
        byte[] truncated = new byte[]{0x7f,'E','L','F',2,1,1,0};
        BinaryLoader.LoadedBinary corrupt = load("in-1", "bad.o", truncated, 1);
        assertEquals(InputKind.CORRUPT, corrupt.input().kind());

        ElfFixtureBuilder object = ElfFixtureBuilder.relocatable("bounds.o");
        object.relocation(".text", 99, ElfFixtureBuilder.R_X86_64_PC32, 0, -4);
        assertThrows(Exception.class, () -> ElfParser.parse("bounds.o", object.build()));
    }

    @Test
    void archiveCorruptMemberIsIsolated() {
        ElfFixtureBuilder good = ElfFixtureBuilder.relocatable("good.o");
        good.defined("good_symbol", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_FUNC,
                ElfFixtureBuilder.STV_DEFAULT, 1, 0, 1);
        byte[] archive = new ArchiveFixtureBuilder().add("good.o", good.build()).add("bad.o", new byte[10]).build();
        BinaryLoader.LoadedBinary loaded = load("in-1", "libmixed.a", archive, 1);
        assertEquals(InputKind.ARCHIVE, loaded.input().kind());
        assertEquals(0, loaded.objects().size());
        assertEquals(1, loaded.archiveMembers().size());
        assertTrue(ArchiveParser.isArchive(archive));
    }

    @Test
    void comdatDuplicateGroupDeduplicatesBeforeSymbolChoice() {
        ElfFixtureBuilder main = ElfFixtureBuilder.relocatable("main.o");
        int ref = main.undefined("inline_fn", ElfFixtureBuilder.STB_GLOBAL);
        main.relocation(".text", ref, ElfFixtureBuilder.R_X86_64_PC32, 0, -4);

        ElfFixtureBuilder first = ElfFixtureBuilder.relocatable("first_inline.o");
        first.section(".text.inline", new byte[8], ElfFixtureBuilder.SHT_PROGBITS, 6L);
        int signature = first.defined("inline_fn", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_FUNC,
                ElfFixtureBuilder.STV_DEFAULT, 2, 0, 8);
        first.group(signature, List.of(2), true);

        ElfFixtureBuilder second = ElfFixtureBuilder.relocatable("second_inline.o");
        second.section(".text.inline", new byte[8], ElfFixtureBuilder.SHT_PROGBITS, 6L);
        int other = second.defined("inline_fn", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_FUNC,
                ElfFixtureBuilder.STV_DEFAULT, 2, 0x40, 8);
        second.group(other, List.of(2), true);

        Scheme scheme = resolve("comdat", load("in-1", "main.o", main.build(), 1),
                load("in-2", "first_inline.o", first.build(), 2),
                load("in-3", "second_inline.o", second.build(), 3));
        SymbolResolution result = resolution(scheme, "inline_fn");
        assertEquals(Outcome.RESOLVED, result.outcome());
        assertTrue(result.candidates().stream().anyMatch(c -> c.eliminationReason() == EliminationReason.DISCARDED_COMDAT
                || c.module().display().contains("second")));
    }

    @Test
    void versionAliasResolvesAndMissingVersionStaysUnresolved() {
        ElfFixtureBuilder consumer = ElfFixtureBuilder.executable("consumer");
        int oldRef = consumer.versionedUndefined("versioned_fn", "libx.so", "LIBX_1.0");
        consumer.relocation(".data", oldRef, ElfFixtureBuilder.R_X86_64_RELATIVE, 0, 0);

        ElfFixtureBuilder library = ElfFixtureBuilder.shared("libx.so").soname("libx.so");
        library.defineVersion("LIBX_2.0", List.of("LIBX_2.0", "LIBX_1.0"), 2, false);
        library.versionedDefined("versioned_fn", "LIBX_2.0", ElfFixtureBuilder.STT_FUNC,
                ElfFixtureBuilder.STV_DEFAULT, 0x20, 8);

        Scheme scheme = resolve("version-alias", load("in-1", "consumer", consumer.build(), 1),
                load("in-2", "libx.so", library.build(), 2));
        SymbolResolution resolved = resolution(scheme, "versioned_fn");
        assertEquals(Outcome.RESOLVED, resolved.outcome());
        assertEquals("LIBX_1.0", resolved.key().version());
        assertTrue(resolved.aliasTried().contains("LIBX_2.0"));

        ElfFixtureBuilder wrongConsumer = ElfFixtureBuilder.executable("wrong-consumer");
        int missingRef = wrongConsumer.versionedUndefined("versioned_fn", "libx.so", "LIBX_9.9");
        wrongConsumer.relocation(".data", missingRef, ElfFixtureBuilder.R_X86_64_RELATIVE, 0, 0);
        Scheme missing = resolve("missing-version", load("in-1", "wrong-consumer", wrongConsumer.build(), 1),
                load("in-2", "libx.so", library.build(), 2));
        assertEquals(Outcome.UNRESOLVED, resolution(missing, "versioned_fn").outcome());
    }

    @Test
    void copyRelocationRequiresSharedDataObject() {
        ElfFixtureBuilder executableObject = ElfFixtureBuilder.executable("app");
        int ref = executableObject.undefined("shared_state", ElfFixtureBuilder.STB_GLOBAL);
        executableObject.relocation(".data", ref, ElfFixtureBuilder.R_X86_64_COPY, 0, 0);

        ElfFixtureBuilder library = ElfFixtureBuilder.shared("libstate.so").soname("libstate.so");
        library.versionedDefined("shared_state", "", ElfFixtureBuilder.STT_OBJECT,
                ElfFixtureBuilder.STV_DEFAULT, 0x100, 8);

        Scheme scheme = resolve("copy", load("in-1", "app", executableObject.build(), 1),
                load("in-2", "libstate.so", library.build(), 2));
        SymbolResolution result = resolution(scheme, "shared_state");
        assertEquals(Outcome.RESOLVED, result.outcome());
        assertTrue(result.selected().module().shared());
        assertTrue(result.relocations().stream().anyMatch(RelocationRef::copy));
    }
}
