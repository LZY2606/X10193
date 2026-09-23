package com.court.verdict.elf;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 带严格边界检查的字节读取器，任何越界访问都会抛出 ElfParseException。 */
public final class ElfReader {
    private final byte[] data;
    private final boolean littleEndian;

    public ElfReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    public int size() {
        return data.length;
    }

    private void check(long off, long len) {
        if (off < 0 || len < 0 || off + len < off || off + len > data.length) {
            throw new ElfParseException("越界读取: offset=" + off + " len=" + len + " 文件大小=" + data.length);
        }
    }

    public int u8(long off) {
        check(off, 1);
        return data[(int) off] & 0xff;
    }

    public int u16(long off) {
        check(off, 2);
        int i = (int) off;
        if (littleEndian) {
            return (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8);
        }
        return ((data[i] & 0xff) << 8) | (data[i + 1] & 0xff);
    }

    public long u32(long off) {
        check(off, 4);
        int i = (int) off;
        long v;
        if (littleEndian) {
            v = (data[i] & 0xffL) | ((data[i + 1] & 0xffL) << 8)
                    | ((data[i + 2] & 0xffL) << 16) | ((data[i + 3] & 0xffL) << 24);
        } else {
            v = ((data[i] & 0xffL) << 24) | ((data[i + 1] & 0xffL) << 16)
                    | ((data[i + 2] & 0xffL) << 8) | (data[i + 3] & 0xffL);
        }
        return v & 0xffffffffL;
    }

    public long u64(long off) {
        check(off, 8);
        long v = 0;
        if (littleEndian) {
            for (int k = 7; k >= 0; k--) {
                v = (v << 8) | (data[(int) off + k] & 0xffL);
            }
        } else {
            for (int k = 0; k < 8; k++) {
                v = (v << 8) | (data[(int) off + k] & 0xffL);
            }
        }
        return v;
    }

    public byte[] bytes(long off, long len) {
        check(off, len);
        return Arrays.copyOfRange(data, (int) off, (int) (off + len));
    }

    /** 读取 NUL 结尾字符串，必须在 [off, maxEnd) 内找到终止符。 */
    public String cstr(long off, long maxEnd) {
        if (off < 0 || off >= maxEnd || maxEnd > data.length) {
            throw new ElfParseException("字符串偏移越界: off=" + off + " maxEnd=" + maxEnd);
        }
        long p = off;
        while (p < maxEnd && data[(int) p] != 0) {
            p++;
        }
        if (p >= maxEnd) {
            throw new ElfParseException("字符串缺少终止符: off=" + off);
        }
        return new String(data, (int) off, (int) (p - off), StandardCharsets.UTF_8);
    }
}
