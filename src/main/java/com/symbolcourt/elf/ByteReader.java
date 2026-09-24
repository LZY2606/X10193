package com.symbolcourt.elf;

import com.symbolcourt.model.Enums.Endian;

import java.nio.charset.StandardCharsets;

/** Bounds-checked view over an ELF byte buffer. Every read validates length/index edges. */
public class ByteReader {

    public static final long MAX_SANE = 1L << 40;

    public final byte[] data;
    public final boolean bigEndian;

    public ByteReader(byte[] data, Endian endian) {
        this.data = data;
        this.bigEndian = endian == Endian.BIG;
    }

    private void require(long off, int len) {
        if (off < 0 || len < 0 || off > data.length - len || off > MAX_SANE) {
            throw new ElfFormatException(
                    "read out of bounds: offset=" + off + " length=" + len + " fileSize=" + data.length);
        }
    }

    public int u8(long off) {
        require(off, 1);
        return data[(int) off] & 0xff;
    }

    public int u16(long off) {
        require(off, 2);
        int a = data[(int) off] & 0xff;
        int b = data[(int) (off + 1)] & 0xff;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    public long u32(long off) {
        require(off, 4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = data[(int) off + (bigEndian ? i : 3 - i)] & 0xff;
            v = (v << 8) | b;
        }
        return v & 0xffffffffL;
    }

    public long u64(long off) {
        require(off, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int b = data[(int) off + (bigEndian ? i : 7 - i)] & 0xff;
            v = (v << 8) | b;
        }
        return v;
    }

    public byte[] slice(long off, long len) {
        if (len > Integer.MAX_VALUE - 8L) {
            throw new ElfFormatException("slice length exceeds capacity: " + len);
        }
        require(off, (int) len);
        byte[] out = new byte[(int) len];
        System.arraycopy(data, (int) off, out, 0, (int) len);
        return out;
    }

    public boolean inRange(long off, int len) {
        return off >= 0 && len >= 0 && off <= data.length - len && off <= MAX_SANE;
    }

    public String cstring(long off, int tableSize) {
        if (tableSize < 0 || off < 0 || off > tableSize) {
            throw new ElfFormatException("string index out of bounds: " + off + " table=" + tableSize);
        }
        int start = (int) off;
        int end = start;
        int limit = Math.min(data.length, tableSize);
        while (end < limit && data[end] != 0) end++;
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    public String cstringInSection(long off, long tableStart, long tableSize) {
        if (off < 0 || off > tableSize) {
            throw new ElfFormatException("string index out of section: " + off);
        }
        long abs = tableStart + off;
        return cstring(abs, (int) Math.min(tableStart + tableSize, data.length));
    }
}
