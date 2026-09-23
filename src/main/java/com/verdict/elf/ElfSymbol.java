package com.verdict.elf;

public final class ElfSymbol {
    public static final int BIND_LOCAL = 0;
    public static final int BIND_GLOBAL = 1;
    public static final int BIND_WEAK = 2;

    public static final int TYPE_NOTYPE = 0;
    public static final int TYPE_OBJECT = 1;
    public static final int TYPE_FUNC = 2;
    public static final int TYPE_SECTION = 3;
    public static final int TYPE_COMMON = 5;
    public static final int TYPE_GNU_IFUNC = 10;

    public static final int VIS_DEFAULT = 0;
    public static final int VIS_INTERNAL = 1;
    public static final int VIS_HIDDEN = 2;
    public static final int VIS_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public int index;
    public String name = "";
    public long value;
    public long size;
    public int bind;
    public int type;
    public int visibility;
    public int shndx;
    public boolean dynamic;
    /** Raw index from .gnu.version (hidden bit stripped); -1 when no versym present. */
    public int versionIndex = -1;
    /** True when the versym hidden bit (non-default version, name@ver) was set. */
    public boolean versionHidden;
    /** Resolved version name, or null when unversioned. */
    public String versionName;

    public boolean isUndefined() {
        return shndx == SHN_UNDEF;
    }

    public boolean isCommon() {
        return shndx == SHN_COMMON;
    }

    public boolean isWeak() {
        return bind == BIND_WEAK;
    }

    public boolean isLocal() {
        return bind == BIND_LOCAL;
    }

    public boolean isHidden() {
        return visibility == VIS_HIDDEN || visibility == VIS_INTERNAL;
    }

    public boolean isProtected() {
        return visibility == VIS_PROTECTED;
    }

    public String bindName() {
        return switch (bind) {
            case BIND_LOCAL -> "LOCAL";
            case BIND_GLOBAL -> "GLOBAL";
            case BIND_WEAK -> "WEAK";
            default -> "BIND_" + bind;
        };
    }

    public String visibilityName() {
        return switch (visibility) {
            case VIS_DEFAULT -> "DEFAULT";
            case VIS_INTERNAL -> "INTERNAL";
            case VIS_HIDDEN -> "HIDDEN";
            case VIS_PROTECTED -> "PROTECTED";
            default -> "VIS_" + visibility;
        };
    }

    public String typeName() {
        return switch (type) {
            case TYPE_NOTYPE -> "NOTYPE";
            case TYPE_OBJECT -> "OBJECT";
            case TYPE_FUNC -> "FUNC";
            case TYPE_SECTION -> "SECTION";
            case TYPE_COMMON -> "COMMON";
            case TYPE_GNU_IFUNC -> "IFUNC";
            default -> "TYPE_" + type;
        };
    }
}
