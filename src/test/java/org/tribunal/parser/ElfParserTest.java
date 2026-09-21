package org.tribunal.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tribunal.model.ArchiveMember;
import org.tribunal.model.ElfFile;
import org.tribunal.model.VersionDefinition;
import org.tribunal.support.ElfFixtureBuilder;
import org.tribunal.support.ElfFixtureBuilder.Binding;
import org.tribunal.support.ElfFixtureBuilder.SymbolType;
import org.tribunal.support.ElfFixtureBuilder.Visibility;

class ElfParserTest {
    @Test
    void parsesSymbolsRelocationsVersionsGroupsAndDependencies() throws Exception {
        byte[] bytes = new ElfFixtureBuilder().name("lib.so").shared().needed("libc.so.6").soname("lib.so")
                .verdef("LIB_1").verdefAlias("LIB_1_ALIAS")
                .group("inline")
                .define("normal", ".text", Binding.GLOBAL, Visibility.PROTECTED, SymbolType.FUNC, 0x10, 8)
                .defineVersioned("versioned", ".text", Binding.GLOBAL, 0x20, 2)
                .reference("external", Binding.GLOBAL, 0)
                .build();
        ElfFile elf = new ElfParser("binary:lib.so", "lib.so").parse(bytes);
        assertThat(elf.definitions()).extracting(elfSymbol -> elfSymbol.name()).contains("normal", "versioned");
        assertThat(elf.versionDefinitions()).flatMap(VersionDefinition::names).contains("LIB_1", "LIB_1_ALIAS");
        assertThat(elf.relocations()).singleElement().satisfies(relocation -> {
            assertThat(relocation.symbol()).isEqualTo("external");
            assertThat(relocation.typeName()).isEqualTo("R_X86_64_PLT32");
        });
        assertThat(elf.comdatGroups()).containsExactly("inline");
        assertThat(elf.dependencies()).anySatisfy(edge -> assertThat(edge.toLibrary()).isEqualTo("libc.so.6"));
    }

    @Test
    void enforcesLengthAndIndexBounds() {
        byte[] truncated = new byte[]{0x7f,'E','L','F',2,1,1};
        assertThatThrownBy(() -> new ElfParser("bad", "bad").parse(truncated))
                .isInstanceOf(ElfFormatException.class).hasMessageContaining("smaller");
        byte[] bytes = new ElfFixtureBuilder().text().define("x", ".text", Binding.GLOBAL,
                Visibility.DEFAULT, SymbolType.FUNC, 0, 8).build();
        byte[] corrupted = java.util.Arrays.copyOf(bytes, 50);
        assertThatThrownBy(() -> new ElfParser("corrupt", "corrupt").parse(corrupted))
                .isInstanceOf(ElfFormatException.class).hasMessageContaining("bounds");
    }

    @Test
    void corruptArchiveMemberIsIsolatedWithoutStoppingOthers() throws Exception {
        byte[] good = new ElfFixtureBuilder().name("good.o").text()
                .define("good", ".text", Binding.GLOBAL, Visibility.DEFAULT, SymbolType.FUNC, 0, 8).build();
        byte[] bad = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] archive = ElfFixtureBuilder.archive(Map.entry("good.o", good), Map.entry("bad.o", bad));
        var members = new ArchiveParser().parse("binary:lib.a", "lib.a", archive);
        assertThat(members).hasSize(2);
        assertThat(members.get(0).parseOk()).isTrue();
        assertThat(members.get(1).parseOk()).isFalse();
        assertThat(members.get(1).parseError()).contains("Invalid ELF magic");
    }
}
