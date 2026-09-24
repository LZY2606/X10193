package court.elf;

/** Bounds-checked random access reader over a byte array, supporting 32/64-bit and both endians. */
public final class R {
    public final byte[] d;
    public final boolean le;
    public final boolean elf64;

    public R(byte[] data, boolean littleEndian, boolean elf64) {
        this.d = data;
        this.le = littleEndian;
        this.elf64 = elf64;
    }

    public void need(int off, int n) throws ElfException {
        if (off < 0 || n < 0 || (long) off + n > d.length) {
            throw new ElfException("read out of bounds: offset=" + off + " length=" + n
                    + " fileSize=" + d.length);
        }
    }

    public int u8(int off) throws ElfException {
        need(off, 1);
        return d[off] & 0xff;
    }

    public int u16(int off) throws ElfException {
        need(off, 2);
        if (le) {
            return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8);
        }
        return ((d[off] & 0xff) << 8) | (d[off + 1] & 0xff);
    }

    public long u32(int off) throws ElfException {
        need(off, 4);
        long v;
        if (le) {
            v = (d[off] & 0xffL) | ((d[off + 1] & 0xffL) << 8)
                    | ((d[off + 2] & 0xffL) << 16) | ((d[off + 3] & 0xffL) << 24);
        } else {
            v = ((d[off] & 0xffL) << 24) | ((d[off + 1] & 0xffL) << 16)
                    | ((d[off + 2] & 0xffL) << 8) | (d[off + 3] & 0xffL);
        }
        return v & 0xffffffffL;
    }

    public long u64(int off) throws ElfException {
        need(off, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int b = d[off + (le ? i : 7 - i)] & 0xff;
            v |= (long) b << (8 * i);
        }
        return v;
    }

    public long word(int off) throws ElfException {
        return elf64 ? u64(off) : u32(off);
    }

    public int wordSize() {
        return elf64 ? 8 : 4;
    }

    public byte[] slice(int off, int len) throws ElfException {
        need(off, len);
        byte[] out = new byte[len];
        System.arraycopy(d, off, out, 0, len);
        return out;
    }

    public String cstr(int off) throws ElfException {
        if (off < 0 || off >= d.length) {
            throw new ElfException("string offset out of bounds: " + off);
        }
        int end = off;
        while (end < d.length && d[end] != 0) {
            end++;
        }
        return new String(d, off, end - off, java.nio.charset.StandardCharsets.UTF_8);
    }
}
