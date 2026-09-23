package com.gsb.tribunal.elf;

public record Symbol(
        int index,
        String name,
        int bind,
        int type,
        int visibility,
        int sectionIndex,
        long value,
        long size,
        String versionName,
        boolean versionNonDefault,
        boolean versionFromNeed,
        String groupSignature) {

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

    public boolean isUndefined() {
        return sectionIndex == SHN_UNDEF;
    }

    public boolean isCommon() {
        return sectionIndex == SHN_COMMON;
    }

    public boolean isDefined() {
        return sectionIndex != SHN_UNDEF && !isCommon();
    }

    public boolean isExportedDefinition() {
        return sectionIndex != SHN_UNDEF && !isCommon();
    }

    public String bindName() {
        return switch (bind) {
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "LOCAL";
        };
    }

    public String typeName() {
        return switch (type) {
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            default -> "NOTYPE";
        };
    }

    public String visibilityName() {
        return switch (visibility) {
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "DEFAULT";
        };
    }

    public String qualifiedName() {
        if (versionName == null) {
            return name;
        }
        return name + (versionNonDefault ? "@" : "@@") + versionName;
    }
}
