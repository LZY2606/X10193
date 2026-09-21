package court.elf;

/**
 * 定长数组上的边界检查读取器。所有 get 都先校验偏移与长度，任何越界都抛 ElfException，
 * 保证畸形输入永远不会读到数组之外。
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

    public void require(long offset, long len) {
        if (offset < 0 || len < 0 || offset > data.length || len > data.length - offset) {
            throw new ElfException("读取越界: offset=" + offset + " len=" + len + " total=" + data.length);
        }
    }

    public int u8(long off) {
        require(off, 1);
        return data[(int) off] & 0xff;
    }

    public int u16(long off) {
        require(off, 2);
        int a = data[(int) off] & 0xff;
        int b = data[(int) off + 1] & 0xff;
        return littleEndian ? (a | (b << 8)) : ((a << 8) | b);
    }

    public long u32(long off) {
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

    public long u64(long off) {
        long hi = u32(off + (littleEndian ? 4 : 0));
        long lo = u32(off + (littleEndian ? 0 : 4));
        return (hi << 32) | lo;
    }

    public byte[] slice(long off, long len) {
        require(off, len);
        byte[] out = new byte[(int) len];
        System.arraycopy(data, (int) off, out, 0, (int) len);
        return out;
    }

    /** NUL 结尾字符串；max 为硬边界，越界或找不到 NUL 都报错。 */
    public String cstr(long off, long max) {
        if (max < 0) {
            throw new ElfException("非法字符串长度");
        }
        require(off, 0);
        long end = off;
        long limit = Math.min(off + max, data.length);
        while (end < limit && data[(int) end] != 0) {
            end++;
        }
        if (end >= limit) {
            throw new ElfException("字符串缺少 NUL 结尾: offset=" + off);
        }
        require(off, end - off);
        return new String(data, (int) off, (int) (end - off), java.nio.charset.StandardCharsets.UTF_8);
    }
}
