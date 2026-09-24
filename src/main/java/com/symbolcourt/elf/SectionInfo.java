package com.symbolcourt.elf;

public record SectionInfo(
        int index,
        int name,
        String nameString,
        int type,
        long flags,
        long address,
        long offset,
        long size,
        int link,
        long info,
        long addralign,
        long entsize
) {
    public static final int SHT_NULL = 0;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;
    public static final int SHT_GNU_versym = 0x6fffffff;
}
