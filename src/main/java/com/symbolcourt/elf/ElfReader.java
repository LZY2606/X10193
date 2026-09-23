package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/**
 * 小端（ELF64 fixture 均为小端）只读访问器，所有读取都做长度与索引边界检查。
 */
public class ElfReader {
    private final byte[] data;

    public ElfReader(byte[] data) {
        this.data = data;
    }

    public int length() {
        return data.length;
    }

    public byte[] bytes() {
        return data;
    }

    public int u8(long off) throws ElfException {
        int o = check(off, 1);
        return data[o] & 0xff;
    }

    public int u16(long off) throws ElfException {
        int o = check(off, 2);
        return (data[o] & 0xff) | ((data[o + 1] & 0xff) << 8);
    }

    public long u32(long off) throws ElfException {
        int o = check(off, 4);
        return (data[o] & 0xffL) | ((data[o + 1] & 0xffL) << 8)
                | ((data[o + 2] & 0xffL) << 16) | ((data[o + 3] & 0xffL) << 24);
    }

    public long u64(long off) throws ElfException {
        int o = check(off, 8);
        long v = 0;
        for (int i = 7; i >= 0; i--) {
            v = (v << 8) | (data[o + i] & 0xffL);
        }
        return v;
    }

    public int check(long off, long size) throws ElfException {
        if (off < 0 || size < 0 || off > data.length || off + size > data.length) {
            throw new ElfException("读取越界: offset=" + off + " size=" + size
                    + " 文件长度=" + data.length);
        }
        if (off > Integer.MAX_VALUE) {
            throw new ElfException("偏移超出实现范围: " + off);
        }
        return (int) off;
    }

    /** 在 [start, start+size) 内读取以 NUL 结尾的字符串，边界自动截断。 */
    public String cstr(long start, long size) throws ElfException {
        int o = check(start, 0);
        long end = start + size;
        if (end < start || end > data.length) {
            end = data.length;
        }
        int p = o;
        while (p < end && data[p] != 0) {
            p++;
        }
        return new String(data, o, p - o, StandardCharsets.UTF_8);
    }

    public byte[] slice(long off, long size) throws ElfException {
        int o = check(off, size);
        if (size > Integer.MAX_VALUE - 8) {
            throw new ElfException("切片过大: " + size);
        }
        byte[] out = new byte[(int) size];
        System.arraycopy(data, o, out, 0, (int) size);
        return out;
    }
}
