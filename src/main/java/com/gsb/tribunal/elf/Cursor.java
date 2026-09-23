package com.gsb.tribunal.elf;

import java.nio.ByteOrder;

/** Bounds-checked random reader over a byte array. */
final class Cursor {
    private final byte[] d;
    private final ByteOrder order;

    Cursor(byte[] data, ByteOrder order) {
        this.d = data;
        this.order = order;
    }

    void require(long offset, long len) throws ElfException {
        if (offset < 0 || len < 0 || offset > d.length || offset + len > d.length || offset + len < 0) {
            throw new ElfException("out of bounds read: offset=" + offset + " length=" + len + " filesize=" + d.length);
        }
    }

    int u1(long off) throws ElfException {
        require(off, 1);
        return d[(int) off] & 0xff;
    }

    int u2(long off) throws ElfException {
        require(off, 2);
        int o = (int) off;
        if (order == ByteOrder.LITTLE_ENDIAN) {
            return (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8);
        }
        return ((d[o] & 0xff) << 8) | (d[o + 1] & 0xff);
    }

    long u4(long off) throws ElfException {
        require(off, 4);
        int o = (int) off;
        if (order == ByteOrder.LITTLE_ENDIAN) {
            return (d[o] & 0xffL) | ((d[o + 1] & 0xffL) << 8) | ((d[o + 2] & 0xffL) << 16) | ((d[o + 3] & 0xffL) << 24);
        }
        return ((d[o] & 0xffL) << 24) | ((d[o + 1] & 0xffL) << 16) | ((d[o + 2] & 0xffL) << 8) | (d[o + 3] & 0xffL);
    }

    long u8(long off) throws ElfException {
        require(off, 8);
        long lo = u4(off);
        long hi = u4(off + 4);
        return order == ByteOrder.LITTLE_ENDIAN ? (hi << 32) | lo : (lo << 32) | hi;
    }

    long word(long off, boolean elf64) throws ElfException {
        return elf64 ? u8(off) : u4(off);
    }

    String cstring(long sectionOffset, long sectionSize, long index) throws ElfException {
        if (index < 0 || index > sectionSize) {
            throw new ElfException("string index " + index + " out of section range [0," + sectionSize + ")");
        }
        long start = sectionOffset + index;
        require(start, 0);
        long end = start;
        while (end < d.length && end < sectionOffset + sectionSize && d[(int) end] != 0) {
            end++;
        }
        if (end >= sectionOffset + sectionSize) {
            throw new ElfException("unterminated string starting at " + start);
        }
        return new String(d, (int) start, (int) (end - start), java.nio.charset.StandardCharsets.UTF_8);
    }
}
