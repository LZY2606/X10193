package com.example.tribunal.elf;

final class ElfConstants {
    static final int ET_REL = 1;
    static final int ET_EXEC = 2;
    static final int ET_DYN = 3;

    static final int SHT_SYMTAB = 2;
    static final int SHT_STRTAB = 3;
    static final int SHT_RELA = 4;
    static final int SHT_GROUP = 17;
    static final int SHT_DYNAMIC = 6;
    static final int SHT_REL = 9;
    static final int SHT_DYNSYM = 11;
    static final int SHT_GNU_VERDEF = 0x6ffffffd;
    static final int SHT_GNU_VERNEED = 0x6ffffffe;
    static final int SHT_GNU_VERSYM = 0x6fffffff;

    static final int SHF_ALLOC = 2;
    static final int SHN_UNDEF = 0;
    static final int SHN_ABS = 0xfff1;
    static final int SHN_COMMON = 0xfff2;
    static final int SHN_XINDEX = 0xffff;

    static final int STB_LOCAL = 0;
    static final int STB_GLOBAL = 1;
    static final int STB_WEAK = 2;

    static final int STT_NOTYPE = 0;
    static final int STT_OBJECT = 1;
    static final int STT_FUNC = 2;
    static final int STT_COMMON = 5;

    static final int STV_DEFAULT = 0;
    static final int STV_INTERNAL = 1;
    static final int STV_HIDDEN = 2;
    static final int STV_PROTECTED = 3;

    static final int DT_NEEDED = 1;
    static final int DT_SONAME = 14;
    static final int DT_NULL = 0;

    static final int VER_FLG_BASE = 1;
    static final int VER_FLG_WEAK = 2;
    static final int VER_NDX_LOCAL = 0;
    static final int VER_NDX_GLOBAL = 1;
    static final int VERSYM_HIDDEN = 0x8000;

    private ElfConstants() {
    }
}
