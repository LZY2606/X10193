package com.symtribunal.elf;

import java.nio.charset.StandardCharsets;

/**
 * Bounds checked reader over an immutable byte buffer. Every access is
 * length and offset validated; malformed input raises {@link ElfException}
 * instead of leaking into {@link ArrayIndexOutOfBoundsException}.
 */
public final class ByteReader {
    private final byte[] data;
    private final boolean littleEndian;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    public int size() {
        return data.length;
    }

    public byte[] bytes() {
        return data;
    }

    public boolean littleEndian() {
        return littleEndian;
    }

    private void checkRange(int offset, int length) {
        if (offset < 0 || length < 0 || (long) offset + length > data.length) {
            throw new ElfException("read out of bounds: offset=" + offset
                    + " length=" + length + " size=" + data.length);
        }
    }

    public int u8(int offset) {
        checkRange(offset, 1);
        return data[offset] & 0xff;
    }

    public int u16(int offset) {
        checkRange(offset, 2);
        if (littleEndian) {
            return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
        }
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    public long u32(int offset) {
        checkRange(offset, 4);
        long value = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                value = (value << 8) | (data[offset + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                value = (value << 8) | (data[offset + i] & 0xffL);
            }
        }
        return value & 0xffffffffL;
    }

    public long u64(int offset) {
        checkRange(offset, 8);
        long value = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                value = (value << 8) | (data[offset + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (data[offset + i] & 0xffL);
            }
        }
        return value;
    }

    public byte[] slice(int offset, int length) {
        checkRange(offset, length);
        byte[] out = new byte[length];
        System.arraycopy(data, offset, out, 0, length);
        return out;
    }

    public String cstring(int offset) {
        if (offset < 0 || offset >= data.length) {
            throw new ElfException("string offset out of bounds: " + offset);
        }
        int end = offset;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, offset, end - offset, StandardCharsets.UTF_8);
    }

    public String cstring(int offset, int maxLength) {
        if (offset < 0 || offset >= data.length) {
            throw new ElfException("string offset out of bounds: " + offset);
        }
        int end = offset;
        int limit = Math.min(data.length, offset + maxLength);
        while (end < limit && data[end] != 0) {
            end++;
        }
        return new String(data, offset, end - offset, StandardCharsets.UTF_8);
    }
}
