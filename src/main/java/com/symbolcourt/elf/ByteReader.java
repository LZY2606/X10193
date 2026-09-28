package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

public class ByteReader {
    public static final int SHT_NULL = 0;
    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_NOTE = 7;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_GROUP = 17;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_STRTAB = 5;
    public static final int DT_VERNEEDNUM = 0x6fffffff;
    public static final int DT_VERDEFNUM = 0x6ffffffc;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_GNU_UNIQUE = 10;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_FILE = 4;
    public static final int STT_TLS = 6;
    public static final int STT_GNU_IFUNC = 10;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_VERSION = 0x7fff;
    public static final int VERSYM_HIDDEN = 0x8000;

    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_386_COPY = 5;
    public static final int R_386_JMP_SLOT = 7;
    public static final int R_386_RELATIVE = 8;

    private final byte[] data;
    private final boolean bigEndian;

    public ByteReader(byte[] data, boolean bigEndian) {
        this.data = data;
        this.bigEndian = bigEndian;
    }

    public int length() {
        return data.length;
    }

    public int u8(long offset) {
        if (offset < 0 || offset >= data.length) {
            throw new ElfFormatException("读取越界: offset=" + offset + " len=" + data.length);
        }
        return data[(int) offset] & 0xff;
    }

    public int u16(long offset) {
        int hi = u8(offset);
        int lo = u8(offset + 1);
        return bigEndian ? (hi << 8) | lo : (lo << 8) | hi;
    }

    public long u32(long offset) {
        long a = u8(offset);
        long b = u8(offset + 1);
        long c = u8(offset + 2);
        long d = u8(offset + 3);
        if (bigEndian) {
            return (a << 24) | (b << 16) | (c << 8) | d;
        }
        return (d << 24) | (c << 16) | (b << 8) | a;
    }

    public long u64(long offset) {
        long value = 0;
        if (bigEndian) {
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | u8(offset + i);
            }
        } else {
            for (int i = 7; i >= 0; i--) {
                value = (value << 8) | u8(offset + i);
            }
        }
        return value;
    }

    public void require(long offset, long count) {
        if (offset < 0 || count < 0 || offset > data.length || count > data.length - offset) {
            throw new ElfFormatException("区间越界: offset=" + offset + " count=" + count
                    + " len=" + data.length);
        }
    }

    public byte[] slice(long offset, long count) {
        require(offset, count);
        byte[] out = new byte[(int) count];
        System.arraycopy(data, (int) offset, out, 0, (int) count);
        return out;
    }

    public String cString(long offset) {
        if (offset < 0 || offset >= data.length) {
            throw new ElfFormatException("字符串越界: offset=" + offset);
        }
        int end = (int) offset;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, (int) offset, end - (int) offset, StandardCharsets.UTF_8);
    }

    public String stringFromTable(long tableOffset, long tableSize, long index) {
        if (index < 0 || index >= tableSize) {
            throw new ElfFormatException("字符串表索引越界: index=" + index + " size=" + tableSize);
        }
        long start = tableOffset + index;
        if (start >= data.length) {
            throw new ElfFormatException("字符串表偏移越界");
        }
        int end = (int) start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end - start > 4096) {
            throw new ElfFormatException("字符串表项过长，疑似损坏");
        }
        return new String(data, (int) start, end - (int) start, StandardCharsets.UTF_8);
    }

    public static class ElfFormatException extends RuntimeException {
        public ElfFormatException(String message) {
            super(message);
        }
    }
}
