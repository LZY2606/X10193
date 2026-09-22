package com.symcourt.elf;

/**
 * Bounds-checked random access over one object's bytes. Every read validates
 * length and index boundaries; a corrupt object throws ElfException instead of
 * leaking an IndexOutOfBounds / NegativeArraySizeException.
 */
final class ElfInput {
    final byte[] data;
    final boolean littleEndian;
    final boolean elf64;

    ElfInput(byte[] data, boolean littleEndian, boolean elf64) {
        this.data = data;
        this.littleEndian = littleEndian;
        this.elf64 = elf64;
    }

    int u8(long off) throws ElfException {
        check(off, 1);
        return data[(int) off] & 0xff;
    }

    int u32i(long off) throws ElfException {
        long v = u32(off);
        if (v > 0xffffffffL) {
            throw new ElfException("u32 overflow");
        }
        return (int) v;
    }

    int u16(long off) throws ElfException {
        check(off, 2);
        int o = (int) off;
        int a = data[o] & 0xff;
        int b = data[o + 1] & 0xff;
        return littleEndian ? (a | (b << 8)) : ((a << 8) | b);
    }

    long u32(long off) throws ElfException {
        check(off, 4);
        int o = (int) off;
        long v = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (data[o + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (data[o + i] & 0xffL);
            }
        }
        return v;
    }

    long u64(long off) throws ElfException {
        check(off, 8);
        int o = (int) off;
        long v = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[o + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[o + i] & 0xffL);
            }
        }
        return v;
    }

    /** Native-width "word": 8 bytes on ELF64, 4 bytes (zero-extended) on ELF32. */
    long word(long off) throws ElfException {
        return elf64 ? u64(off) : u32(off);
    }

    int wordBytes() {
        return elf64 ? 8 : 4;
    }

    byte[] slice(long off, long len) throws ElfException {
        check(off, len);
        byte[] out = new byte[(int) len];
        System.arraycopy(data, (int) off, out, 0, (int) len);
        return out;
    }

    void check(long off, long len) throws ElfException {
        if (off < 0 || len < 0 || off > data.length || len > (long) data.length - off) {
            throw new ElfException(
                    "read out of bounds: offset=" + off + " length=" + len + " fileSize=" + data.length);
        }
    }

    String cstring(long off) throws ElfException {
        if (off < 0 || off >= data.length) {
            throw new ElfException("string offset out of bounds: " + off);
        }
        int start = (int) off;
        int end = start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end >= data.length) {
            throw new ElfException("unterminated string at offset " + off);
        }
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Read a string from a dedicated string table section's own region. */
    String tableString(long tableOffset, long tableSize, long index) throws ElfException {
        if (index < 0 || index >= tableSize) {
            throw new ElfException("string index " + index + " outside section of size " + tableSize);
        }
        return cstring(tableOffset + index);
    }
}
