package com.tribunal.elf;

final class ByteReader {
    private final byte[] data;
    private final boolean littleEndian;

    ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    byte s8(long offset) throws ElfParseException {
        require(offset, 1);
        return data[(int) offset];
    }

    int u8(long offset) throws ElfParseException {
        return s8(offset) & 0xff;
    }

    int u16(long offset) throws ElfParseException {
        require(offset, 2);
        int a = data[(int) offset] & 0xff;
        int b = data[(int) (offset + 1)] & 0xff;
        return littleEndian ? a | (b << 8) : (a << 8) | b;
    }

    long u32(long offset) throws ElfParseException {
        require(offset, 4);
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = littleEndian ? i * 8 : (3 - i) * 8;
            value |= (long) (data[(int) offset + i] & 0xff) << shift;
        }
        return value & 0xffffffffL;
    }

    long u64(long offset) throws ElfParseException {
        require(offset, 8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int shift = littleEndian ? i * 8 : (7 - i) * 8;
            value |= (long) (data[(int) offset + i] & 0xff) << shift;
        }
        return value;
    }

    String cString(long offset, long tableSize) throws ElfParseException {
        if (offset < 0 || offset >= tableSize) {
            throw new ElfParseException("string offset " + offset + " outside table of size " + tableSize);
        }
        int start = (int) offset;
        int end = start;
        while (end < start + tableSize - offset && end < data.length && data[end] != 0) {
            end++;
        }
        if (end >= data.length || end >= start + tableSize - offset) {
            throw new ElfParseException("unterminated string in table");
        }
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }

    byte[] slice(long offset, long size) throws ElfParseException {
        require(offset, size);
        byte[] result = new byte[(int) size];
        System.arraycopy(data, (int) offset, result, 0, result.length);
        return result;
    }

    private void require(long offset, long size) throws ElfParseException {
        if (offset < 0 || size < 0 || offset > data.length || size > data.length - offset) {
            throw new ElfParseException("read at " + offset + " size " + size + " exceeds object length " + data.length);
        }
    }
}
