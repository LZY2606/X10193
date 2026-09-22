package com.example.tribunal.elf;

final class BinaryReader {
    private final byte[] data;
    private final boolean littleEndian;

    BinaryReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    int u8(long offset) {
        require(offset, 1);
        return data[(int) offset] & 0xff;
    }

    int u16(long offset) {
        require(offset, 2);
        int a = data[(int) offset] & 0xff;
        int b = data[(int) offset + 1] & 0xff;
        return littleEndian ? (b << 8) | a : (a << 8) | b;
    }

    long u32(long offset) {
        require(offset, 4);
        long value = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                value = (value << 8) | (data[(int) offset + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                value = (value << 8) | (data[(int) offset + i] & 0xffL);
            }
        }
        return value & 0xffffffffL;
    }

    long u64(long offset) {
        require(offset, 8);
        long value = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                value = (value << 8) | (data[(int) offset + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (data[(int) offset + i] & 0xffL);
            }
        }
        return value;
    }

    long signed(long unsigned, int width) {
        int bits = width * 8;
        long mask = 1L << (bits - 1);
        return (unsigned ^ mask) - mask;
    }

    String cString(long offset) {
        if (offset < 0 || offset >= data.length) {
            return "";
        }
        int start = (int) offset;
        int end = start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }

    byte[] slice(long offset, long length) {
        require(offset, length);
        byte[] out = new byte[(int) length];
        System.arraycopy(data, (int) offset, out, 0, (int) length);
        return out;
    }

    boolean inRange(long offset, long length) {
        return offset >= 0 && length >= 0 && offset <= data.length && length <= data.length - offset;
    }

    private void require(long offset, long length) {
        if (!inRange(offset, length)) {
            throw new ElfParseException("读取越过 ELF 边界: offset=" + offset + ", length=" + length);
        }
    }
}
