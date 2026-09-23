package com.tribunal.elf;

/**
 * 对字节数组做边界检查的小端/大端读取器。
 * 所有越界访问都抛 {@link ElfParseException}，避免解析损坏对象时崩溃。
 */
final class BoundsReader {
    private final byte[] data;
    private final boolean le;

    BoundsReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.le = littleEndian;
    }

    int length() { return data.length; }

    void require(long start, long len) {
        if (start < 0 || len < 0 || start > data.length || len > (long) data.length - start) {
            throw new ElfParseException("读取越界： 偏移=" + start + " 长度=" + len + " 文件大小=" + data.length);
        }
    }

    int u8(long off) {
        require(off, 1);
        return data[(int) off] & 0xff;
    }

    int u16(long off) {
        require(off, 2);
        int b0 = data[(int) off] & 0xff, b1 = data[(int) off + 1] & 0xff;
        return le ? (b0 | (b1 << 8)) : ((b0 << 8) | b1);
    }

    long u32(long off) {
        require(off, 4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = data[(int) off + (le ? i : 3 - i)] & 0xff;
            v |= (long) b << (8 * i);
        }
        return v & 0xffffffffL;
    }

    long u64(long off) {
        require(off, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int b = data[(int) off + (le ? i : 7 - i)] & 0xff;
            v |= (long) b << (8 * i);
        }
        return v;
    }

    byte[] bytes(long off, long len) {
        require(off, len);
        byte[] out = new byte[(int) len];
        System.arraycopy(data, (int) off, out, 0, (int) len);
        return out;
    }

    String cstr(long off, long maxLen) {
        require(off, 0);
        long end = Math.min((long) data.length, off + maxLen);
        long p = off;
        while (p < end && data[(int) p] != 0) p++;
        if (p >= end && p < data.length && data[(int) p] != 0) {
            throw new ElfParseException("字符串未终止： 偏移=" + off);
        }
        return new String(data, (int) off, (int) (p - off), java.nio.charset.StandardCharsets.UTF_8);
    }
}
