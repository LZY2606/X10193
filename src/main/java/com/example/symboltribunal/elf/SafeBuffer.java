package com.example.symboltribunal.elf;

import java.nio.charset.StandardCharsets;

final class SafeBuffer {
    private final byte[] data;
    private final boolean littleEndian;

    SafeBuffer(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    byte u8(long offset) {
        require(offset, 1);
        return data[Math.toIntExact(offset)];
    }

    int u16(long offset) {
        require(offset, 2);
        int index = Math.toIntExact(offset);
        int low = data[index] & 0xff;
        int high = data[index + 1] & 0xff;
        return littleEndian ? low | (high << 8) : (low << 8) | high;
    }

    long u32(long offset) {
        require(offset, 4);
        int index = Math.toIntExact(offset);
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int byteIndex = littleEndian ? i : 3 - i;
            value |= (long) (data[index + byteIndex] & 0xff) << (8 * i);
        }
        return value;
    }

    long u64(long offset) {
        require(offset, 8);
        int index = Math.toIntExact(offset);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int byteIndex = littleEndian ? i : 7 - i;
            value |= (long) (data[index + byteIndex] & 0xff) << (8 * i);
        }
        return value;
    }

    byte[] bytes(long offset, long length) {
        require(offset, length);
        byte[] result = new byte[Math.toIntExact(length)];
        System.arraycopy(data, Math.toIntExact(offset), result, 0, result.length);
        return result;
    }

    String cString(long offset) {
        if (offset < 0 || offset >= data.length) {
            return "";
        }
        int end = Math.toIntExact(offset);
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, Math.toIntExact(offset), end - Math.toIntExact(offset), StandardCharsets.UTF_8);
    }

    String stringTable(long offset, int index) {
        if (index < 0 || offset < 0) {
            return "";
        }
        return cString(offset + index);
    }

    private void require(long offset, long length) {
        if (offset < 0 || length < 0 || offset > Integer.MAX_VALUE || length > Integer.MAX_VALUE
                || offset + length < 0 || offset + length > data.length) {
            throw new ElfFormatException("读取越界: offset=" + offset + ", length=" + length);
        }
    }
}
