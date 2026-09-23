package com.court.verdict.elf;

public final class ElfConstants {
    private ElfConstants() {}

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_COMMON = 0xfff2;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int GRP_COMDAT = 1;

    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_IDX_MASK = 0x7fff;

    public static final long DT_NULL = 0;
    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;

    public static String bindName(int b) {
        return switch (b) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND(" + b + ")";
        };
    }

    public static String visName(int v) {
        return switch (v) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS(" + v + ")";
        };
    }

    public static String typeName(int t) {
        return switch (t) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            default -> "TYPE(" + t + ")";
        };
    }
}
