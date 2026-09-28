package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/** Little/big-endian bounded cursor over a raw byte buffer. */
final class ElfReader {
    final byte[] data;
    final boolean le;
    int pos;

    ElfReader(byte[] data, boolean le) { this.data = data; this.le = le; }

    void seek(int p) throws ElfParseException {
        if (p < 0 || p > data.length) throw new ElfParseException("offset out of range: " + p);
        this.pos = p;
    }

    void require(int n) throws ElfParseException {
        if (n < 0 || pos > data.length - n) {
            throw new ElfParseException("truncated data at offset " + pos + " need " + n + " len " + data.length);
        }
    }

    int u1() throws ElfParseException { require(1); return data[pos++] & 0xff; }
    long u2() throws ElfParseException { require(2); int a = data[pos++] & 0xff, b = data[pos++] & 0xff; return le ? (a | (b << 8)) : (b | (a << 8)); }
    long u4() throws ElfParseException {
        require(4);
        long v = 0;
        if (le) for (int i = 0; i < 4; i++) v |= (data[pos++] & 0xffL) << (8 * i);
        else for (int i = 3; i >= 0; i--) v = (v << 8) | (data[pos++] & 0xff);
        return v & 0xffffffffL;
    }
    long u8() throws ElfParseException {
        require(8);
        long v = 0;
        if (le) for (int i = 0; i < 8; i++) v |= (data[pos++] & 0xffL) << (8 * i);
        else for (int i = 7; i >= 0; i--) v = (v << 8) | (data[pos++] & 0xff);
        return v;
    }

    String cstr(long offset) throws ElfParseException {
        int o = (int) offset;
        if (o < 0 || o >= data.length) throw new ElfParseException("string offset out of range: " + o);
        int end = o;
        while (end < data.length && data[end] != 0) end++;
        if (end >= data.length) throw new ElfParseException("unterminated string at " + o);
        return new String(data, o, end - o, StandardCharsets.UTF_8);
    }
}
