package com.symcourt.elf;

/** Bounds-checked little/big-endian cursor over a byte array. */
public final class ByteReader {
    private final byte[] data;
    private final boolean le;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.le = littleEndian;
    }

    public int size() {
        return data.length;
    }

    public boolean littleEndian() {
        return le;
    }

    private void check(long off, long len) {
        if (off < 0 || len < 0 || off > data.length || len > data.length - off) {
            throw new ElfException("corrupt: out-of-bounds read off=" + off + " len=" + len
                    + " size=" + data.length);
        }
    }

    public int u1(long off) {
        check(off, 1);
        return data[(int) off] & 0xff;
    }

    public int u2(long off) {
        check(off, 2);
        int i = (int) off;
        if (le) {
            return (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8);
        }
        return ((data[i] & 0xff) << 8) | (data[i + 1] & 0xff);
    }

    /** unsigned 32-bit returned as long */
    public long u4(long off) {
        check(off, 4);
        int i = (int) off;
        long v;
        if (le) {
            v = (data[i] & 0xffL) | ((data[i + 1] & 0xffL) << 8)
                    | ((data[i + 2] & 0xffL) << 16) | ((data[i + 3] & 0xffL) << 24);
        } else {
            v = ((data[i] & 0xffL) << 24) | ((data[i + 1] & 0xffL) << 16)
                    | ((data[i + 2] & 0xffL) << 8) | (data[i + 3] & 0xffL);
        }
        return v & 0xffffffffL;
    }

    public long u8(long off) {
        check(off, 8);
        if (le) {
            return u4(off) | (u4(off + 4) << 32);
        }
        return (u4(off) << 32) | u4(off + 4);
    }

    public byte[] bytes(long off, long len) {
        check(off, len);
        byte[] out = new byte[(int) len];
        System.arraycopy(data, (int) off, out, 0, (int) len);
        return out;
    }

    /** bounded NUL-terminated string inside [tableOff, tableOff+tableLen) */
    public String cstr(long tableOff, long tableLen, long relOff) {
        if (relOff < 0 || relOff >= tableLen) {
            throw new ElfException("corrupt: string offset " + relOff
                    + " outside string table of size " + tableLen);
        }
        long start = tableOff + relOff;
        long end = start;
        long limit = tableOff + tableLen;
        while (end < limit && data[(int) end] != 0) {
            end++;
        }
        return new String(data, (int) start, (int) (end - start), java.nio.charset.StandardCharsets.UTF_8);
    }
}
