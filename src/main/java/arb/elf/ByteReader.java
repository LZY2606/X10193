package arb.elf;

public class ByteReader {
    private final byte[] data;
    private final boolean little;

    public ByteReader(byte[] data, boolean little) {
        this.data = data;
        this.little = little;
    }

    public void check(long off, long len) throws ElfException {
        if (off < 0 || len < 0 || off > data.length || off + len > data.length
                || off + len < 0) {
            throw new ElfException("out of bounds: offset=" + off + " len=" + len
                    + " size=" + data.length);
        }
    }

    public int u8(long off) throws ElfException {
        check(off, 1);
        return data[(int) off] & 0xff;
    }

    public int u16(long off) throws ElfException {
        check(off, 2);
        int a = data[(int) off] & 0xff;
        int b = data[(int) off + 1] & 0xff;
        return little ? (a | (b << 8)) : ((a << 8) | b);
    }

    public long u32(long off) throws ElfException {
        check(off, 4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = data[(int) off + (little ? i : 3 - i)] & 0xff;
            v = (v << 8) | b;
        }
        return v & 0xffffffffL;
    }

    public long u64(long off) throws ElfException {
        check(off, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int b = data[(int) off + (little ? i : 7 - i)] & 0xff;
            v = (v << 8) | (b & 0xffL);
        }
        return v;
    }

    public long uint(boolean elf64, long off) throws ElfException {
        return elf64 ? u64(off) : u32(off);
    }

    public byte[] bytes(long off, int len) throws ElfException {
        check(off, len);
        byte[] out = new byte[len];
        System.arraycopy(data, (int) off, out, 0, len);
        return out;
    }

    public String cstring(long off, int max) throws ElfException {
        if (off < 0 || off > data.length) {
            throw new ElfException("string table out of bounds");
        }
        StringBuilder sb = new StringBuilder();
        int end = (int) Math.min(data.length, off + max);
        for (int i = (int) off; i < end; i++) {
            byte b = data[i];
            if (b == 0) {
                break;
            }
            sb.append((char) (b & 0xff));
        }
        return sb.toString();
    }

    public int size() {
        return data.length;
    }
}
