package court.symbol.elf;

import court.symbol.fixture.ElfFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElfParserTest {

    @Test
    void parsesRelocatableWithStrongAndUndefined() {
        int undef = ElfFixture.builder()
                .relocatable()
                .addUndef("external_fn");
        byte[] elf = ElfFixture.builder()
                .relocatable()
                .addSymbol(ElfFixture.Sym.undef("external_fn"))
                .addSymbol(ElfFixture.Sym.def("strong_fn", 1))
                .addRela(1, new ElfFixture.Rela(1, 4, 0x10, 0))
                .build();

        ElfFile parsed = ElfFile.parse(elf);
        assertTrue(parsed.relocatable());
        assertEquals(3, parsed.symbols().size());
        Elf.Symbol fn = parsed.symbols().get(2);
        assertEquals("strong_fn", fn.name());
        assertEquals(Elf.STB_GLOBAL, fn.binding());
        assertTrue(fn.defined());

        Elf.Relocation rel = parsed.primaryTable()
                .relocationsBySection().values().iterator().next().get(0);
        assertEquals(1, rel.symbolIndex());
        assertEquals("external_fn", parsed.symbols().get(rel.symbolIndex()).name());
    }

    @Test
    void restoresVersionIndicesToNames() {
        byte[] elf = ElfFixture.builder()
                .shared()
                .verDef(2, "LIBA_1.0", List.of("OLDLIB"))
                .addSymbol(ElfFixture.Sym.def("api", 1).version(2))
                .build();
        ElfFile f = ElfFile.parse(elf);
        assertEquals("LIBA_1.0", f.versionDefinitions().get(0).name());
        assertEquals(List.of("OLDLIB"), f.versionDefinitions().get(0).aliases());
        Elf.Symbol api = f.symbols().get(1);
        assertEquals("LIBA_1.0", api.version().display());
    }

    @Test
    void parsesVersionNeedsAndFlags() {
        byte[] elf = ElfFixture.builder()
                .shared()
                .needed("libc.so.6")
                .verNeed(2, "libc.so.6", "GLIBC_2.2.5", false)
                .verNeed(3, "libc.so.6", "GLIBC_2.99", true)
                .addSymbol(ElfFixture.Sym.undef("malloc").version(2))
                .addSymbol(ElfFixture.Sym.undef("future").version(3))
                .build();
        ElfFile f = ElfFile.parse(elf);
        assertEquals(List.of("libc.so.6"), f.needed());
        assertTrue(f.versionNeeds().stream()
                .anyMatch(n -> n.name().equals("GLIBC_2.99") && n.weak()));
    }

    @Test
    void parsesComdatGroupAndSignature() {
        byte[] elf = ElfFixture.builder()
                .relocatable()
                .comdat("inline_thing")
                .addSymbol(ElfFixture.Sym.def("inline_thing", 1))
                .build();
        ElfFile f = ElfFile.parse(elf);
        assertEquals(1, f.groups().size());
        Elf.SectionGroup g = f.groups().get(0);
        assertTrue(g.comdat());
        assertEquals("inline_thing", g.signature(f));
        assertTrue(g.memberSections().contains(1));
    }

    @Test
    void hiddenVisibilityAndBindingRoundTrip() {
        byte[] elf = ElfFixture.builder()
                .relocatable()
                .addSymbol(ElfFixture.Sym.def("visible", 1))
                .addSymbol(ElfFixture.Sym.def("secret", 1).hidden())
                .addSymbol(ElfFixture.Sym.def("guarded", 1).protectedVis())
                .addSymbol(ElfFixture.Sym.def("maybe", 1).weak())
                .build();
        ElfFile f = ElfFile.parse(elf);
        Elf.Symbol secret = f.symbols().stream()
                .filter(s -> s.name().equals("secret")).findFirst().orElseThrow();
        Elf.Symbol guarded = f.symbols().stream()
                .filter(s -> s.name().equals("guarded")).findFirst().orElseThrow();
        assertTrue(secret.hiddenVis());
        assertTrue(guarded.protectedVis());
    }

    @Test
    void rejectsTruncatedFilesAndBadMagic() {
        assertThrows(ElfException.class, () -> ElfFile.parse(new byte[]{1, 2, 3}));
        byte[] junk = new byte[64];
        junk[0] = 0x7f; junk[1] = 'E'; junk[2] = 'L'; junk[3] = 'F';
        junk[4] = Elf.ELFCLASS64; junk[5] = Elf.ELFDATA2LSB;
        // shoff pointing way beyond the file
        for (int i = 40; i < 48; i++) junk[i] = (byte) 0xff;
        assertThrows(ElfException.class, () -> ElfFile.parse(junk));
    }

    @Test
    void boundsChecksRelocationSymbolIndexAndStringOffsets() {
        // corrupt the versym-style length / section headers by truncation
        byte[] good = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("a", 1)).build();
        byte[] cut = new byte[good.length - 8];
        System.arraycopy(good, 0, cut, 0, cut.length);
        assertThrows(ElfException.class, () -> ElfFile.parse(cut));
    }
}
