package org.symcourt.elf;

/**
 * One symbol table entry ({@code Elf32_Sym}/{@code Elf64_Sym}) plus the
 * resolved GNU symbol version info.
 */
public final class Symbol {
    public enum Table { STATIC, DYNAMIC }

    public final int index;
    public final Table table;
    public final String name;
    public final long value;
    public final long size;
    public final int binding;
    public final int type;
    public final int visibility;
    public final int shndx;
    /** Raw SHT_GNU_versym value including the hidden bit. */
    public int versionRaw = -1;
    /** Resolved definition version name (e.g. "LIB_1.1"), or null. */
    public String defVersion;
    /** Alias version names sharing the same verdef node, or empty. */
    public java.util.List<String> defAliases = java.util.List.of();
    /** Verdef/Verneed node flags (VER_FLG_BASE / VER_FLG_WEAK). */
    public int versionFlags;
    /** For undefined versioned symbols: the required version name. */
    public String needVersion;
    /** For undefined versioned symbols: the required soname. */
    public String needFrom;
    /** True when the versym entry carries the VERSYM_HIDDEN bit. */
    public boolean versionHidden;

    public Symbol(int index, Table table, String name, long value, long size,
                  int binding, int type, int visibility, int shndx) {
        this.index = index;
        this.table = table;
        this.name = name;
        this.value = value;
        this.size = size;
        this.binding = binding;
        this.type = type;
        this.visibility = visibility;
        this.shndx = shndx;
    }

    public boolean defined() {
        return shndx != Elf.SHN_UNDEF;
    }

    public boolean isCommon() {
        return shndx == Elf.SHN_COMMON
                || (defined() && type == Elf.STT_COMMON);
    }

    public boolean weak() {
        return binding == Elf.STB_WEAK;
    }

    public boolean local() {
        return binding == Elf.STB_LOCAL;
    }

    public boolean hidden() {
        return visibility == Elf.STV_HIDDEN || visibility == Elf.STV_INTERNAL;
    }

    public boolean protectedVis() {
        return visibility == Elf.STV_PROTECTED;
    }

    public String key() {
        return name + (defVersion != null ? "@@" + defVersion
                : needVersion != null ? "@" + needVersion : "");
    }

    @Override
    public String toString() {
        return name + "[" + Elf.bindingName(binding)
                + "/" + Elf.symbolTypeName(type)
                + "/" + Elf.visibilityName(visibility)
                + (defVersion != null ? "@@" + defVersion : "")
                + (needVersion != null ? "@" + needVersion : "") + "]";
    }
}
