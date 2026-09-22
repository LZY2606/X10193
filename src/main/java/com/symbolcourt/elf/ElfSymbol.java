package com.symbolcourt.elf;

/** A single symbol-table entry with its version already resolved to a name. */
public final class ElfSymbol {
    public enum Table { SYMTAB, DYNSYM }

    public final int index;
    public final Table table;
    public final String name;
    public final int bind;
    public final int type;
    public final int visibility;
    /** Resolved section index (SHN_XINDEX already expanded). */
    public final int shndx;
    public final long value;
    public final long size;
    /** Resolved version name, or null when unversioned. */
    public final String version;
    /** True for the default (@@) version; false for non-default (@) or unversioned. */
    public final boolean versionDefault;
    /** Library that must provide the version (from VERNEED), or null. */
    public final String versionFrom;

    public ElfSymbol(int index, Table table, String name, int bind, int type, int visibility,
                     int shndx, long value, long size,
                     String version, boolean versionDefault, String versionFrom) {
        this.index = index;
        this.table = table;
        this.name = name;
        this.bind = bind;
        this.type = type;
        this.visibility = visibility;
        this.shndx = shndx;
        this.value = value;
        this.size = size;
        this.version = version;
        this.versionDefault = versionDefault;
        this.versionFrom = versionFrom;
    }

    public boolean isUndefined() {
        return shndx == Elf.SHN_UNDEF;
    }

    public boolean isCommon() {
        return shndx == Elf.SHN_COMMON;
    }

    public boolean isLocal() {
        return bind == Elf.STB_LOCAL;
    }

    public boolean isWeak() {
        return bind == Elf.STB_WEAK;
    }

    /** Display form: name, name@VER or name@@VER. */
    public String displayName() {
        if (version == null) {
            return name;
        }
        return name + (versionDefault ? "@@" : "@") + version;
    }
}
