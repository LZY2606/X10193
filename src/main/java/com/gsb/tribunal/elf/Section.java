package com.gsb.tribunal.elf;

public record Section(
        int index,
        String name,
        int type,
        long flags,
        long address,
        long offset,
        long size,
        int link,
        int info,
        long alignment,
        long entrySize) {

    public static final int SHT_NULL = 0;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
}
