package com.tribunal.elf;

/** ELF 常量。 */
public final class Elf {
    private Elf() {}

    // e_type
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    // sh_type
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;
    public static final int SHT_RELR = 19;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_STRTAB = 3;

    // shndx specials
    public static final int SHN_UNDEF = 0;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_XINDEX = 0xffff;

    // bind
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_GNU_UNIQUE = 10;

    // type
    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_COMMON = 5;
    public static final int STT_GNU_IFUNC = 10;

    // visibility
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    // group flags
    public static final int GRP_COMDAT = 1;

    // versym specials
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_VERSION = 0x7fff;

    // verdef flags
    public static final int VER_FLG_BASE = 1;
    public static final int VER_FLG_WEAK = 2;

    // dynamic tags
    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;
    public static final long DT_NULL = 0;

    public static String bindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            case STB_GNU_UNIQUE -> "GNU_UNIQUE";
            default -> "BIND#" + bind;
        };
    }

    public static String typeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_SECTION -> "SECTION";
            case STT_COMMON -> "COMMON";
            case STT_GNU_IFUNC -> "GNU_IFUNC";
            default -> "TYPE#" + type;
        };
    }

    public static String visName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS#" + vis;
        };
    }
}
