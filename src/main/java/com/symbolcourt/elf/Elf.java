package com.symbolcourt.elf;

/** ELF 常量（以 ELF64 为主）。 */
public final class Elf {
    private Elf() {}

    public static final byte EI_MAG0 = 0x7f;
    public static final byte EI_MAG1 = 'E';
    public static final byte EI_MAG2 = 'L';
    public static final byte EI_MAG3 = 'F';
    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_GROUP = 17;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_EXECINSTR = 0x4L;
    public static final long SHF_WRITE = 0x1L;
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
    public static final int STT_SECTION = 3;
    public static final int STT_TLS = 6;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int GRP_COMDAT = 0x1;

    public static final int PT_LOAD = 1;
    public static final int PT_DYNAMIC = 2;
    public static final int PT_PHDR = 6;

    public static final long DT_NULL = 0;
    public static final long DT_NEEDED = 1;
    public static final long DT_HASH = 4;
    public static final long DT_STRTAB = 5;
    public static final long DT_SYMTAB = 6;
    public static final long DT_STRSZ = 10;
    public static final long DT_SYMENT = 11;
    public static final long DT_SONAME = 14;
    public static final long DT_VERSYM = 0x6fffffff;
    public static final long DT_VERDEF = 0x6ffffffc;
    public static final long DT_VERDEFNUM = 0x6ffffffd;
    public static final long DT_VERNEED = 0x6ffffffe;
    public static final long DT_VERNEEDNUM = 0x6fffffff;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_VERSION = 0x7fff;
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_FLG_INFO = 0x4;

    /** x86_64 常见重定位类型。 */
    public static String relocName(long machine, int type) {
        if (machine == 62) {
            switch (type) {
                case 0: return "R_X86_64_NONE";
                case 1: return "R_X86_64_64";
                case 2: return "R_X86_64_PC32";
                case 4: return "R_X86_64_PLT32";
                case 5: return "R_X86_64_COPY";
                case 6: return "R_X86_64_GLOB_DAT";
                case 7: return "R_X86_64_JUMP_SLOT";
                case 8: return "R_X86_64_RELATIVE";
                case 9: return "R_X86_64_RELATIVE";
                case 10: return "R_X86_64_32";
                case 11: return "R_X86_64_32S";
                case 18: return "R_X86_64_TPOFF64";
                case 24: return "R_X86_64_PC64";
                default: return "R_X86_64_" + type;
            }
        }
        return "R_" + type;
    }

    public static String bindName(int bind) {
        switch (bind) {
            case STB_LOCAL: return "LOCAL";
            case STB_GLOBAL: return "GLOBAL";
            case STB_WEAK: return "WEAK";
            default: return "STB_" + bind;
        }
    }

    public static String typeName(int type) {
        switch (type) {
            case STT_NOTYPE: return "NOTYPE";
            case STT_OBJECT: return "OBJECT";
            case STT_FUNC: return "FUNC";
            case STT_SECTION: return "SECTION";
            case STT_TLS: return "TLS";
            default: return "STT_" + type;
        }
    }

    public static String visibilityName(int vis) {
        switch (vis) {
            case STV_DEFAULT: return "DEFAULT";
            case STV_INTERNAL: return "INTERNAL";
            case STV_HIDDEN: return "HIDDEN";
            case STV_PROTECTED: return "PROTECTED";
            default: return "STV_" + vis;
        }
    }
}
