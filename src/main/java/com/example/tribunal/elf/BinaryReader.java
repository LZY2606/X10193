package com.example.tribunal.elf;

import java.nio.charset.StandardCharsets;

final class BinaryReader {
    private final byte[] data;
    private final boolean littleEndian;

    BinaryReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    byte u8(long offset) {
        require(offset, 1);
        return data[Math.toIntExact(offset)];
    }

    short i8(long offset) {
        return (short) (u8(offset) & 0xff);
    }

    int u16(long offset) {
        require(offset, 2);
        int p = Math.toIntExact(offset);
        int a = data[p] & 0xff;
        int b = data[p + 1] & 0xff;
        return littleEndian ? a | (b << 8) : (a << 8) | b;
    }

    long u32(long offset) {
        require(offset, 4);
        int p = Math.toIntExact(offset);
        long a = data[p] & 0xff;
        long b = data[p + 1] & 0xff;
        long c = data[p + 2] & 0xff;
        long d = data[p + 3] & 0xff;
        if (littleEndian) {
            return a | (b << 8) | (c << 16) | (d << 24);
        }
        return (a << 24) | (b << 16) | (c << 8) | d;
    }

    long u64(long offset) {
        require(offset, 8);
        int p = Math.toIntExact(offset);
        long result = 0;
        for (int i = 0; i < 8; i++) {
            int shift = littleEndian ? i * 8 : (7 - i) * 8;
            result |= (long) (data[p + i] & 0xff) << shift;
        }
        return result;
    }

    long word(long offset, boolean elf64) {
        return elf64 ? u64(offset) : u32(offset);
    }

    void require(long offset, long size) {
        if (offset < 0 || size < 0 || offset > data.length || size > data.length - offset) {
            throw new ElfFormatException("读取越界 offset=" + offset + " size=" + size + " length=" + data.length);
        }
    }

    byte[] bytes(long offset, long size) {
        require(offset, size);
        byte[] result = new byte[Math.toIntExact(size)];
        System.arraycopy(data, Math.toIntExact(offset), result, 0, result.length);
        return result;
    }

    String cString(long offset, long maxLength) {
        require(offset, 0);
        long limit = Math.min(Math.max(offset, 0) + maxLength, data.length);
        int start = Math.toIntExact(offset);
        int end = start;
        while (end < limit && data[end] != 0) {
            end++;
        }
        if (end >= limit && offset + maxLength > data.length) {
            throw new ElfFormatException("字符串缺少 NUL 结尾");
        }
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    int size() {
        return data.length;
    }
}
