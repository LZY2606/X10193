package com.gsb.symbolcourt.elf;

public record ElfSymbol(
        int index,
        String name,
        int bind,
        int type,
        int visibility,
        int sectionIndex,
        long value,
        long size,
        int versionIndex,
        String versionName,
        boolean versionNonDefault,
        boolean versionHidden
) {
    public boolean isUndefined() {
        return sectionIndex == ElfConstants.SHN_UNDEF && value == 0;
    }

    public boolean isCommon() {
        return sectionIndex == ElfConstants.SHN_COMMON;
    }

    public boolean isDefined() {
        return !isUndefined();
    }

    public boolean isLocal() {
        return bind == ElfConstants.STB_LOCAL;
    }

    public boolean isWeak() {
        return bind == ElfConstants.STB_WEAK;
    }

    public boolean isHidden() {
        return visibility == ElfConstants.STV_HIDDEN || visibility == ElfConstants.STV_INTERNAL;
    }

    public boolean isProtected() {
        return visibility == ElfConstants.STV_PROTECTED;
    }

    public String bindName() {
        return switch (bind) {
            case ElfConstants.STB_LOCAL -> "LOCAL";
            case ElfConstants.STB_GLOBAL -> "GLOBAL";
            case ElfConstants.STB_WEAK -> "WEAK";
            default -> "STB_" + bind;
        };
    }

    public String typeName() {
        return switch (type) {
            case ElfConstants.STT_NOTYPE -> "NOTYPE";
            case ElfConstants.STT_OBJECT -> "OBJECT";
            case ElfConstants.STT_FUNC -> "FUNC";
            case ElfConstants.STT_SECTION -> "SECTION";
            case ElfConstants.STT_FILE -> "FILE";
            case ElfConstants.STT_COMMON -> "TLS_COMMON";
            default -> "STT_" + type;
        };
    }

    public String visibilityName() {
        return switch (visibility) {
            case ElfConstants.STV_DEFAULT -> "DEFAULT";
            case ElfConstants.STV_INTERNAL -> "INTERNAL";
            case ElfConstants.STV_HIDDEN -> "HIDDEN";
            case ElfConstants.STV_PROTECTED -> "PROTECTED";
            default -> "VIS_" + visibility;
        };
    }
}
