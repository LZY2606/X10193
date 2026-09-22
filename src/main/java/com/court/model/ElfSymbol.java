package com.court.model;

public class ElfSymbol {
    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    private final int index;
    private final int nameOffset;
    private final String name;
    private final int bind;
    private final int type;
    private final int visibility;
    private final int shndx;
    private final long value;
    private final long size;

    public ElfSymbol(int index, int nameOffset, String name, int bind, int type,
                     int visibility, int shndx, long value, long size) {
        this.index = index;
        this.nameOffset = nameOffset;
        this.name = name;
        this.bind = bind;
        this.type = type;
        this.visibility = visibility;
        this.shndx = shndx;
        this.value = value;
        this.size = size;
    }

    public int getIndex() { return index; }
    public int getNameOffset() { return nameOffset; }
    public String getName() { return name; }
    public int getBind() { return bind; }
    public int getType() { return type; }
    public int getVisibility() { return visibility; }
    public int getShndx() { return shndx; }
    public long getValue() { return value; }
    public long getSize() { return size; }

    public boolean isUndefined() { return shndx == SHN_UNDEF; }
    public boolean isCommon() { return shndx == SHN_COMMON; }
    public boolean isLocal() { return bind == 0; }
    public boolean isWeak() { return bind == 2; }
    public boolean isGlobal() { return bind == 1; }

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

    public String bindName() {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND(" + bind + ")";
        };
    }

    public String visibilityName() {
        return switch (visibility) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS(" + visibility + ")";
        };
    }
}
