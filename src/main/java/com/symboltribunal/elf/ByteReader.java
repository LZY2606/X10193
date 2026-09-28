package com.symboltribunal.elf;

import java.nio.charset.StandardCharsets;

/** Bounds-checked, endian-aware reader over a fixed byte array. */
public class ByteReader {
    private final byte[] data;
    private final boolean littleEndian;

    public ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    public int length() {
        return data.length;
    }

    public int u8(int off) throws ElfException {
        require(off, 1);
        return data[off] & 0xff;
    }

    public int u16(int off) throws ElfException {
        require(off, 2);
        if (littleEndian) {
            return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
        }
        return ((data[off] & 0xff) << 8) | (data[off + 1] & 0xff);
    }

    public long u32(int off) throws ElfException {
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
        return v & 0xffffffffL;
    }

    public long u64(int off) throws ElfException {
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

    /** Reads an Elf{32,64}_Addr sized integer (4 bytes for ELF32, 8 for ELF64). */
    public long addr(int off, boolean elf64) throws ElfException {
        return elf64 ? u64(off) : u32(off);
    }

    public byte[] slice(int off, int len) throws ElfException {
        require(off, len);
        byte[] out = new byte[len];
        System.arraycopy(data, off, out, 0, len);
        return out;
    }

    public void require(int off, int len) throws ElfException {
        if (off < 0 || len < 0 || off > data.length - len) {
            throw new ElfException("out of bounds read: offset=" + off + " length=" + len
                    + " fileSize=" + data.length);
        }
    }

    public String cString(int off) throws ElfException {
        if (off < 0 || off >= data.length) {
            throw new ElfException("string table offset out of bounds: " + off);
        }
        int end = off;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, off, end - off, StandardCharsets.UTF_8);
    }
}
