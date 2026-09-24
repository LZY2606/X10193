package com.example.symboltribunal.elf;

public final class ElfConstants {
    private ElfConstants() {
    }

    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;

    public static final int SHT_GNU_VERSYM = 0x6fffffff;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_GNU_UNIQUE = 10;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int GRP_COMDAT = 0x1;

    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_VERSION_MASK = 0x7fff;
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;

    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_AARCH64_COPY = 1024;
    public static final int R_AARCH64_GLOB_DAT = 1025;
    public static final int R_AARCH64_JUMP_SLOT = 1026;
    public static final int R_AARCH64_RELATIVE = 1027;
}
