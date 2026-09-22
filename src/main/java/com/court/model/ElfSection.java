package com.court.model;

public record ElfSection(
        int index, String name, int type, long flags, long addr,
        long offset, long size, int link, int info,
        long addralign, long entsize) {
    public static final long SHF_ALLOC = 0x2L;
    public static final int SHT_NOBITS = 8;
    public boolean allocated() { return (flags & SHF_ALLOC) != 0L; }
}
