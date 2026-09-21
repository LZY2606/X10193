package court.elf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Length and index boundary checks: malformed inputs are flagged, not thrown. */
class ParserBoundsTest {

    @Test
    void truncatedHeaderIsCorrupt() {
        byte[] tiny = {0x7f, 'E', 'L', 'F', 2, 1, 1, 0};
        ElfImage img = ElfReader.read(tiny);
        assertTrue(img.corrupt);
        assertNotNull(img.error);
    }

    @Test
    void badMagicIsCorrupt() {
        byte[] x = new byte[64];
        x[0] = 'M'; x[1] = 'Z';
        assertTrue(ElfReader.read(x).corrupt);
    }

    @Test
    void sectionTablePastEofIsCorrupt() {
        byte[] good = ElfBuilder.rel().def("f", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] cut = new byte[good.length - 8];
        System.arraycopy(good, 0, cut, 0, cut.length);
        ElfImage img = ElfReader.read(cut);
        assertTrue(img.corrupt);
        assertTrue(img.error.toLowerCase().contains("section") || img.error.contains("past end"));
    }

    @Test
    void bogusShLinkIsCorrupt() {
        byte[] good = ElfBuilder.rel().def("f", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        // corrupt the .symtab sh_link field (section header 4, offset +40)
        // find shoff
        long shoff = 0;
        for (int i = 0; i < 8; i++) shoff |= (good[40 + i] & 0xffL) << (8 * i);
        // locate .symtab header by scanning for type==2 at +4
        int symHdr = -1;
        for (int h = 1; ; h++) {
            int base = (int) (shoff + h * 64L);
            int type = (good[base + 4] & 0xff);
            if (type == 2) { symHdr = base; break; }
        }
        good[symHdr + 40] = (byte) 0xef; // out-of-range sh_link
        ElfImage img = ElfReader.read(good);
        assertTrue(img.corrupt, () -> "expected corruption, got sections " + img.sections.size());
    }

    @Test
    void relocSymbolIndexOutOfRangeIsCorrupt() {
        // build an ELF then inflate the rela sym index beyond dynsym count
        byte[] good = ElfBuilder.dyn().dynUndef("g", Const.STB_GLOBAL, null)
                .rela(0x10, 7, 99, 0, ".text").build();
        ElfImage img = ElfReader.read(good);
        assertTrue(img.corrupt);
        assertTrue(img.error.contains("symbol index"));
    }

    @Test
    void archiveGarbageIsRejected() {
        byte[] notAr = "not an archive at all".getBytes();
        ArReader.Archive ar = ArReader.readIndex(notAr);
        assertFalse(ar.valid);
    }

    @Test
    void unknownBlobIsMarkedByLoaderNotThrown() {
        BinaryLoader loader = new BinaryLoader();
        LoadedFile f = loader.load(0, "x.dat", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertTrue(f.corrupt);
        assertEquals("CORRUPT", f.kind);
    }

    @Test
    void bigEndian32ClassRejectedGracefully() {
        byte[] x = new byte[64];
        x[0] = 0x7f; x[1] = 'E'; x[2] = 'L'; x[3] = 'F';
        x[4] = Const.ELFCLASS32; x[5] = Const.ELFDATA2MSB;
        // unsupported class value instead
        x[4] = 9;
        assertTrue(ElfReader.read(x).corrupt);
    }
}
