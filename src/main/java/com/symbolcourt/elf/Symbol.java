package com.symbolcourt.elf;

/**
 * One entry of a symbol table. {@code sectionIndex} uses the raw st_shndx
 * value; special values are represented as negative/named constants through
 * {@link #specialSection()}.
 */
public final class Symbol {
    public final String name;
    public final long nameIndex;
    public final SymbolBinding binding;
    public final SymbolType type;
    public final Visibility visibility;
    public final long sectionIndex;   // raw st_shndx
    public final long value;
    public final long size;
    public final boolean dynamic;     // from .dynsym vs .symtab
    public final int tableIndex;      // index inside its owning table
    public final int versionIndex;    // entry index into .gnu.version, 0 if none
    public String versionName;        // resolved via verdef/verneed (may be null)
    public boolean versionHidden;     // VER_NDX_HIDDEN bit (0x8000)

    public Symbol(String name, long nameIndex, SymbolBinding binding, SymbolType type,
                  Visibility visibility, long sectionIndex, long value, long size,
                  boolean dynamic, int tableIndex, int versionIndex) {
        this.name = name;
        this.nameIndex = nameIndex;
        this.binding = binding;
        this.type = type;
        this.visibility = visibility;
        this.sectionIndex = sectionIndex;
        this.value = value;
        this.size = size;
        this.dynamic = dynamic;
        this.tableIndex = tableIndex;
        this.versionIndex = versionIndex;
    }

    public boolean isUndefined() {
        return sectionIndex == 0; // SHN_UNDEF
    }

    public boolean isCommon() {
        return sectionIndex == 0xfff2; // SHN_COMMON
    }

    public boolean isAbsolute() {
        return sectionIndex == 0xfff1; // SHN_ABS
    }

    public String specialSection() {
        if (sectionIndex == 0) return "UND";
        if (isCommon()) return "COMMON";
        if (isAbsolute()) return "ABS";
        return null;
    }

    public boolean isDefined() {
        return !isUndefined();
    }

    @Override
    public String toString() {
        return name + "[" + binding + "/" + type + "/" + visibility
                + (versionIndex != 0 ? "/v" + versionIndex : "") + "]";
    }
}
