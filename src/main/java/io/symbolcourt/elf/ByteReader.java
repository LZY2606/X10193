package io.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/**
 * Little / big endian random access reader over an ELF image with strict
 * length and index bounds checking. Every access is validated so a crafted or
 * truncated fixture can only throw {@link ElfException}, never run off the end
 * of the buffer.
 */
public final class ByteReader {
    private final byte[] data;
    private final boolean littleEndian;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    public int length() {
        return data.length;
    }

    public void require(int off, int n) {
        if (off < 0 || n < 0 || (long) off + n > data.length) {
            throw new ElfException("read out of bounds: offset=" + off + " size=" + n
                    + " length=" + data.length);
        }
    }

    public byte u8(int off) {
        require(off, 1);
        return data[off];
    }

    public int u16(int off) {
        require(off, 2);
        if (littleEndian) {
            return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
        }
        return ((data[off] & 0xff) << 8) | (data[off + 1] & 0xff);
    }

    public long u32(int off) {
        require(off, 4);
        long v = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (data[off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (data[off + i] & 0xffL);
            }
        }
        return v;
    }

    public long u64(int off) {
        require(off, 8);
        long v = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[off + i] & 0xffL);
            }
        }
        return v;
    }

    /**
     * Reads a NUL terminated string from a string table section starting at
     * {@code start}. Validates that the table region itself is inside the
     * image and that a terminator exists.
     */
    public String strtabString(int sectionStart, int sectionSize, int strIndex) {
        if (strIndex < 0 || sectionSize < 0 || strIndex >= sectionSize) {
            throw new ElfException("string index out of bounds: index=" + strIndex
                    + " tableSize=" + sectionSize);
        }
        int start = sectionStart + strIndex;
        require(start, 1);
        int end = start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end >= data.length) {
            throw new ElfException("unterminated string at offset " + start);
        }
        if (end - start > sectionSize - strIndex) {
            throw new ElfException("string runs past end of its section at offset " + start);
        }
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    public byte[] slice(int off, int size) {
        require(off, size);
        byte[] out = new byte[size];
        System.arraycopy(data, off, out, 0, size);
        return out;
    }
}
