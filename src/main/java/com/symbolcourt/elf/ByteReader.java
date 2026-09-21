package com.symbolcourt.elf;

/** Bounds-checked little/big-endian reader over a byte array. */
public final class ByteReader {
    private final byte[] data;
    private final boolean littleEndian;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    public int length() {
        return data.length;
    }

    private void check(long offset, int size) {
        if (offset < 0 || size < 0 || offset + size > data.length) {
            throw new ElfParseException("read out of bounds: offset=" + offset + " size=" + size
                    + " length=" + data.length);
        }
    }

    public int u8(long offset) {
        check(offset, 1);
        return data[(int) offset] & 0xff;
    }

    public int u16(long offset) {
        check(offset, 2);
        int o = (int) offset;
        if (littleEndian) {
            return (data[o] & 0xff) | ((data[o + 1] & 0xff) << 8);
        }
        return ((data[o] & 0xff) << 8) | (data[o + 1] & 0xff);
    }

    public long u32(long offset) {
        check(offset, 4);
        int o = (int) offset;
        long v;
        if (littleEndian) {
            v = (data[o] & 0xffL) | ((data[o + 1] & 0xffL) << 8)
                    | ((data[o + 2] & 0xffL) << 16) | ((data[o + 3] & 0xffL) << 24);
        } else {
            v = ((data[o] & 0xffL) << 24) | ((data[o + 1] & 0xffL) << 16)
                    | ((data[o + 2] & 0xffL) << 8) | (data[o + 3] & 0xffL);
        }
        return v & 0xffffffffL;
    }

    public long u64(long offset) {
        check(offset, 8);
        int o = (int) offset;
        long v = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[o + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[o + i] & 0xffL);
            }
        }
        return v;
    }

    public String cstr(long offset, long maxEnd) {
        if (offset < 0 || offset >= maxEnd || maxEnd > data.length) {
            throw new ElfParseException("string out of bounds: offset=" + offset + " end=" + maxEnd);
        }
        long end = offset;
        while (end < maxEnd && data[(int) end] != 0) {
            end++;
        }
        return new String(data, (int) offset, (int) (end - offset), java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] slice(long offset, long size) {
        check(offset, (int) size);
        byte[] out = new byte[(int) size];
        System.arraycopy(data, (int) offset, out, 0, (int) size);
        return out;
    }
}
