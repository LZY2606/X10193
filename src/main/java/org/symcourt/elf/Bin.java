package org.symcourt.elf;

import java.nio.charset.StandardCharsets;

/**
 * Bounds-checked, endian-aware reader over an immutable byte buffer.
 * Every access validates length and alignment-independent offsets so that a
 * malformed object raises {@link ElfException} instead of corrupting state.
 */
public final class Bin {
    private final byte[] data;
    private final boolean le;

    public Bin(byte[] data, boolean littleEndian) {
        this.data = data;
        this.le = littleEndian;
    }

    public int length() {
        return data.length;
    }

    public byte[] bytes() {
        return data;
    }

    public void require(int off, int len) {
        if (off < 0 || len < 0 || (long) off + (long) len > data.length) {
            throw new ElfException("读取越界: offset=" + off + " length=" + len
                    + " size=" + data.length);
        }
    }

    public byte u8(int off) {
        require(off, 1);
        return data[off];
    }

    public int u16(int off) {
        require(off, 2);
        if (le) {
            return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
        }
        return ((data[off] & 0xff) << 8) | (data[off + 1] & 0xff);
    }

    public long u32(int off) {
        require(off, 4);
        long v;
        if (le) {
            v = (data[off] & 0xffL)
                    | ((data[off + 1] & 0xffL) << 8)
                    | ((data[off + 2] & 0xffL) << 16)
                    | ((data[off + 3] & 0xffL) << 24);
        } else {
            v = ((data[off] & 0xffL) << 24)
                    | ((data[off + 1] & 0xffL) << 16)
                    | ((data[off + 2] & 0xffL) << 8)
                    | (data[off + 3] & 0xffL);
        }
        return v;
    }

    public long u64(int off) {
        require(off, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int idx = le ? off + i : off + (7 - i);
            v |= (data[idx] & 0xffL) << (8 * i);
        }
        return v;
    }

    public byte[] slice(int off, int len) {
        require(off, len);
        byte[] out = new byte[len];
        System.arraycopy(data, off, out, 0, len);
        return out;
    }

    /** Reads a NUL terminated string inside a string table. */
    public String cstr(int off, int tableLen) {
        if (off < 0 || off >= tableLen || off >= data.length) {
            throw new ElfException("字符串索引越界: " + off);
        }
        int end = off;
        int limit = Math.min(tableLen, data.length);
        while (end < limit && data[end] != 0) {
            end++;
        }
        return new String(data, off, end - off, StandardCharsets.UTF_8);
    }

    public boolean startsWith(int off, byte[] prefix) {
        if ((long) off + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[off + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
