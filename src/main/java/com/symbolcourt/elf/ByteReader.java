package com.symbolcourt.elf;

public class ByteReader {
    private final byte[] data;
    private final boolean bigEndian;
    private int pos;

    public ByteReader(byte[] data, boolean bigEndian, int pos) {
        this.data = data;
        this.bigEndian = bigEndian;
        this.pos = pos;
    }

    public int pos() {
        return pos;
    }

    public void seek(int p) {
        if (p < 0 || p > data.length) {
            throw new ElfParseException("offset out of bounds: " + p);
        }
        pos = p;
    }

    public int remaining() {
        return data.length - pos;
    }

    private void need(int n) {
        if (pos < 0 || n < 0 || (long) pos + n > data.length) {
            throw new ElfParseException(
                    "unexpected end of data at offset " + pos + ", need " + n + " bytes, have " + data.length);
        }
    }

    public byte u8() {
        need(1);
        return data[pos++];
    }

    public int u16() {
        need(2);
        int b0 = data[pos++] & 0xff;
        int b1 = data[pos++] & 0xff;
        return bigEndian ? (b0 << 8) | b1 : (b1 << 8) | b0;
    }

    public long u32() {
        need(4);
        long v = 0;
        if (bigEndian) {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (data[pos++] & 0xff);
            }
        } else {
            for (int i = 3; i >= 0; i--) {
                v |= (long) (data[pos++] & 0xff) << (8 * i);
            }
        }
        return v;
    }

    public long u64() {
        need(8);
        long v = 0;
        if (bigEndian) {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[pos++] & 0xff);
            }
        } else {
            for (int i = 7; i >= 0; i--) {
                v |= (long) (data[pos++] & 0xff) << (8 * i);
            }
        }
        return v;
    }

    public byte[] bytes(int n) {
        need(n);
        byte[] out = new byte[n];
        System.arraycopy(data, pos, out, 0, n);
        pos += n;
        return out;
    }

    public byte[] sliceAt(long offset, long size) {
        if (offset < 0 || size < 0 || offset > data.length || offset + size > data.length) {
            throw new ElfParseException(
                    "slice out of bounds: offset=" + offset + " size=" + size + " length=" + data.length);
        }
        byte[] out = new byte[(int) size];
        System.arraycopy(data, (int) offset, out, 0, (int) size);
        return out;
    }

    public boolean inRange(long offset, long size) {
        return offset >= 0 && size >= 0 && offset <= data.length && offset + size <= data.length;
    }

    public String cString(long offset) {
        if (offset < 0 || offset >= data.length) {
            throw new ElfParseException("string offset out of bounds: " + offset);
        }
        int start = (int) offset;
        int end = start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end >= data.length) {
            throw new ElfParseException("unterminated string at offset " + offset);
        }
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }

    public int length() {
        return data.length;
    }
}
