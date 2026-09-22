package local.symboltribunal.elf;

import java.nio.charset.StandardCharsets;

final class ElfReader {
    private final byte[] data;
    private final boolean littleEndian;

    ElfReader(byte[] data, boolean littleEndian) {
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
        int b = data[(int) (offset + 1)] & 0xff;
        return littleEndian ? a | (b << 8) : b | (a << 8);
    }

    long u32(long offset) {
        require(offset, 4);
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int byteIndex = littleEndian ? i : 3 - i;
            value |= ((long) data[(int) (offset + byteIndex)] & 0xffL) << (i * 8);
        }
        return value & 0xffffffffL;
    }

    long u64(long offset) {
        require(offset, 8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int byteIndex = littleEndian ? i : 7 - i;
            value |= ((long) data[(int) (offset + byteIndex)] & 0xffL) << (i * 8);
        }
        return value;
    }

    long word(long offset, boolean elf64) {
        return elf64 ? u64(offset) : u32(offset);
    }

    byte[] bytes(long offset, int length) {
        require(offset, length);
        byte[] result = new byte[length];
        System.arraycopy(data, (int) offset, result, 0, length);
        return result;
    }

    String cString(long offset) {
        if (offset < 0 || offset >= data.length) {
            throw new ElfParseException("string offset out of bounds: " + offset);
        }
        int end = (int) offset;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end >= data.length) {
            throw new ElfParseException("unterminated string at " + offset);
        }
        return new String(data, (int) offset, end - (int) offset, StandardCharsets.UTF_8);
    }

    String tableString(long tableOffset, long tableSize, long index) {
        if (index < 0 || index >= tableSize) {
            throw new ElfParseException("symbol/string table index out of bounds: " + index);
        }
        long offset = tableOffset + index;
        if (offset >= data.length) {
            throw new ElfParseException("string outside file: " + offset);
        }
        return cString(offset);
    }

    void require(long offset, long length) {
        if (offset < 0 || length < 0 || offset > data.length || offset + length > data.length) {
            throw new ElfParseException("read out of bounds: offset=" + offset + ", length=" + length + ", file=" + data.length);
        }
    }
}
