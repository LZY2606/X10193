package com.symcourt.elf;

/** One symbol entry from SHT_SYMTAB or SHT_DYNSYM. */
public final class ElfSymbol {
    public final int index;
    public String name;
    public final long value;
    public final long size;
    public final int binding;
    public final int type;
    public final int visibility;
    public final int sectionIndex; // raw shndx, may be SHN_UNDEF / SHN_COMMON / SHN_ABS
    public final boolean dynamic;
    public final int nameOffset;
    public int versionIndex = -1; // -1 = no .gnu.version entry

    ElfSymbol(int index, String name, int nameOffset, long value, long size, int binding,
              int type, int visibility, int sectionIndex, boolean dynamic) {
        this.index = index;
        this.name = name;
        this.nameOffset = nameOffset;
        this.value = value;
        this.size = size;
        this.binding = binding;
        this.type = type;
        this.visibility = visibility;
        this.sectionIndex = sectionIndex;
        this.dynamic = dynamic;
    }

    void setName(String name) {
        this.name = name;
    }

    long nameOffset() {
        return nameOffset & 0xffffffffL;
    }

    public boolean isUndefined() {
        return sectionIndex == ElfConstants.SHN_UNDEF;
    }

    public boolean isCommon() {
        return type == ElfConstants.STT_COMMON || sectionIndex == ElfConstants.SHN_COMMON;
    }

    public boolean isLocal() {
        return binding == ElfConstants.STB_LOCAL;
    }

    public boolean isWeak() {
        return binding == ElfConstants.STB_WEAK;
    }

    public String bindingName() {
        return ElfConstants.bindingName(binding);
    }

    public String visibilityName() {
        return ElfConstants.visibilityName(visibility);
    }

    public String typeName() {
        return ElfConstants.typeName(type);
    }
}
