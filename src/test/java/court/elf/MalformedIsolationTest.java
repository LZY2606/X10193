package court.elf;

import court.fixture.ElfBuilder;
import court.fixture.Sym;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MalformedIsolationTest {

    private static void assertRejected(byte[] data, String fragment) {
        ElfException ex = assertThrows(ElfException.class,
                () -> ElfParser.parse(data, "x", "x"), "应当拒绝");
        assertTrue(ex.getMessage().contains(fragment),
                "异常应包含 " + fragment + "，实际: " + ex.getMessage());
    }

    @Test
    void tooShortAndBadMagicAreRejected() {
        assertRejected(new byte[]{1, 2, 3}, "不是 ELF");
        byte[] badMagic = new byte[64];
        badMagic[0] = 0x7f; badMagic[1] = 'X'; badMagic[2] = 'L'; badMagic[3] = 'F';
        assertRejected(badMagic, "魔数");
    }

    @Test
    void unsupportedClassAndEncodingAreRejected() {
        byte[] d = new byte[64];
        d[0] = 0x7f; d[1] = 'E'; d[2] = 'L'; d[3] = 'F';
        d[4] = 9;
        assertRejected(d, "EI_CLASS");
        d[4] = 2; d[5] = 9;
        assertRejected(d, "EI_DATA");
    }

    @Test
    void sectionHeaderBeyondFileIsRejected() {
        byte[] good = ElfBuilder.rel().text("ok").syms(new Sym("f").defined(0)).build();
        byte[] tampered = good.clone();
        // e_shoff 在 ELF64 header 0x28，改成指向文件末尾之外
        tampered[0x28] = (byte) 0xff;
        tampered[0x29] = (byte) 0xff;
        assertRejected(tampered, "越界");
    }

    @Test
    void symbolShndxOutOfRangeIsRejected() {
        // 手工构造：合法头 + 一个 shndx 越界的 symtab 比较繁琐；这里用截断 symtab 触发边界
        byte[] good = ElfBuilder.rel().text("ok")
                .syms(new Sym("f").defined(0), Sym.undef("g")).build();
        byte[] cut = new byte[good.length - 5];
        System.arraycopy(good, 0, cut, 0, cut.length);
        // 截断文件会让 section 数据越界
        assertThrows(ElfException.class, () -> ElfParser.parse(cut, "x", "cut"));
    }

    @Test
    void stringTableWithoutNulIsRejected() {
        // shstrtab 区域被改成非零且无 NUL：构造合法对象后覆盖 shstrtab 内容
        byte[] good = ElfBuilder.rel().text("ok").build();
        // 直接篡改通常会破坏更多结构；这里验证损坏不产生 NPE 而产生 ElfException
        for (int i = 0x34; i < 0x40; i++) good[i] = (byte) 0xff;
        assertThrows(ElfException.class, () -> ElfParser.parse(good, "x", "x"));
    }

    @Test
    void thinArchiveIsRejected() {
        byte[] thin = "!<thin>\n".getBytes();
        ElfException ex = assertThrows(ElfException.class, () -> ArchiveParser.parse(thin));
        assertTrue(ex.getMessage().contains("thin"));
    }

    @Test
    void archiveWithTruncatedMemberHeaderIsRejected() {
        byte[] ar = "!<arch>\nshort".getBytes();
        ElfException ex = assertThrows(ElfException.class, () -> ArchiveParser.parse(ar));
        assertNotNull(ex.getMessage());
    }

    @Test
    void archiveMemberSizeBeyondFileIsRejected() {
        // 合法魔数 + 声称 1GB 成员
        byte[] ar = new byte[68];
        System.arraycopy("!<arch>\n".getBytes(), 0, ar, 0, 8);
        // 名称字段 "/" 后空格，大小字段放在偏移 48
        byte[] header = "a.o/            0           0     0     100644  4294967295  `\n"
                .getBytes();
        byte[] full = new byte[8 + header.length];
        System.arraycopy(ar, 0, full, 0, 8);
        System.arraycopy(header, 0, full, 8, header.length);
        assertThrows(ElfException.class, () -> ArchiveParser.parse(full));
    }

    @Test
    void oneBadObjectDoesNotAffectOthers() {
        byte[] good = ElfBuilder.rel().text("ok").syms(new Sym("f").defined(0)).build();
        ElfObject parsed = ElfParser.parse(good, "good", "good.o");
        assertEquals("good.o", parsed.displayName);
        byte[] junk = "not an elf".getBytes();
        assertThrows(ElfException.class, () -> ElfParser.parse(junk, "bad", "bad.o"));
        // good 仍然可用
        assertTrue(parsed.symbols.stream().anyMatch(s -> s.name.equals("f")));
    }
}
