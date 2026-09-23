package com.tribunal.elf;

import java.nio.charset.StandardCharsets;

/**
 * Bounded random-access reader over a byte buffer. Every access is range
 * checked so that a truncated or corrupted object can never cause an
 * out-of-bounds read; callers receive ElfFormatException instead.
 */
public final class ByteReader {

    private final byte[] data;
    public final boolean littleEndian;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    public int size() {
        return data.length;
    }

    public byte[] raw() {
        return data;
    }

    public void require(int off, int len) {
        if (off < 0 || len < 0 || (long) off + len > data.length) {
            throw new ElfFormatException(
                    "read past end of file: offset=" + off + " length=" + len + " size=" + data.length);
        }
    }

    public int u8(int off) {
        require(off, 1);
        return data[off] & 0xff;
    }

    public int u16(int off) {
        require(off, 2);
        if (littleEndian) {
            return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
        }
        return ((data[off] & 0xff) << 8) | (data[off + 1] & 0xff);
    }

    public long u32(int off) {
        require(off, 4);
        long v = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (data[off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (data[off + i] & 0xffL);
            }
        }
        return v;
    }

    public long u64(int off) {
        require(off, 8);
        long v = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[off + i] & 0xffL);
            }
        }
        return v;
    }

    public byte[] bytes(int off, int len) {
        require(off, len);
        byte[] out = new byte[len];
        System.arraycopy(data, off, out, 0, len);
        return out;
    }

    /** Read a NUL-terminated string starting at {@code off}, bounded by the file. */
    public String cString(int off) {
        if (off < 0 || off >= data.length) {
            throw new ElfFormatException("string offset out of range: " + off);
        }
        int end = off;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, off, end - off, StandardCharsets.UTF_8);
    }
}
