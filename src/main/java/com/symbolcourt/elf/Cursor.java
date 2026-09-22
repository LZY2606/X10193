package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/** Bounds-checked little/big-endian reader over a byte array. */
final class Cursor {
    private final byte[] data;
    private boolean littleEndian = true;
    private int pos;

    Cursor(byte[] data) {
        this.data = data;
    }

    int length() {
        return data.length;
    }

    void littleEndian(boolean le) {
        this.littleEndian = le;
    }

    void seek(int newPos) {
        check(newPos, 0);
        this.pos = newPos;
    }

    int position() {
        return pos;
    }

    void check(long offset, long length) {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new ElfException("offset out of bounds: off=" + offset + " len=" + length
                    + " file=" + data.length);
        }
    }

    int u8() {
        check(pos, 1);
        return data[pos++] & 0xff;
    }

    int u16() {
        check(pos, 2);
        int v = littleEndian
                ? (data[pos] & 0xff) | ((data[pos + 1] & 0xff) << 8)
                : ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff);
        pos += 2;
        return v;
    }

    long u32() {
        check(pos, 4);
        long v;
        if (littleEndian) {
            v = (data[pos] & 0xffL) | ((data[pos + 1] & 0xffL) << 8)
                    | ((data[pos + 2] & 0xffL) << 16) | ((data[pos + 3] & 0xffL) << 24);
        } else {
            v = ((data[pos] & 0xffL) << 24) | ((data[pos + 1] & 0xffL) << 16)
                    | ((data[pos + 2] & 0xffL) << 8) | (data[pos + 3] & 0xffL);
        }
        pos += 4;
        return v;
    }

    long u64() {
        check(pos, 8);
        long v = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[pos + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[pos + i] & 0xffL);
            }
        }
        pos += 8;
        return v;
    }

    byte[] bytes(long offset, long length) {
        check(offset, length);
        byte[] out = new byte[(int) length];
        System.arraycopy(data, (int) offset, out, 0, (int) length);
        return out;
    }

    /** Reads a NUL-terminated string from {@code strtab} at {@code offset}. */
    static String cstr(byte[] strtab, long offset) {
        if (offset < 0 || offset >= strtab.length) {
            throw new ElfException("string offset out of bounds: " + offset
                    + " strtab=" + strtab.length);
        }
        int end = (int) offset;
        while (end < strtab.length && strtab[end] != 0) {
            end++;
        }
        return new String(strtab, (int) offset, end - (int) offset, StandardCharsets.UTF_8);
    }
}
