package com.tribunal.elf;

import java.nio.charset.StandardCharsets;

/**
 * Bounds checked, endian aware reader over one byte array.
 * Every access is validated up-front so a corrupt file can never produce an
 * out of bounds read here; it surfaces as {@link ElfFormatException} instead.
 */
final class ElfReader {
    final byte[] data;
    final boolean bigEndian;
    final boolean elf64;

    ElfReader(byte[] data, boolean bigEndian, boolean elf64) {
        this.data = data;
        this.bigEndian = bigEndian;
        this.elf64 = elf64;
    }

    void require(long start, long length) throws ElfFormatException {
        if (start < 0 || length < 0 || start > Integer.MAX_VALUE || length > Integer.MAX_VALUE
                || start + length < 0 || start + length > data.length) {
            throw new ElfFormatException("out of bounds read at " + start + " length " + length
                    + " (file size " + data.length + ")");
        }
    }

    byte u8(long off) throws ElfFormatException {
        require(off, 1);
        return data[(int) off];
    }

    int u16(long off) throws ElfFormatException {
        require(off, 2);
        int o = (int) off;
        int a = data[o] & 0xff;
        int b = data[o + 1] & 0xff;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    long u32(long off) throws ElfFormatException {
        require(off, 4);
        int o = (int) off;
        long v;
        if (bigEndian) {
            v = ((long) (data[o] & 0xff) << 24) | ((data[o + 1] & 0xff) << 16)
                    | ((data[o + 2] & 0xff) << 8) | (data[o + 3] & 0xff);
        } else {
            v = (data[o] & 0xff) | ((data[o + 1] & 0xff) << 8)
                    | ((data[o + 2] & 0xff) << 16) | ((long) (data[o + 3] & 0xff) << 24);
        }
        return v & 0xffffffffL;
    }

    long u64(long off) throws ElfFormatException {
        require(off, 8);
        int o = (int) off;
        long v = 0;
        if (bigEndian) {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[o + i] & 0xffL);
            }
        } else {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[o + i] & 0xffL);
            }
        }
        return v;
    }

    long addr(long off) throws ElfFormatException {
        return elf64 ? u64(off) : u32(off);
    }

    long off(long off) throws ElfFormatException {
        return elf64 ? u64(off) : u32(off);
    }

    static int addrSize(boolean elf64) {
        return elf64 ? 8 : 4;
    }

    /** Read a NUL terminated string from a string table section, bounds checked. */
    String strtabString(long strOffset, long strSize, int index) throws ElfFormatException {
        if (index < 0) {
            return "";
        }
        long start = strOffset + index;
        if (strSize <= index) {
            throw new ElfFormatException("string table index " + index + " out of bounds (size "
                    + strSize + ")");
        }
        require(start, 1);
        int end = (int) start;
        int limit = (int) Math.min(data.length, strOffset + strSize);
        while (end < limit && data[end] != 0) {
            end++;
        }
        if (end >= limit) {
            throw new ElfFormatException("unterminated string table entry at " + start);
        }
        if (end == start) {
            return "";
        }
        return new String(data, (int) start, end - (int) start, StandardCharsets.UTF_8);
    }
}
