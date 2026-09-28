package com.symboltribunal.elf;

/** ELF constants used by the tribunal (subset of the System V gABI). */
public final class Elf {
    private Elf() {
    }

    public static final int EI_MAG0 = 0;
    public static final int EI_CLASS = 4;
    public static final int EI_DATA = 5;
    public static final int EI_VERSION = 6;
    public static final int EI_NIDENT = 16;

    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;

    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

    // section types
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_GROUP = 17;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;

    // special section indices
    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    // symbol bindings / types
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

    // versioning
    public static final int SHT_GNU_versym = 0x6fffffff;
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;

    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_VERNEEDNUM = 0x6fffffff;
    public static final int DT_VERDEFNUM = 0x6ffffffc;
    public static final int DT_VERSYM = 0x6ffffff0;

    public static int stBind(int info) {
        return info >> 4;
    }

    public static int stType(int info) {
        return info & 0xf;
    }

    public static int stInfo(int bind, int type) {
        return (bind << 4) | (type & 0xf);
    }

    public static int stVisibility(int other) {
        return other & 3;
    }

    public static String typeName(int type) {
        return switch (type) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            case ET_CORE -> "CORE";
            default -> "NONE(" + type + ")";
        };
    }

    public static String bindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND(" + bind + ")";
        };
    }

    public static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_PROTECTED -> "PROTECTED";
            case STV_HIDDEN -> "HIDDEN";
            default -> "VIS(" + vis + ")";
        };
    }
}
