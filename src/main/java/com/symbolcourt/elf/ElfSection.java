package com.symbolcourt.elf;

public class ElfSection {
    public static final int SHT_NULL = 0;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public final int index;
    public final String name;
    public final int type;
    public final long flags;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long entsize;

    public ElfSection(int index, String name, int type, long flags, long offset, long size,
                      int link, int info, long entsize) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.entsize = entsize;
    }
}
