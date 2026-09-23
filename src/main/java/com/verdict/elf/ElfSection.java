package com.verdict.elf;

public final class ElfSection {
    public static final int SHT_NULL = 0;
    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final long SHF_WRITE = 0x1;
    public static final long SHF_ALLOC = 0x2;
    public static final long SHF_EXECINSTR = 0x4;
    public static final long SHF_GROUP = 0x200;
    public static final long SHF_EXCLUDE = 0x80000000L;

    public static final int GRP_COMDAT = 0x1;

    public int index;
    public String name = "";
    public int type;
    public long flags;
    public long addr;
    public long offset;
    public long size;
    public int link;
    public int info;
    public long addralign;
    public long entsize;

    /** For SHT_GROUP: signature symbol name. */
    public String groupSignature;
    /** For SHT_GROUP: COMDAT flag seen. */
    public boolean groupComdat;

    public boolean isGroupMemberOf(String signature) {
        return false;
    }
}
