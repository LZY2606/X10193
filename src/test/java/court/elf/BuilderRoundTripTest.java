package court.elf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuilderRoundTripTest {

    @Test
    void parsesStrongWeakRel() {
        byte[] a = ElfBuilder.rel()
                .def("strong_fn", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .def("weak_fn", Const.STB_WEAK, Const.STT_FUNC, 1)
                .common("buf", 64)
                .undef("extern_fn", Const.STB_GLOBAL)
                .build();
        ElfImage img = ElfReader.read(a);
        assertFalse(img.corrupt, () -> "should parse: " + img.error);
        assertEquals(4 + 1, img.symbols.size());
        assertEquals("REL", img.typeName());
        assertTrue(img.symbols.stream().anyMatch(s -> s.name.equals("buf") && s.isCommon()));
    }

    @Test
    void parsesVersionedShared() {
        byte[] so = ElfBuilder.dyn()
                .soname("libx.so")
                .dynDef("api", Const.STB_GLOBAL, Const.STT_FUNC, "VERS_1.1", Const.STV_DEFAULT)
                .dynDef("oldapi", Const.STB_GLOBAL, Const.STT_FUNC, "VERS_1.0", Const.STV_DEFAULT)
                .dynUndef("callee", Const.STB_GLOBAL, "VERS_2.0")
                .verDef("VERS_1.1", List.of())
                .verDef("VERS_1.0", List.of())
                .verNeed("liby.so", "VERS_2.0", false)
                .needed("liby.so")
                .build();
        ElfImage img = ElfReader.read(so);
        assertFalse(img.corrupt, () -> "should parse: " + img.error);
        assertEquals(2, img.versionDefs.size());
        assertEquals(1, img.versionNeeds.size());
        assertEquals("liby.so", img.versionNeeds.get(0).file);
        assertEquals("VERS_2.0", img.versionNeeds.get(0).entries.get(0).name);
        // version index restored
        int apiIdx = img.dynSymbols.get(1).versionIndex;
        assertEquals("VERS_1.1", img.versionDefs.stream()
                .filter(d -> d.index == apiIdx).findFirst().orElseThrow().name);
    }

    @Test
    void parsesArchive() {
        byte[] m1 = ElfBuilder.rel().def("a_fn", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] m2 = ElfBuilder.rel().def("b_fn", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] ar = new ArBuilder().add("a.o", m1, "a_fn").add("b.o", m2, "b_fn").build();
        ArReader.Archive archive = ArReader.readIndex(ar);
        assertTrue(archive.valid, () -> archive.error);
        assertEquals(2, archive.members.size());
        assertEquals(0, archive.symbolToMember.get("a_fn").get(0));
        assertEquals(1, archive.symbolToMember.get("b_fn").get(0));
    }
}
