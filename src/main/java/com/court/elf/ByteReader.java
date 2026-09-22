package com.court.elf;

/**
 * Bounds-checked random reader over a little- or big-endian byte image.
 * Every access verifies the [offset, offset+len) range before touching memory,
 * so a corrupt object can never read beyond its own bytes.
 */
public class ByteReader {
    private final byte[] data;
    private final boolean bigEndian;

    public ByteReader(byte[] data, boolean bigEndian) {
        this.data = data;
        this.bigEndian = bigEndian;
    }

    public int length() { return data.length; }

    public void require(long off, long len) throws ElfParseException {
        if (off < 0 || len < 0 || off > data.length - len) {
            throw new ElfParseException(
                    "out of bounds: offset=" + off + " length=" + len + " fileSize=" + data.length);
        }
    }

    public byte[] bytes(long off, long len) throws ElfParseException {
        require(off, len);
        byte[] out = new byte[(int) len];
        System.arraycopy(data, (int) off, out, 0, (int) len);
        return out;
    }

    public int u8(long off) throws ElfParseException {
        require(off, 1);
        return data[(int) off] & 0xff;
    }

    public int u16(long off) throws ElfParseException {
        require(off, 2);
        int a = data[(int) off] & 0xff;
        int b = data[(int) off + 1] & 0xff;
        return bigEndian ? ((a << 8) | b) : ((b << 8) | a);
    }

    public long u32(long off) throws ElfParseException {
        require(off, 4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = data[(int) off + (bigEndian ? i : 3 - i)] & 0xff;
            v = (v << 8) | b;
        }
        return v & 0xffffffffL;
    }

    public long u64(long off) throws ElfParseException {
        require(off, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int b = data[(int) off + (bigEndian ? i : 7 - i)] & 0xff;
            v = (v << 8) | b;
        }
        return v;
    }

    public String cstring(long off) throws ElfParseException {
        if (off < 0 || off >= data.length) {
            throw new ElfParseException("string offset out of bounds: " + off);
        }
        int end = (int) off;
        while (end < data.length && data[end] != 0) end++;
        if (end >= data.length) {
            throw new ElfParseException("unterminated string at offset " + off);
        }
        return new String(data, (int) off, end - (int) off, java.nio.charset.StandardCharsets.UTF_8);
    }
}
