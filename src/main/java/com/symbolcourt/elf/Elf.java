package com.symbolcourt.elf;

/** ELF constants used by the reader and the adjudicator. */
public final class Elf {
    private Elf() {
    }

    // e_type
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    // section types
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    // special section indices
    public static final int SHN_UNDEF = 0;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    // symbol bindings
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_GNU_UNIQUE = 10;

    // symbol types
    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;

    // symbol visibility
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    // versym
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_VERSION = 0x7fff;
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    // verdef flags
    public static final int VER_FLG_BASE = 1;

    // group flags
    public static final int GRP_COMDAT = 1;

    public static String bindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            case STB_GNU_UNIQUE -> "GNU_UNIQUE";
            default -> "bind#" + bind;
        };
    }

    public static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "vis#" + vis;
        };
    }

    public static String typeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            default -> "type#" + type;
        };
    }
}
