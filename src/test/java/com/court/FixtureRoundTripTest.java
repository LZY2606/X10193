package com.court;

import com.court.elf.ArchiveParser;
import com.court.elf.ElfParseException;
import com.court.elf.ElfParser;
import com.court.fixtures.Ar;
import com.court.fixtures.Elf;
import com.court.model.ElfFile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FixtureRoundTripTest {

    @Test
    void parsesVersionedSharedObject() throws Exception {
        Elf soBuilder = Elf.shared()
                .soname("libfoo.so.1")
                .need("libc.so.6")
                .verDef(new Elf.VerDef("LIBFOO_1.0", 2))
                .verDef(new Elf.VerDef("LIBFOO_2.0", 3).alias())
                .verNeed(new Elf.VerNeed("libc.so.6", "GLIBC_2.2.5", 4));
        soBuilder.dyn("foo").object().defined(1, 0x100).version(2);
        soBuilder.dyn("foo_new").func().defined(1, 0x120).version(3);
        soBuilder.dyn("malloc").defined(0, 0).version(4);
        byte[] so = soBuilder.build();
        ElfFile f = ElfParser.parse(so, "libfoo.so.1", null);
        assertTrue(f.isShared());
        assertEquals("libfoo.so.1", f.getSoname());
        assertTrue(f.getNeeded().contains("libc.so.6"));
        assertTrue(f.getVersionDefs().stream().anyMatch(d -> d.name().equals("LIBFOO_2.0")));
        assertTrue(f.getVersionDefs().stream().anyMatch(d -> d.name().equals("LIBFOO_2.0_OLD") && d.nonDefault()));
        assertTrue(f.getVersionReqs().stream().anyMatch(r -> r.name().equals("GLIBC_2.2.5")));
        assertEquals(4, f.getDynVersym().size());
    }

    @Test
    void parsesArchiveWithSymbolTable() throws Exception {
        byte[] a = buildRel("helper");
        byte[] b = buildRel("other");
        byte[] ar = new Ar()
                .member("helper.o", a).exports("helper.o", "helper")
                .member("this_is_a_very_long_member_name_for_testing.o", b)
                .exports("this_is_a_very_long_member_name_for_testing.o", "other")
                .build();
        assertTrue(ArchiveParser.isArchive(ar));
        Map<String, ArchiveParser.ArchiveMember> members = ArchiveParser.parse(ar);
        assertEquals(2, members.size());
        assertTrue(members.containsKey("this_is_a_very_long_member_name_for_testing.o"));
        Map<String, Integer> table = ArchiveParser.symbolTable(ar);
        assertEquals(0, table.get("helper"));
        assertEquals(1, table.get("other"));
        // each archive member must itself parse as ELF
        for (ArchiveParser.ArchiveMember m : members.values()) {
            assertNotNull(ElfParser.parse(m.getData(), "libx.a", m.getName()));
        }
    }

    @Test
    void parsesComdatGroup() throws Exception {
        Elf objBuilder = Elf.reloc().sectionName(3, ".text.dup");
        objBuilder.sym("dup_func").defined(3, 0x0);
        objBuilder.comdat("dup_func").member(3);
        byte[] obj = objBuilder.build();
        ElfFile f = ElfParser.parse(obj, "a.o", null);
        assertEquals(1, f.getGroups().size());
        assertEquals("dup_func", f.getGroups().get(0).signature());
        assertTrue(f.getGroups().get(0).comdat());
    }

    private static byte[] buildRel(String sym) {
        Elf e = Elf.reloc();
        e.sym(sym).defined(1, 0x10);
        return e.build();
    }

    @Test
    void quarantinesTruncatedObject() {
        byte[] junk = new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1, 0};
        assertThrows(ElfParseException.class, () -> ElfParser.parse(junk, "bad.o", null));
    }
}
