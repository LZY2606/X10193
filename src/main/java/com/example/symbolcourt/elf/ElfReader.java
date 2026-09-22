package com.example.symbolcourt.elf;

/** Bounds-checked little/big endian reader over a byte array. */
final class ElfReader {
    private final byte[] d;
    private final boolean le;

    ElfReader(byte[] d, boolean le) {
        this.d = d;
        this.le = le;
    }

    private void check(long off, int n) {
        if (off < 0 || n < 0 || off + n > d.length) {
            throw new ElfParseException("read out of bounds: offset=" + off + " len=" + n + " size=" + d.length);
        }
    }

    long limit() {
        return d.length;
    }

    int u8(long off) {
        check(off, 1);
        return d[(int) off] & 0xff;
    }

    int u16(long off) {
        check(off, 2);
        int a = (int) off;
        if (le) return (d[a] & 0xff) | ((d[a + 1] & 0xff) << 8);
        return ((d[a] & 0xff) << 8) | (d[a + 1] & 0xff);
    }

    long u32(long off) {
        check(off, 4);
        int a = (int) off;
        long v;
        if (le) {
            v = ((long) (d[a] & 0xff)) | ((long) (d[a + 1] & 0xff) << 8)
                    | ((long) (d[a + 2] & 0xff) << 16) | ((long) (d[a + 3] & 0xff) << 24);
        } else {
            v = ((long) (d[a] & 0xff) << 24) | ((long) (d[a + 1] & 0xff) << 16)
                    | ((long) (d[a + 2] & 0xff) << 8) | ((long) (d[a + 3] & 0xff));
        }
        return v & 0xffffffffL;
    }

    long u64(long off) {
        long lo = u32(off);
        long hi = u32(off + 4);
        return le ? (hi << 32) | lo : (lo << 32) | hi;
    }

    /** Bounded C-string read inside [limit). */
    String cstr(long off, long limit) {
        if (off < 0 || off >= limit || limit > d.length) {
            throw new ElfParseException("string out of bounds: offset=" + off + " limit=" + limit);
        }
        StringBuilder sb = new StringBuilder();
        long p = off;
        while (p < limit) {
            int c = u8(p);
            if (c == 0) break;
            sb.append((char) c);
            p++;
        }
        return sb.toString();
    }
}
