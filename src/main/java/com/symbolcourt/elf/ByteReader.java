package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/**
 * Bounds-checked reader over a byte array. Every access verifies
 * offset/length before touching the buffer, so a malformed file can
 * never walk out of the slice.
 */
public final class ByteReader {

    private final byte[] buf;
    private final boolean littleEndian;

    public ByteReader(byte[] buf, boolean littleEndian) {
        this.buf = buf;
        this.littleEndian = littleEndian;
    }

    public int size() {
        return buf.length;
    }

    public boolean littleEndian() {
        return littleEndian;
    }

    public void require(long off, long len) {
        if (off < 0 || len < 0 || off > buf.length || len > (long) buf.length - off) {
            throw new ElfParseException(
                    "read out of bounds: offset=" + off + " length=" + len + " fileSize=" + buf.length);
        }
    }

    public byte[] slice(long off, int len) {
        require(off, len);
        byte[] out = new byte[len];
        System.arraycopy(buf, (int) off, out, 0, len);
        return out;
    }

    public int u8(long off) {
        require(off, 1);
        return buf[(int) off] & 0xff;
    }

    public int u16(long off) {
        require(off, 2);
        int a = buf[(int) off] & 0xff;
        int b = buf[(int) off + 1] & 0xff;
        return littleEndian ? (a | (b << 8)) : ((a << 8) | b);
    }

    public long u32(long off) {
        require(off, 4);
        long v = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (buf[(int) off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (buf[(int) off + i] & 0xffL);
            }
        }
        return v & 0xffffffffL;
    }

    public long u64(long off) {
        require(off, 8);
        long v = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (buf[(int) off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (buf[(int) off + i] & 0xffL);
            }
        }
        return v;
    }

    /** NUL-terminated string starting at {@code off}; bounded by buffer end. */
    public String cstr(long off) {
        if (off < 0 || off >= buf.length) {
            throw new ElfParseException("string offset out of bounds: " + off);
        }
        int start = (int) off;
        int end = start;
        while (end < buf.length && buf[end] != 0) {
            end++;
        }
        return new String(buf, start, end - start, StandardCharsets.UTF_8);
    }

    public boolean startsWith(long off, byte[] magic) {
        if (off < 0 || off + magic.length > buf.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (buf[(int) off + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
