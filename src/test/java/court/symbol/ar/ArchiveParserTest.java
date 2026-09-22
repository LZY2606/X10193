package court.symbol.ar;

import court.symbol.fixture.ElfFixture;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveParserTest {

    @Test
    void parsesMembersLazily() {
        byte[] a = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("alpha", 1)).build();
        byte[] b = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("beta", 1)).build();
        byte[] ar = ElfFixture.archive(Map.of("alpha.o", a, "beta.o", b));

        Archive parsed = Archive.parse(ar);
        assertEquals(2, parsed.members().size());
        assertEquals("alpha.o", parsed.members().get(0).name());
        assertNotNull(parsed.members().get(1).parseElf());
    }

    @Test
    void rejectsBadMagicAndTruncatedMember() {
        assertThrows(ArchiveException.class,
                () -> Archive.parse(new byte[]{'n', 'o', 'p', 'e'}));
        byte[] good = ElfFixture.archive(Map.of("x.o",
                ElfFixture.builder().relocatable()
                        .addSymbol(ElfFixture.Sym.def("x", 1)).build()));
        byte[] cut = new byte[good.length - 4];
        System.arraycopy(good, 0, cut, 0, cut.length);
        assertThrows(ArchiveException.class, () -> Archive.parse(cut));
    }
}
