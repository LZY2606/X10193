package org.tribunal.parser;

import java.nio.charset.StandardCharsets;

final class ElfReader {
    private final byte[] data;
    private final boolean littleEndian;

    ElfReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    byte[] bytes() {
        return data;
    }

    void require(int offset, int length) throws ElfFormatException {
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new ElfFormatException("Read out of bounds at offset " + offset + " length " + length);
        }
    }

    int u8(int offset) throws ElfFormatException {
        require(offset, 1);
        return data[offset] & 0xff;
    }

    int u16(int offset) throws ElfFormatException {
        require(offset, 2);
        int value = 0;
        for (int i = 0; i < 2; i++) {
            int shift = littleEndian ? i * 8 : (1 - i) * 8;
            value |= (data[offset + i] & 0xff) << shift;
        }
        return value;
    }

    long u32(int offset) throws ElfFormatException {
        require(offset, 4);
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = littleEndian ? i * 8 : (3 - i) * 8;
            value |= ((long) data[offset + i] & 0xff) << shift;
        }
        return value & 0xffffffffL;
    }

    long u64(int offset) throws ElfFormatException {
        require(offset, 8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int shift = littleEndian ? i * 8 : (7 - i) * 8;
            value |= ((long) data[offset + i] & 0xff) << shift;
        }
        return value;
    }

    byte[] slice(int offset, int length) throws ElfFormatException {
        require(offset, length);
        byte[] result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        return result;
    }

    String cString(int offset, int sectionSize) throws ElfFormatException {
        if (offset < 0 || offset >= sectionSize || sectionSize < 0 || offset > data.length - sectionSize) {
            throw new ElfFormatException("String offset is outside section");
        }
        int start = offset;
        int sectionEnd = Math.min(data.length, offset + sectionSize);
        int end = start;
        while (end < sectionEnd && data[end] != 0) {
            end++;
        }
        if (end >= sectionEnd && data[end - 1] != 0) {
            throw new ElfFormatException("Unterminated string table entry");
        }
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }
}
