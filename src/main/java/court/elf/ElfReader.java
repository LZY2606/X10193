package court.elf;

import java.nio.charset.StandardCharsets;

/** Bounds-checked random reader over an ELF byte image. */
final class ElfReader {
    private final byte[] data;
    private final boolean littleEndian;

    ElfReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    int u8(long off) {
        require(off, 1);
        return data[(int) off] & 0xff;
    }

    int u16(long off) {
        require(off, 2);
        int a = data[(int) off] & 0xff;
        int b = data[(int) off + 1] & 0xff;
        return littleEndian ? (a | (b << 8)) : ((a << 8) | b);
    }

    long u32(long off) {
        require(off, 4);
        long v = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (data[(int) off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (data[(int) off + i] & 0xffL);
            }
        }
        return v & 0xffffffffL;
    }

    long u64(long off) {
        require(off, 8);
        long v = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[(int) off + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[(int) off + i] & 0xffL);
            }
        }
        return v;
    }

    byte[] bytes(long off, long len) {
        require(off, len);
        byte[] out = new byte[(int) len];
        System.arraycopy(data, (int) off, out, 0, (int) len);
        return out;
    }

    String cstr(long off) {
        if (off < 0 || off >= data.length) {
            throw new ElfFormatException("string offset out of range: " + off);
        }
        int end = (int) off;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end >= data.length) {
            throw new ElfFormatException("unterminated string at offset " + off);
        }
        return new String(data, (int) off, end - (int) off, StandardCharsets.UTF_8);
    }

    String strtabString(long sectionStart, long sectionSize, long offset) {
        if (offset < 0 || offset >= sectionSize) {
            throw new ElfFormatException(
                    "strtab index out of range: offset=" + offset + " size=" + sectionSize);
        }
        return cstr(sectionStart + offset);
    }

    private void require(long off, long len) {
        if (off < 0 || len < 0 || off > data.length || off + len > data.length) {
            throw new ElfFormatException(
                    "read out of bounds: off=" + off + " len=" + len + " file=" + data.length);
        }
    }
}
