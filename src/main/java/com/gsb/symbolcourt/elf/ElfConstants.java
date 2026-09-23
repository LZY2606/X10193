package com.gsb.symbolcourt.elf;

public final class ElfConstants {
    private ElfConstants() {
    }

    public static final byte[] MAGIC = {0x7f, 'E', 'L', 'F'};

    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;

    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

    public static final int SHT_NULL = 0;
    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_NOTE = 7;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;
    public static final int SHT_GNU_versym = 0x6fffffff;

    public static final long SHF_WRITE = 0x1L;
    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_EXECINSTR = 0x4L;
    public static final long SHF_GROUP = 0x200L;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 0x3;
    public static final int STT_FILE = 0x4;
    public static final int STT_COMMON = 0xb;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VER_NDX_ELIMINATE = 0xff00;
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_VERSION = 0x7fff;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_VERNEED = 0x6ffffffe;
    public static final int DT_VERNEEDNUM = 0x6fffffff;
    public static final int DT_VERDEF = 0x6ffffffc;
    public static final int DT_VERDEFNUM = 0x6ffffffd;
    public static final int DT_VERSYM = 0x6ffffff0;

    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_32 = 10;
    public static final int R_X86_64_PC32 = 2;
    public static final int R_X86_64_PLT32 = 4;
    public static final int R_X86_64_64 = 1;

    public static final int GRP_COMDAT = 0x1;

    public static final String LOCAL_SECTION = null;

    public static int bind(int info) {
        return info >>> 4;
    }

    public static int type(int info) {
        return info & 0xf;
    }

    public static int visibility(int other) {
        return other & 0x3;
    }

    public static int makeInfo(int bind, int type) {
        return (bind << 4) | (type & 0xf);
    }
}
