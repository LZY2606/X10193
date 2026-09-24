package com.symcourt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.symcourt.elf.Archive;
import com.symcourt.elf.ElfFile;
import com.symcourt.elf.ElfParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElfParserTest {

    @Test
    void parsesSymtabGroupAndRelocs() {
        byte[] bytes = ElfBuilder.create(ElfBuilder.ET_REL)
                .def("alpha").group()
                .undef("beta")
                .reloc(0x10, "beta", 2, -4)
                .build();
        ElfFile elf = ElfFile.parse(bytes);
        assertEquals(ElfBuilder.ET_REL, elf.elfType());
        assertEquals(3, elf.symtab().size());
        assertEquals("alpha", elf.symtab().get(1).name);
        assertTrue(elf.symtab().get(2).isUndefined());
        assertEquals(1, elf.groups().size());
        assertEquals("alpha", elf.groups().get(0).signature);
        assertTrue(elf.groups().get(0).comdat);
        assertEquals(List.of(1), elf.groups().get(0).sectionIndices);
        assertEquals(1, elf.relocs().size());
        assertEquals(2, elf.relocs().get(0).symIndex);
        assertEquals(-4, elf.relocs().get(0).addend);
    }

    @Test
    void restoresVersionNamesFromIndices() {
        byte[] lib = ElfBuilder.create(ElfBuilder.ET_DYN).dynsym()
                .def("foo").version(2, false)
                .def("bar").version(3, true)
                .verdef(2, "LIB_1").verdef(3, "LIB_2")
                .build();
        ElfFile elf = ElfFile.parse(lib);
        ElfFile.VersionRef v1 = elf.dynsymVersion(1);
        assertEquals("LIB_1", v1.name);
        assertTrue(!v1.hidden);
        ElfFile.VersionRef v2 = elf.dynsymVersion(2);
        assertEquals("LIB_2", v2.name);
        assertTrue(v2.hidden);

        byte[] main = ElfBuilder.create(ElfBuilder.ET_EXEC).dynsym()
                .undef("foo").version(2, false)
                .verneed("libx.so", 2, "LIB_1")
                .build();
        ElfFile m = ElfFile.parse(main);
        assertEquals("LIB_1", m.dynsymVersion(1).name);
        assertEquals("libx.so", m.versionNeeds().get(0).file);
    }

    @Test
    void rejectsTruncatedAndOutOfBounds() {
        assertThrows(ElfParseException.class, () -> ElfFile.parse(new byte[4]));
        assertThrows(ElfParseException.class, () -> ElfFile.parse("not an elf".getBytes()));
        byte[] good = ElfBuilder.create(ElfBuilder.ET_REL).def("x").build();
        byte[] bad = good.clone();
        bad[40] = 0x7f; // shoff near end of file -> section table out of bounds
        assertThrows(ElfParseException.class, () -> ElfFile.parse(bad));
        byte[] trunc = new byte[good.length / 2];
        System.arraycopy(good, 0, trunc, 0, trunc.length);
        assertThrows(ElfParseException.class, () -> ElfFile.parse(trunc));
    }

    @Test
    void parsesArchives() {
        byte[] m1 = ElfBuilder.create(ElfBuilder.ET_REL).def("one").build();
        byte[] m2 = ElfBuilder.create(ElfBuilder.ET_REL).def("two").build();
        byte[] ar = new ArBuilder().member("m1.o", m1).member("m2.o", m2).build();
        assertTrue(Archive.isArchive(ar));
        List<Archive.Member> members = Archive.parse(ar);
        assertEquals(2, members.size());
        assertEquals("m1.o", members.get(0).name);
        assertEquals("one", ElfFile.parse(members.get(0).data).symtab().get(1).name);
        assertThrows(ElfParseException.class, () -> Archive.parse(m1));
    }
}
