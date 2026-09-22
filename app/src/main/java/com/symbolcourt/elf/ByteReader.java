package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/** Big/little endian byte reader over one ELF image with hard bounds checks. */
final class ByteReader {
    private final byte[] data;
    private final boolean le;

    ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.le = littleEndian;
    }

    int length() {
        return data.length;
    }

    private void require(long off, int n) {
        if (off < 0 || n < 0 || off > data.length - n) {
            throw new ElfFormatException("read " + n + " bytes at " + off
                    + " exceeds image length " + data.length);
        }
    }

    byte u8(long off) {
        require(off, 1);
        return data[(int) off];
    }

    int u16(long off) {
        require(off, 2);
        int a = data[(int) off] & 0xff;
        int b = data[(int) off + 1] & 0xff;
        return le ? (a | (b << 8)) : ((a << 8) | b);
    }

    long u32(long off) {
        require(off, 4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = data[(int) off + (le ? i : 3 - i)] & 0xff;
            v = (v << 8) | b;
        }
        return v;
    }

    long u64(long off) {
        require(off, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int b = data[(int) off + (le ? i : 7 - i)] & 0xff;
            v = (v << 8) | b;
        }
        return v;
    }

    /** NUL terminated string starting at offset, or empty when NUL missing. */
    String cstr(long off) {
        if (off < 0 || off >= data.length) {
            throw new ElfFormatException("string offset " + off + " out of range");
        }
        int end = (int) off;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, (int) off, end - (int) off, StandardCharsets.UTF_8);
    }

    String cstrIn(long sectionStart, long sectionSize, long relativeOff) {
        if (relativeOff < 0 || relativeOff >= sectionSize) {
            throw new ElfFormatException("relative string offset " + relativeOff
                    + " outside section of size " + sectionSize);
        }
        return cstr(sectionStart + relativeOff);
    }
}
