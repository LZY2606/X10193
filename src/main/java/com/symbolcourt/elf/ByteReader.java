package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/** Bounds-checked, endian-aware byte window over an entire file image. */
public final class ByteReader {
    private final byte[] data;
    private final boolean le;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.le = littleEndian;
    }

    public int size() {
        return data.length;
    }

    private void check(long off, int len) throws ElfException {
        if (off < 0 || len < 0 || off > (long) data.length - len) {
            throw new ElfException("read out of bounds: offset=" + off + " length=" + len
                    + " size=" + data.length);
        }
    }

    public byte i8(long off) throws ElfException {
        check(off, 1);
        return data[(int) off];
    }

    public int u8(long off) throws ElfException {
        return i8(off) & 0xff;
    }

    public int u16(long off) throws ElfException {
        check(off, 2);
        int a = data[(int) off] & 0xff;
        int b = data[(int) off + 1] & 0xff;
        return le ? (a | (b << 8)) : ((a << 8) | b);
    }

    public long u32(long off) throws ElfException {
        check(off, 4);
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
        return v;
    }

    public long u64(long off) throws ElfException {
        check(off, 8);
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

    public byte[] range(long off, int len) throws ElfException {
        check(off, len);
        byte[] out = new byte[len];
        System.arraycopy(data, (int) off, out, 0, len);
        return out;
    }

    /** Reads a NUL terminated string from the given section/string table. */
    public String cstr(long tableOffset, long index) throws ElfException {
        if (index < 0 || index > (long) data.length) {
            throw new ElfException("string index out of bounds: " + index);
        }
        long start = tableOffset + index;
        if (start > data.length) {
            throw new ElfException("string starts past end of image");
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
