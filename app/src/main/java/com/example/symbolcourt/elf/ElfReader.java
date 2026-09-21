package com.example.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

final class ElfReader {
    private final byte[] data;
    private final boolean littleEndian;

    ElfReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    int u8(long offset) throws ElfFormatException {
        check(offset, 1);
        return data[(int) offset] & 0xff;
    }

    int u16(long offset) throws ElfFormatException {
        check(offset, 2);
        int a = u8(offset);
        int b = u8(offset + 1);
        return littleEndian ? a | (b << 8) : (a << 8) | b;
    }

    long u32(long offset) throws ElfFormatException {
        check(offset, 4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = u8(offset + i);
            v |= (long) b << (littleEndian ? i * 8 : (3 - i) * 8);
        }
        return v & 0xffffffffL;
    }

    long u64(long offset) throws ElfFormatException {
        check(offset, 8);
        long low = u32(offset);
        long high = u32(offset + 4);
        return littleEndian ? low | (high << 32) : (low << 32) | high;
    }

    long word(boolean elf64, long offset) throws ElfFormatException {
        return elf64 ? u64(offset) : u32(offset);
    }

    String cString(long offset) throws ElfFormatException {
        if (offset < 0 || offset >= data.length) {
            throw new ElfFormatException("string offset out of bounds: " + offset);
        }
        int end = (int) offset;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end >= data.length) {
            throw new ElfFormatException("unterminated string at " + offset);
        }
        return new String(data, (int) offset, end - (int) offset, StandardCharsets.UTF_8);
    }

    void check(long offset, long length) throws ElfFormatException {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new ElfFormatException("read out of bounds at " + offset + " length " + length);
        }
    }

    void checkSection(long offset, long size) throws ElfFormatException {
        if (offset < 0 || size < 0 || offset > data.length || size > data.length - offset) {
            throw new ElfFormatException("section out of bounds at " + offset + " size " + size);
        }
    }
}
