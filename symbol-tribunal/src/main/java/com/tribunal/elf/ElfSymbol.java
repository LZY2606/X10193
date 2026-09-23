package com.tribunal.elf;

/** One symbol table entry (from .symtab or .dynsym). */
public final class ElfSymbol {
    public final int index;
    public final String name;
    public final long value;
    public final long size;
    public final int binding;      // STB_*
    public final int type;         // STT_*
    public final int visibility;   // STV_*
    public final int sectionIndex; // section header index, SHN_* sentinel, or 0 (undef)
    public final String sectionName;
    public final boolean dynamic;  // from .dynsym

    public ElfSymbol(int index, String name, long value, long size, int binding, int type,
                     int visibility, int sectionIndex, String sectionName, boolean dynamic) {
        this.index = index;
        this.name = name;
        this.value = value;
        this.size = size;
        this.binding = binding;
        this.type = type;
        this.visibility = visibility;
        this.sectionIndex = sectionIndex;
        this.sectionName = sectionName;
        this.dynamic = dynamic;
    }

    public boolean isUndef() {
        return sectionIndex == ElfConstants.SHN_UNDEF;
    }

    public boolean isCommon() {
        return sectionIndex == ElfConstants.SHN_COMMON || type == ElfConstants.STT_COMMON;
    }

    public boolean isDefined() {
        return !isUndef();
    }

    public boolean isWeak() {
        return binding == ElfConstants.STB_WEAK;
    }

    public boolean isLocal() {
        return binding == ElfConstants.STB_LOCAL;
    }

    public boolean isHidden() {
        return visibility == ElfConstants.STV_HIDDEN || visibility == ElfConstants.STV_INTERNAL;
    }

    public boolean isProtected() {
        return visibility == ElfConstants.STV_PROTECTED;
    }
}
