package com.symbolcourt.elf;

/** Bounds checked, endian aware reader over a fixed byte array. */
public class BinaryReader {
    public final byte[] buf;
    public boolean little = true;

    public BinaryReader(byte[] buf) { this.buf = buf; }

    public int length() { return buf.length; }

    public boolean inRange(long off, long len) {
        return off >= 0 && len >= 0 && off <= buf.length && len <= (long) buf.length - off;
    }

    private int u(long off) { return ((int) buf[(int) off]) & 0xff; }

    public int u8(long off) {
        if (!inRange(off, 1)) throw new ElfReadException("u8 out of range at " + off);
        return u(off);
    }

    public int u16(long off) {
        if (!inRange(off, 2)) throw new ElfReadException("u16 out of range at " + off);
        int a = u(off), b = u(off + 1);
        return little ? (a | (b << 8)) : ((a << 8) | b);
    }

    public long u32(long off) {
        if (!inRange(off, 4)) throw new ElfReadException("u32 out of range at " + off);
        int a = u(off), b = u(off + 1), c = u(off + 2), d = u(off + 3);
        long v = little
                ? ((long) a | ((long) b << 8) | ((long) c << 16) | ((long) d << 24))
                : (((long) a << 24) | ((long) b << 16) | ((long) c << 8) | (long) d);
        return v & 0xffffffffL;
    }

    public long u64(long off) {
        if (!inRange(off, 8)) throw new ElfReadException("u64 out of range at " + off);
        long v = 0;
        if (little) {
            for (int i = 7; i >= 0; i--) v = (v << 8) | u(off + i);
        } else {
            for (int i = 0; i < 8; i++) v = (v << 8) | u(off + i);
        }
        return v;
    }

    public String cstr(long strOff, long tableStart, long tableSize) {
        if (!inRange(tableStart, tableSize) || strOff < 0 || strOff >= tableSize) return "";
        long start = tableStart + strOff;
        long end = start;
        long max = tableStart + tableSize;
        while (end < max && u(end) != 0) end++;
        if (end <= start) return "";
        return new String(buf, (int) start, (int) (end - start), java.nio.charset.StandardCharsets.UTF_8);
    }
}
