package tribunal.elf;

import java.nio.charset.StandardCharsets;

final class ByteReader {
    private final byte[] data;
    private final boolean littleEndian;

    ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    boolean fits(long offset, long length) {
        return offset >= 0 && length >= 0 && offset <= data.length && (long) data.length - offset >= length;
    }

    void require(long offset, long length) {
        if (!fits(offset, length)) {
            throw new ElfParseException("读取越过文件边界: offset=" + offset + ", length=" + length
                    + ", file=" + data.length);
        }
    }

    int u8(long offset) {
        require(offset, 1);
        return data[(int) offset] & 0xff;
    }

    int u16(long offset) {
        require(offset, 2);
        int a = u8(offset);
        int b = u8(offset + 1);
        return littleEndian ? (b << 8) | a : (a << 8) | b;
    }

    int u32AsInt(long offset) {
        return (int) u32(offset);
    }

    long u32(long offset) {
        require(offset, 4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = u8(offset + (littleEndian ? i : 3 - i));
            v = (v << 8) | b;
        }
        return v & 0xffffffffL;
    }

    long u64(long offset) {
        require(offset, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int b = u8(offset + (littleEndian ? i : 7 - i));
            v = (v << 8) | b;
        }
        return v;
    }

    long word(long offset, boolean elf64) {
        return elf64 ? u64(offset) : u32(offset);
    }

    byte[] slice(long offset, long length) {
        require(offset, length);
        byte[] out = new byte[(int) length];
        System.arraycopy(data, (int) offset, out, 0, (int) length);
        return out;
    }

    String cString(long offset) {
        if (offset < 0 || offset >= data.length) {
            throw new ElfParseException("字符串偏移越界: " + offset);
        }
        int end = (int) offset;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, (int) offset, end - (int) offset, StandardCharsets.UTF_8);
    }
}
