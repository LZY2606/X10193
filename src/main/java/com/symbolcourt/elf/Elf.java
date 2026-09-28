package com.symbolcourt.elf;

/** ELF constants used by the parser and the fixture builder. */
public final class Elf {
    private Elf() {}

    // e_ident
    public static final byte ELFMAG0 = 0x7f;
    public static final byte ELFMAG1 = 'E';
    public static final byte ELFMAG2 = 'L';
    public static final byte ELFMAG3 = 'F';
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int EV_CURRENT = 1;

    // e_type
    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

    // section types
    public static final int SHT_NULL = 0;
    public static final int SHT_PROGBITS = 1;
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

    // section flags
    public static final long SHF_ALLOC = 0x2;

    // group flags
    public static final int GRP_COMDAT = 0x1;

    // bindings
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    // symbol types
    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_FILE = 4;
    public static final int STT_COMMON = 5;

    // special section indexes
    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    // visibility
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    // version
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;

    // common x86_64 relocation types we display
    public static String relocTypeName(long type) {
        return switch ((int) type) {
            case 0 -> "R_X86_64_NONE";
            case 1 -> "R_X86_64_64";
            case 2 -> "R_X86_64_PC32";
            case 4 -> "R_X86_64_PLT32";
            case 5 -> "R_X86_64_COPY";
            case 6 -> "R_X86_64_GLOB_DAT";
            case 7 -> "R_X86_64_JUMP_SLOT";
            case 8 -> "R_X86_64_RELATIVE";
            case 10 -> "R_X86_64_32S";
            default -> "R_" + type;
        };
    }

    public static String bindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND_" + bind;
        };
    }

    public static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "STV_" + vis;
        };
    }

    public static String typeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_SECTION -> "SECTION";
            case STT_FILE -> "FILE";
            case STT_COMMON -> "COMMON";
            default -> "TYPE_" + type;
        };
    }
}
