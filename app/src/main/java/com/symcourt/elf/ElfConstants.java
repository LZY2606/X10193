package com.symcourt.elf;

/** Constants from the System V gABI / Linux ELF specification. */
public final class ElfConstants {
    private ElfConstants() {}

    public static final byte EI_MAG0 = 0x7f;
    public static final int EI_CLASS = 4;
    public static final int EI_DATA = 5;
    public static final int EI_NIDENT = 16;

    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_GROUP = 17;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;

    public static final long SHF_WRITE = 0x1L;
    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_GROUP = 0x200L;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_COMMON = 5;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = (1 << 16) - 1;   // 0xfff1
    public static final int SHN_COMMON = (1 << 16) - 2; // 0xfff2

    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_VERNEED = 0x6ffffffe;
    public static final int DT_VERNEEDNUM = 0x6fffffff;
    public static final int DT_VERDEF = 0x6ffffffc;
    public static final int DT_VERDEFNUM = 0x6ffffffd;
    public static final int DT_VERSYM = 0x6ffffff0;

    public static final int GRP_COMDAT = 0x1;

    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VER_NDX_GNU_ELIMINATED = 2;
    public static final int VER_NDX_GNU_VERSION = 0x4000;

    // x86-64 relocation types we care about.
    public static final int R_X86_64_NONE = 0;
    public static final int R_X86_64_64 = 1;
    public static final int R_X86_64_PC32 = 2;
    public static final int R_X86_64_GOT32 = 3;
    public static final int R_X86_64_PLT32 = 4;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_X86_64_32 = 10;
    public static final int R_X86_64_32S = 11;
    public static final int R_X86_64_PC64 = 24;

    static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "OTHER(" + vis + ")";
        };
    }

    static String bindingName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "OTHER(" + bind + ")";
        };
    }

    static String typeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_COMMON -> "COMMON";
            default -> "OTHER(" + type + ")";
        };
    }
}
