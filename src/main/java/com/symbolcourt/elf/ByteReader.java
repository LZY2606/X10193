package com.symbolcourt.elf;

/**
 * Bounded random-access view over an ELF image. Every access is bounds checked
 * against the underlying byte array so a truncated/corrupt object can never
 * trigger an out-of-bounds read.
 */
public final class ByteReader {

    private final byte[] data;

    public ByteReader(byte[] data) {
        this.data = data;
    }

    public int length() {
        return data.length;
    }

    public void require(int offset, int count) throws ElfFormatException {
        if (offset < 0 || count < 0 || offset > data.length - count) {
            throw new ElfFormatException("read of " + count + " bytes at offset " + offset
                    + " exceeds image length " + data.length);
        }
    }

    public int u8(int offset) throws ElfFormatException {
        require(offset, 1);
        return data[offset] & 0xff;
    }

    public int u16(boolean elf64, int offset) throws ElfFormatException {
        require(offset, 2);
        int b0 = data[offset] & 0xff;
        int b1 = data[offset + 1] & 0xff;
        if (elf64) {
            return (b1 << 8) | b0;
        }
        // little endian; big endian would be encoded in EI_DATA, fixtures use LE
        return (b1 << 8) | b0;
    }

    public long u32(boolean elf64, int offset) throws ElfFormatException {
        require(offset, 4);
        long v = 0;
        for (int i = 3; i >= 0; i--) {
            v = (v << 8) | (data[offset + i] & 0xffL);
        }
        return v & 0xffffffffL;
    }

    public long u64(boolean elf64, int offset) throws ElfFormatException {
        if (elf64) {
            require(offset, 8);
            long v = 0;
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[offset + i] & 0xffL);
            }
            return v;
        }
        return u32(elf64, offset);
    }

    /** Unsigned little-endian word of the native word size (4 or 8 bytes). */
    public long word(boolean elf64, int offset) throws ElfFormatException {
        return elf64 ? u64(true, offset) : u32(false, offset);
    }

    public byte[] bytes(int offset, int count) throws ElfFormatException {
        require(offset, count);
        byte[] out = new byte[count];
        System.arraycopy(data, offset, out, 0, count);
        return out;
    }

    /** Reads a NUL-terminated string from a section's data range. */
    public String cString(int sectionOffset, int sectionSize, int strIndex) throws ElfFormatException {
        if (strIndex < 0 || strIndex >= sectionSize) {
            return "";
        }
        int start = sectionOffset + strIndex;
        require(start, 1);
        int end = start;
        int max = Math.min(data.length, sectionOffset + sectionSize);
        while (end < max && data[end] != 0) {
            end++;
        }
        if (end >= max && (end >= data.length || data[end] != 0)) {
            // no terminator: take what exists, still bounded
            end = max;
        }
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }
}
