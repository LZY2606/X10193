package com.symtribunal.elf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounds-checked reader over the raw object bytes. */
public final class ByteReader {
    private final byte[] data;
    private final boolean little;

    public ByteReader(byte[] data, boolean little) {
        this.data = data;
        this.little = little;
    }

    public int u8(long offset) {
        require(offset, 1);
        return data[(int) offset] & 0xff;
    }

    public int u16(long offset) {
        require(offset, 2);
        return ByteBuffer.wrap(data, (int) offset, 2)
                .order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).getShort() & 0xffff;
    }

    public long u32(long offset) {
        require(offset, 4);
        return ByteBuffer.wrap(data, (int) offset, 4)
                .order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).getInt() & 0xffffffffL;
    }

    public long u64(long offset) {
        require(offset, 8);
        return ByteBuffer.wrap(data, (int) offset, 8)
                .order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).getLong();
    }

    public byte[] range(long offset, long length) {
        if (length < 0) {
            throw new ElfParseException("negative length " + length);
        }
        require(offset, length);
        byte[] result = new byte[(int) length];
        System.arraycopy(data, (int) offset, result, 0, (int) length);
        return result;
    }

    public String cString(long offset) {
        if (offset < 0 || offset >= data.length) {
            throw new ElfParseException("string out of range at " + offset);
        }
        int start = (int) offset;
        int end = start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void require(long offset, long length) {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new ElfParseException(
                    "out of bounds: offset=" + offset + " length=" + length + " file=" + data.length);
        }
    }
}
