package com.symcourt.elf;

/** Bounds-checked little/big endian byte reader. Every access validates ranges. */
final class Reader {
    final byte[] data;
    boolean little = true;

    Reader(byte[] data) {
        this.data = data;
    }

    void check(long off, long len, String what) {
        if (off < 0 || len < 0 || off > data.length || off + len > data.length) {
            throw new ElfParseException(what + " 越界: off=" + off + " len=" + len
                    + " size=" + data.length);
        }
    }

    int u1(long off) {
        check(off, 1, "u1");
        return data[(int) off] & 0xff;
    }

    int u2(long off) {
        check(off, 2, "u2");
        int o = (int) off;
        if (little) return (data[o] & 0xff) | ((data[o + 1] & 0xff) << 8);
        return ((data[o] & 0xff) << 8) | (data[o + 1] & 0xff);
    }

    long u4(long off) {
        check(off, 4, "u4");
        int o = (int) off;
        if (little) {
            return ((long) (data[o] & 0xff)) | ((long) (data[o + 1] & 0xff) << 8)
                    | ((long) (data[o + 2] & 0xff) << 16) | ((long) (data[o + 3] & 0xff) << 24);
        }
        return ((long) (data[o] & 0xff) << 24) | ((long) (data[o + 1] & 0xff) << 16)
                | ((long) (data[o + 2] & 0xff) << 8) | ((long) (data[o + 3] & 0xff));
    }

    long u8(long off) {
        check(off, 8, "u8");
        if (little) {
            long v = 0;
            for (int i = 7; i >= 0; i--) v = (v << 8) | (data[(int) off + i] & 0xffL);
            return v;
        }
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (data[(int) off + i] & 0xffL);
        return v;
    }

    /** Read a NUL-terminated string inside [tableOff, tableOff+tableSize). */
    String str(long tableOff, long tableSize, long strOff, String what) {
        if (strOff < 0 || strOff >= tableSize) {
            if (strOff == 0) return "";
            throw new ElfParseException(what + " 字符串偏移越界: " + strOff + " 表大小=" + tableSize);
        }
        long start = tableOff + strOff;
        check(start, 1, what);
        long end = start;
        long limit = tableOff + tableSize;
        while (end < limit && data[(int) end] != 0) end++;
        if (end >= limit) throw new ElfParseException(what + " 字符串未终止: off=" + strOff);
        return new String(data, (int) start, (int) (end - start), java.nio.charset.StandardCharsets.UTF_8);
    }
}
