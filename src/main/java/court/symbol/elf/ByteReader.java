package court.symbol.elf;

/**
 * Bounds-checked little/big endian reader over an in-memory byte array.
 * Every access validates offset and length before touching the buffer so a
 * truncated or malicious object can never escape with an ArrayIndexOutOfBounds.
 */
public final class ByteReader {

    private final byte[] data;
    private final boolean littleEndian;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    public int size() {
        return data.length;
    }

    public byte[] raw() {
        return data;
    }

    private void need(long offset, int length) {
        if (offset < 0 || length < 0 || offset > (long) data.length - length) {
            throw new ElfException("读取越界: offset=" + offset + " length=" + length
                    + " fileSize=" + data.length);
        }
    }

    public int u8(long offset) {
        need(offset, 1);
        return data[(int) offset] & 0xff;
    }

    public int u16(long offset) {
        need(offset, 2);
        int a = data[(int) offset] & 0xff;
        int b = data[(int) offset + 1] & 0xff;
        return littleEndian ? (a | (b << 8)) : ((a << 8) | b);
    }

    public long u32(long offset) {
        need(offset, 4);
        long v = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (data[(int) offset + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (data[(int) offset + i] & 0xffL);
            }
        }
        return v;
    }

    public long u64(long offset) {
        need(offset, 8);
        long v = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                v = (v << 8) | (data[(int) offset + i] & 0xffL);
            }
        } else {
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[(int) offset + i] & 0xffL);
            }
        }
        return v;
    }

    public long signed(long offset, int bytes) {
        return switch (bytes) {
            case 1 -> u8(offset);
            case 2 -> (short) u16(offset);
            case 4 -> (int) u32(offset);
            case 8 -> u64(offset);
            default -> throw new ElfException("不支持的整数宽度: " + bytes);
        };
    }

    public byte[] slice(long offset, int length) {
        need(offset, length);
        byte[] out = new byte[length];
        System.arraycopy(data, (int) offset, out, 0, length);
        return out;
    }

    /** Reads a NUL terminated string, bounded by [offset, limit). */
    public String cstr(long offset, long limit) {
        if (offset < 0 || offset > limit || limit > data.length) {
            throw new ElfException("字符串表偏移越界: " + offset);
        }
        int start = (int) offset;
        int end = start;
        while (end < limit && data[end] != 0) {
            end++;
        }
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }
}
