package gsb.symboltribunal.elf;

import java.nio.charset.StandardCharsets;

/** Bounds-checked random access over a byte array. */
final class Bin {
    private final byte[] data;
    final boolean le;

    Bin(byte[] data, boolean littleEndian) {
        this.data = data;
        this.le = littleEndian;
    }

    int length() {
        return data.length;
    }

    void require(int off, int n) {
        if (off < 0 || n < 0 || (long) off + n > data.length) {
            throw new ElfException("读取越过文件边界: offset=" + off + " bytes=" + n + " length=" + data.length);
        }
    }

    byte u8(int off) {
        require(off, 1);
        return data[off];
    }

    int u8u(int off) {
        return u8(off) & 0xff;
    }

    int u16(int off) {
        require(off, 2);
        int a = data[off] & 0xff;
        int b = data[off + 1] & 0xff;
        return le ? (b << 8) | a : (a << 8) | b;
    }

    long u32(int off) {
        require(off, 4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = data[off + (le ? i : 3 - i)] & 0xff;
            v |= b << (8 * i);
        }
        return v & 0xffffffffL;
    }

    long u64(int off) {
        require(off, 8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            int b = data[off + (le ? i : 7 - i)] & 0xff;
            v |= ((long) b) << (8 * i);
        }
        return v;
    }

    String cstr(int off) {
        if (off < 0 || off >= data.length) {
            throw new ElfException("字符串偏移越界: " + off);
        }
        int end = off;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, off, end - off, StandardCharsets.UTF_8);
    }

    byte[] slice(int off, int len) {
        require(off, len);
        byte[] out = new byte[len];
        System.arraycopy(data, off, out, 0, len);
        return out;
    }
}
