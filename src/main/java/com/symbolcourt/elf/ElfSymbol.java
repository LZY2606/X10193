package com.symbolcourt.elf;

public class ElfSymbol {
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

    public final int index;
    public final String name;
    public final int info;
    public final int other;
    public final int shndx;
    public final long value;
    public final long size;
    public final boolean dynamic;

    /** Filled in after version sections are resolved. */
    public String versionName;
    public boolean versionHidden;
    public String versionFile;

    public ElfSymbol(int index, String name, int info, int other, int shndx,
                     long value, long size, boolean dynamic) {
        this.index = index;
        this.name = name;
        this.info = info;
        this.other = other;
        this.shndx = shndx;
        this.value = value;
        this.size = size;
        this.dynamic = dynamic;
    }

    public int bind() { return info >> 4; }
    public int type() { return info & 0xf; }
    public int visibility() { return other & 0x3; }

    public boolean isDefined() { return shndx != SHN_UNDEF; }
    public boolean isCommon() { return shndx == SHN_COMMON; }
    public boolean isLocal() { return bind() == BIND_LOCAL; }
    public boolean isWeak() { return bind() == BIND_WEAK; }

    public String bindName() {
        return switch (bind()) {
            case BIND_LOCAL -> "LOCAL";
            case BIND_GLOBAL -> "GLOBAL";
            case BIND_WEAK -> "WEAK";
            default -> "BIND#" + bind();
        };
    }

    public String visibilityName() {
        return switch (visibility()) {
            case VIS_DEFAULT -> "DEFAULT";
            case VIS_INTERNAL -> "INTERNAL";
            case VIS_HIDDEN -> "HIDDEN";
            case VIS_PROTECTED -> "PROTECTED";
            default -> "VIS#" + visibility();
        };
    }

    public String typeName() {
        return switch (type()) {
            case TYPE_NOTYPE -> "NOTYPE";
            case TYPE_OBJECT -> "OBJECT";
            case TYPE_FUNC -> "FUNC";
            case TYPE_SECTION -> "SECTION";
            case TYPE_COMMON -> "COMMON";
            case TYPE_GNU_IFUNC -> "IFUNC";
            default -> "TYPE#" + type();
        };
    }
}
