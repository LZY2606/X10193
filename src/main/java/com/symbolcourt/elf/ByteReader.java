package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/**
 * Bounds-checked random reader over a byte array. Every access verifies both
 * the length and the index: corrupt or truncated objects fail here instead of
 * leaking out as raw exceptions.
 */
public final class ByteReader {
    private final byte[] data;
    private final boolean le;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.le = littleEndian;
    }

    public int length() {
        return data.length;
    }

    public void require(long offset, long size) throws ElfException {
        if (offset < 0 || size < 0 || offset > data.length || size > (long) data.length - offset) {
            throw new ElfException("out of bounds read: offset=" + offset + " size=" + size
                    + " length=" + data.length);
        }
    }

    public byte u8(long off) throws ElfException {
        require(off, 1);
        return data[(int) off];
    }

    public int u16(long off) throws ElfException {
        require(off, 2);
        int a = data[(int) off] & 0xff;
        int b = data[(int) off + 1] & 0xff;
        return le ? (a | (b << 8)) : ((a << 8) | b);
    }

    public long u32(long off) throws ElfException {
        require(off, 4);
        long v = 0;
        if (le) {
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (data[(int) off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (data[(int) off + i] & 0xffL);
            }
        }
        return v & 0xffffffffL;
    }

    public long u64(long off) throws ElfException {
        require(off, 8);
        long v = 0;
        if (le) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[(int) off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[(int) off + i] & 0xffL);
            }
        }
        return v;
    }

    public byte[] bytes(long off, int len) throws ElfException {
        require(off, len);
        byte[] out = new byte[len];
        System.arraycopy(data, (int) off, out, 0, len);
        return out;
    }

    /** Reads a NUL terminated string from a string-table section. */
    public String cstr(long tableStart, long strIndex) throws ElfException {
        if (strIndex < 0) {
            throw new ElfException("negative string index");
        }
        long start = tableStart + strIndex;
        if (start > data.length) {
            throw new ElfException("string index past end of section");
        }
        int end = (int) start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end >= data.length) {
            throw new ElfException("unterminated string");
        }
        return new String(data, (int) start, end - (int) start, StandardCharsets.UTF_8);
    }
}
