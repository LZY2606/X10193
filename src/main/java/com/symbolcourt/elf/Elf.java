package com.symbolcourt.elf;

/** ELF constants needed by the symbol court. Values follow the generic/processor ABI. */
public final class Elf {
    private Elf() {}

    // identification
    public static final int EI_MAG0 = 0;
    public static final int EI_CLASS = 4;
    public static final int EI_DATA = 5;
    public static final int EI_NIDENT = 16;
    public static final byte ELFCLASS32 = 1;
    public static final byte ELFCLASS64 = 2;
    public static final byte ELFDATA2LSB = 1;
    public static final byte ELFDATA2MSB = 2;

    // e_type
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    // section types
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;
    public static final int SHT_GNU_versym = 0x6fffffff;

    public static final int SHF_ALLOC = 0x2;
    public static final int SHF_WRITE = 0x1;
    public static final int SHF_GROUP = 0x200;

    // special section indexes
    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    // symbol binding / type
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_COMMON = 5;
    public static final int STT_TLS = 6;

    // visibility
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    // x86-64 relocation types of interest (also used for illustrative fixtures)
    public static final int R_X86_64_64 = 1;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_X86_64_32 = 10;
    public static final int R_X86_64_PC32 = 2;

    // GNU versioning
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_VERSION_MASK = 0x7fff;

    public static final int VER_DEF_MIN = 0;
    public static final int VER_NEED_MIN = 1;
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_DEF_CURRENT = 1;
    public static final int VER_NEED_CURRENT = 1;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_VERNEED = 0x6ffffffe;
    public static final int DT_VERNEEDNUM = 0x6fffffff;
    public static final int DT_VERDEF = 0x6ffffffc;
    public static final int DT_VERDEFNUM = 0x6ffffffd;

    public static int stBind(int info) { return info >>> 4; }
    public static int stType(int info) { return info & 0xf; }
    public static int stInfo(int bind, int type) { return (bind << 4) | (type & 0xf); }
    public static int stVisibility(int other) { return other & 3; }
}
