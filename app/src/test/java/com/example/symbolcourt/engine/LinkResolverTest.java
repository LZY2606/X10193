package com.example.symbolcourt.engine;

import com.example.symbolcourt.domain.Models.*;
import com.example.symbolcourt.elf.ArchiveParser;
import com.example.symbolcourt.elf.ElfParser;
import com.example.symbolcourt.fixture.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkResolverTest {
    private final ElfParser elfParser = new ElfParser();
    private final ArchiveParser archiveParser = new ArchiveParser(elfParser);
    private final LinkResolver resolver = new LinkResolver();

    @Test
    void strongDefinitionBeatsWeakDefinition() {
        byte[] referrer = new ElfFixtureBuilder()
                .symbol(SymbolSpec.undef("api", SymbolSpec.Binding.GLOBAL, null))
                .relocation(0, 1, ElfFixtureBuilder.R_X86_64_PC32, -4)
                .build();
        byte[] weak = new ElfFixtureBuilder().symbol(SymbolSpec.weakFunc("api")).build();
        byte[] strong = new ElfFixtureBuilder().symbol(SymbolSpec.globalFunc("api")).build();
        ResolutionResult result = resolve(List.of(referrer, weak, strong), List.of());
        ResolutionDecision decision = decision(result, "api");
        assertThat(decision.status()).isEqualTo("RESOLVED");
        assertThat(decision.chosenProviderIds()).hasSize(1);
        assertThat(decision.candidates()).hasSize(2);
        assertThat(decision.candidates().get(1).eliminationReason()).contains("排名");
    }

    @Test
    void restoresVersionNameAndSelectsNonDefaultVersion() {
        byte[] referrer = new ElfFixtureBuilder()
                .symbol(SymbolSpec.undef("versioned", SymbolSpec.Binding.GLOBAL, "LIBV2"))
                .build();
        byte[] library = new ElfFixtureBuilder().type(ElfFixtureBuilder.ET_DYN)
                .defineVersion("LIBV1", 2, true)
                .defineVersion("LIBV2", 3, false)
                .symbol(versioned("versioned", "LIBV1", 0x1000))
                .symbol(versioned("versioned", "LIBV2", 0x2000))
                .build();
        ResolutionResult result = resolve(List.of(referrer, library), List.of());
        ResolutionDecision decision = decision(result, "versioned");
        assertThat(decision.requestedVersion()).isEqualTo("LIBV2");
        assertThat(decision.status()).isEqualTo("RESOLVED");
        assertThat(decision.chosenProviderIds()).hasSize(1);
        Candidate winner = winner(decision);
        assertThat(winner.versionName()).isEqualTo("LIBV2");
        assertThat(winner.value()).isEqualTo(0x2000);
    }

    @Test
    void hiddenProviderIsEliminatedForAnotherObject() {
        byte[] referrer = new ElfFixtureBuilder()
                .symbol(SymbolSpec.undef("secret", SymbolSpec.Binding.GLOBAL, null))
                .build();
        byte[] hidden = new ElfFixtureBuilder()
                .symbol(new SymbolSpec("secret", null, SymbolSpec.Binding.GLOBAL, SymbolSpec.Type.FUNC,
                        SymbolSpec.Visibility.HIDDEN, 1, 0, 1, false, true, false))
                .build();
        ResolutionResult result = resolve(List.of(referrer, hidden), List.of());
        assertThat(decision(result, "secret").status()).isEqualTo("UNRESOLVED");
        assertThat(decision(result, "secret").candidates().get(0).eliminationReason()).contains("hidden");
    }

    @Test
    void commonSymbolIsCandidateButLosesToStrongDefinition() {
        byte[] referrer = new ElfFixtureBuilder()
                .symbol(SymbolSpec.undef("counter", SymbolSpec.Binding.GLOBAL, null))
                .build();
        byte[] common = new ElfFixtureBuilder().symbol(SymbolSpec.common("counter", 16)).build();
        byte[] strong = new ElfFixtureBuilder().symbol(new SymbolSpec("counter", null,
                SymbolSpec.Binding.GLOBAL, SymbolSpec.Type.OBJECT, SymbolSpec.Visibility.DEFAULT, 1,
                0x3000, 16, false, true, false)).build();
        ResolutionResult result = resolve(List.of(referrer, common, strong), List.of());
        ResolutionDecision decision = decision(result, "counter");
        assertThat(decision.status()).isEqualTo("RESOLVED");
        assertThat(winner(decision).value()).isEqualTo(0x3000);
        assertThat(decision.candidates()).anyMatch(candidate -> candidate.common());
    }

    @Test
    void archiveMembersAreLazilyExtractedForCircularDefinitions() {
        byte[] alpha = new ElfFixtureBuilder()
                .symbol(SymbolSpec.globalFunc("alpha"))
                .symbol(SymbolSpec.undef("beta", SymbolSpec.Binding.GLOBAL, null))
                .build();
        byte[] beta = new ElfFixtureBuilder()
                .symbol(SymbolSpec.globalFunc("beta"))
                .symbol(SymbolSpec.undef("alpha", SymbolSpec.Binding.GLOBAL, null))
                .build();
        byte[] archive = new ArchiveFixtureBuilder().member("alpha.o", alpha).member("beta.o", beta).build();
        ArchiveFile parsedArchive = archiveParser.parse("a", "libcycle.a", "libcycle.a", archive);
        ResolutionResult result = resolve(List.of(), List.of(parsedArchive));
        assertThat(result.extractedMembers()).hasSize(2);
        assertThat(result.events()).anyMatch(event -> event.type().equals("ARCHIVE_EXTRACT"));
    }

    @Test
    void unmetVersionRequirementStaysUnresolved() {
        byte[] referrer = new ElfFixtureBuilder()
                .symbol(SymbolSpec.undef("api", SymbolSpec.Binding.GLOBAL, "MISSING"))
                .requireVersion("MISSING")
                .build();
        byte[] library = new ElfFixtureBuilder().type(ElfFixtureBuilder.ET_DYN)
                .defineVersion("PRESENT", 2, true)
                .symbol(new SymbolSpec("api", "PRESENT", SymbolSpec.Binding.GLOBAL,
                        SymbolSpec.Type.FUNC, SymbolSpec.Visibility.DEFAULT, 1, 0x1000, 1,
                        false, true, true))
                .build();
        ResolutionResult result = resolve(List.of(referrer, library), List.of());
        assertThat(decision(result, "api").status()).isEqualTo("UNRESOLVED");
        assertThat(result.dependencyEdges()).anyMatch(edge -> !edge.satisfied());
    }

    private ResolutionDecision decision(ResolutionResult result, String name) {
        return result.decisions().stream()
                .filter(decision -> decision.symbolName().equals(name))
                .findFirst().orElseThrow();
    }

    private Candidate winner(ResolutionDecision decision) {
        return decision.candidates().stream()
                .filter(candidate -> decision.chosenProviderIds().contains(candidate.providerId()))
                .findFirst().orElseThrow();
    }

    private SymbolSpec versioned(String name, String version, long value) {
        return new SymbolSpec(name, version, SymbolSpec.Binding.GLOBAL, SymbolSpec.Type.FUNC,
                SymbolSpec.Visibility.DEFAULT, 1, value, 1, false, true, true);
    }

    private ResolutionResult resolve(List<byte[]> rawObjects, List<ArchiveFile> archives) {
        List<ParsedObject> objects = rawObjects.stream()
                .map(bytes -> elfParser.parse("o" + System.identityHashCode(bytes),
                        "object-" + System.identityHashCode(bytes), "memory", bytes))
                .toList();
        List<LoadOrderEntry> order = objects.stream()
                .map(object -> new LoadOrderEntry(object.id(), object.displayName(),
                        objects.indexOf(object), "ELF", null))
                .toList();
        return resolver.resolve(new LinkRequest("plan", objects, archives, order, LinkPolicy.GNU));
    }
}
