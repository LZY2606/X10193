package com.tribunal.elf;

public class ElfSymbol {
    public static final int STB_LOCAL = 0, STB_GLOBAL = 1, STB_WEAK = 2;
    public static final int STT_NOTYPE = 0, STT_OBJECT = 1, STT_FUNC = 2, STT_SECTION = 3, STT_COMMON = 5;
    public static final int STV_DEFAULT = 0, STV_INTERNAL = 1, STV_HIDDEN = 2, STV_PROTECTED = 3;
    public static final int SHN_UNDEF = 0, SHN_COMMON = 0xfff2;

    public int index;
    public String name = "";
    public int bind;
    public int type;
    public int visibility;
    public int shndx;
    public long value;
    public long size;
    public String table; // "symtab" or "dynsym"
    public String version;      // restored version name, null when unversioned
    public boolean versionHidden;

    public boolean defined() {
        return shndx != SHN_UNDEF;
    }

    public boolean common() {
        return shndx == SHN_COMMON;
    }

    public String bindName() {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND#" + bind;
        };
    }

    public String typeName() {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_SECTION -> "SECTION";
            case STT_COMMON -> "COMMON";
            default -> "TYPE#" + type;
        };
    }

    public String visibilityName() {
        return switch (visibility) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS#" + visibility;
        };
    }
}
