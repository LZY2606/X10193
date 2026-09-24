package com.symtribunal.elf;

/**
 * One symbol table entry after the raw version index has been resolved to
 * a version name.
 */
public final class SymbolDef {
    private final String name;
    private final int binding;
    private final int type;
    private final int visibility;
    private final short sectionIndex;
    private final long sectionIndexExt;
    private final long value;
    private final long size;
    private final int rawVersionIndex;
    private final String versionName;
    private final boolean versionDefault;
    private final boolean dynamic;

    public SymbolDef(String name, int binding, int type, int visibility,
                     short sectionIndex, long sectionIndexExt, long value, long size,
                     int rawVersionIndex, String versionName, boolean versionDefault,
                     boolean dynamic) {
        this.name = name;
        this.binding = binding;
        this.type = type;
        this.visibility = visibility;
        this.sectionIndex = sectionIndex;
        this.sectionIndexExt = sectionIndexExt;
        this.value = value;
        this.size = size;
        this.rawVersionIndex = rawVersionIndex;
        this.versionName = versionName;
        this.versionDefault = versionDefault;
        this.dynamic = dynamic;
    }

    public String name() {
        return name;
    }

    public int binding() {
        return binding;
    }

    public int type() {
        return type;
    }

    public int visibility() {
        return visibility;
    }

    public short sectionIndex() {
        return sectionIndex;
    }

    public long sectionIndexExt() {
        return sectionIndexExt;
    }

    public long value() {
        return value;
    }

    public long size() {
        return size;
    }

    public int rawVersionIndex() {
        return rawVersionIndex;
    }

    public String versionName() {
        return versionName;
    }

    public boolean versionDefault() {
        return versionDefault;
    }

    public boolean dynamic() {
        return dynamic;
    }

    public boolean defined() {
        return sectionIndex != ElfConstants.SHN_UNDEF;
    }

    public boolean common() {
        return sectionIndex == ElfConstants.SHN_COMMON;
    }

    public boolean local() {
        return binding == ElfConstants.STB_LOCAL;
    }

    public boolean weak() {
        return binding == ElfConstants.STB_WEAK;
    }

    public boolean strong() {
        return binding == ElfConstants.STB_GLOBAL;
    }

    public String bindingName() {
        return switch (binding) {
            case ElfConstants.STB_LOCAL -> "LOCAL";
            case ElfConstants.STB_GLOBAL -> "GLOBAL";
            case ElfConstants.STB_WEAK -> "WEAK";
            default -> "BIND_" + binding;
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
