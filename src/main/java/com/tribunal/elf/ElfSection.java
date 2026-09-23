package com.tribunal.elf;

public class ElfSection {
    public static final int SHT_SYMTAB = 2, SHT_STRTAB = 3, SHT_RELA = 4, SHT_DYNAMIC = 6,
            SHT_REL = 9, SHT_DYNSYM = 11, SHT_GROUP = 17,
            SHT_GNU_VERDEF = 0x6ffffffd, SHT_GNU_VERNEED = 0x6ffffffe, SHT_GNU_VERSYM = 0x6fffffff;

    public int index;
    public String name = "";
    public int type;
    public long flags;
    public long addr;
    public long offset;
    public long size;
    public int link;
    public int info;
    public long entsize;
}
