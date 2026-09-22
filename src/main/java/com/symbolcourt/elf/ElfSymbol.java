package com.symbolcourt.elf;

public class ElfSymbol {
    public final String name;
    public final int nameIndex;
    public final int binding;
    public final int type;
    public final int visibility;
    public final int sectionIndex;
    public final long value;
    public final long size;
    public final int versionIndex;
    public final boolean local;
    public final boolean dynamic;

    public ElfSymbol(String name, int nameIndex, int binding, int type, int visibility, int sectionIndex,
                     long value, long size, int versionIndex, boolean local, boolean dynamic) {
        this.name = name;
        this.nameIndex = nameIndex;
        this.binding = binding;
        this.type = type;
        this.visibility = visibility;
        this.sectionIndex = sectionIndex;
        this.value = value;
        this.size = size;
        this.versionIndex = versionIndex;
        this.local = local;
        this.dynamic = dynamic;
    }

    public boolean isUndefined() {
        return sectionIndex == Constants.SHN_UNDEF;
    }

    public boolean isCommon() {
        return sectionIndex == Constants.SHN_COMMON;
    }

    public boolean hiddenBit() {
        return (versionIndex & Constants.VER_NDX_HIDDEN_FLAG) != 0;
    }
}
