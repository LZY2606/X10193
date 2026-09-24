package com.example.tribunal.elf;

/**
 * One ELF symbol entry (ELF32 or ELF64, normalised).
 */
public record ElfSymbol(
        int index,
        String name,
        long value,
        long size,
        int info,
        int other,
        int sectionIndex,
        String tableName,
        boolean dynamic) {

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

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public int binding() {
        return info >>> 4;
    }

    public int type() {
        return info & 0xf;
    }

    public int visibility() {
        return other & 0x3;
    }

    public boolean isLocal() {
        return binding() == STB_LOCAL;
    }

    public boolean isWeak() {
        return binding() == STB_WEAK;
    }

    public boolean isUndefined() {
        return sectionIndex == SHN_UNDEF;
    }

    public boolean isCommon() {
        return sectionIndex == SHN_COMMON;
    }

    public boolean isDefined() {
        return !isUndefined();
    }

    public boolean isHidden() {
        return visibility() == STV_HIDDEN || visibility() == STV_INTERNAL;
    }

    public boolean isProtected() {
        return visibility() == STV_PROTECTED;
    }

    public String bindingName() {
        return switch (binding()) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "UNKNOWN(" + binding() + ")";
        };
    }

    public String typeName() {
        return switch (type()) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            default -> "OTHER(" + type() + ")";
        };
    }

    public String visibilityName() {
        return switch (visibility()) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "OTHER";
        };
    }
}
