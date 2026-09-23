package com.example.symbolcourt.parser;

import java.nio.charset.StandardCharsets;

final class ByteReader {
    private final byte[] data;
    private final boolean littleEndian;

    ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    byte u8(long offset) {
        check(offset, 1);
        return data[(int) offset];
    }

    int u16(long offset) {
        check(offset, 2);
        int base = (int) offset;
        int low = data[base] & 0xff;
        int high = data[base + 1] & 0xff;
        return littleEndian ? low | (high << 8) : (low << 8) | high;
    }

    long u32(long offset) {
        check(offset, 4);
        int base = (int) offset;
        long value = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                value = (value << 8) | (data[base + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                value = (value << 8) | (data[base + i] & 0xffL);
            }
        }
        return value;
    }

    long u64(long offset) {
        check(offset, 8);
        int base = (int) offset;
        long value = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                value = (value << 8) | (data[base + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (data[base + i] & 0xffL);
            }
        }
        return value;
    }

    byte[] range(long offset, long size) {
        if (size < 0) {
            throw new BinaryParseException("negative range size");
        }
        check(offset, size);
        byte[] result = new byte[(int) size];
        System.arraycopy(data, (int) offset, result, 0, result.length);
        return result;
    }

    String cString(long offset) {
        if (offset < 0 || offset >= data.length) {
            return "";
        }
        int end = (int) offset;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, (int) offset, end - (int) offset, StandardCharsets.UTF_8);
    }

    private void check(long offset, long size) {
        if (offset < 0 || size < 0 || offset > data.length || offset + size > data.length) {
            throw new BinaryParseException("read " + size + " bytes at " + offset + " outside binary length " + data.length);
        }
    }
}
