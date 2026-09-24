package com.symbolcourt.model;

public class ElfSection {
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_WRITE = 0x1L;
    public static final long SHF_EXECINSTR = 0x4L;

    public String name;
    public int nameOffset;
    public int index;
    public int type;
    public long flags;
    public long addr;
    public long offset;
    public long size;
    public int link;
    public int info;
    public long addralign;
    public long entsize;
    public byte[] data;
}
