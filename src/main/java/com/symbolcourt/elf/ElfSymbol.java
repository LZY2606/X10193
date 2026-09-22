package com.symbolcourt.elf;

/**
 * One symbol table entry (.symtab or .dynsym), with the symbol-version
 * index already resolved to a concrete version name where possible.
 */
public final class ElfSymbol {

    public enum VersionSource { NONE, VERDEF, VERNEED, UNRESOLVED, SPECIAL }

    private final String name;
    private final long value;
    private final long size;
    private final int bind;
    private final int type;
    private final int visibility;
    private final int shndx;
    private final String sectionName;
    private final boolean dynamic;
    private final int versionIndex;
    private final String versionName;
    private final VersionSource versionSource;
    private final boolean versionHiddenBit;
    private final boolean defaultVersion;
    private final boolean hiddenVersionNode;

    public ElfSymbol(String name, long value, long size, int bind, int type, int visibility,
                     int shndx, String sectionName, boolean dynamic,
                     int versionIndex, String versionName, VersionSource versionSource,
                     boolean versionHiddenBit, boolean defaultVersion, boolean hiddenVersionNode) {
        this.name = name;
        this.value = value;
        this.size = size;
        this.bind = bind;
        this.type = type;
        this.visibility = visibility;
        this.shndx = shndx;
        this.sectionName = sectionName;
        this.dynamic = dynamic;
        this.versionIndex = versionIndex;
        this.versionName = versionName;
        this.versionSource = versionSource;
        this.versionHiddenBit = versionHiddenBit;
        this.defaultVersion = defaultVersion;
        this.hiddenVersionNode = hiddenVersionNode;
    }

    public String getName() { return name; }
    public long getValue() { return value; }
    public long getSize() { return size; }
    public int getBind() { return bind; }
    public int getType() { return type; }
    public int getVisibility() { return visibility; }
    public int getShndx() { return shndx; }
    public String getSectionName() { return sectionName; }
    public boolean isDynamic() { return dynamic; }
    public int getVersionIndex() { return versionIndex; }
    public String getVersionName() { return versionName; }
    public VersionSource getVersionSource() { return versionSource; }
    public boolean isVersionHiddenBit() { return versionHiddenBit; }
    public boolean isDefaultVersion() { return defaultVersion; }
    public boolean isHiddenVersionNode() { return hiddenVersionNode; }

    public boolean isDefined() {
        return shndx != ElfConstants.SHN_UNDEF;
    }

    public boolean isCommon() {
        return shndx == ElfConstants.SHN_COMMON || type == ElfConstants.STT_COMMON;
    }

    public boolean isLocal() {
        return bind == ElfConstants.STB_LOCAL;
    }

    public boolean isWeak() {
        return bind == ElfConstants.STB_WEAK;
    }

    public boolean isHiddenVisibility() {
        return visibility == ElfConstants.STV_HIDDEN || visibility == ElfConstants.STV_INTERNAL;
    }

    public boolean isProtectedVisibility() {
        return visibility == ElfConstants.STV_PROTECTED;
    }

    /** Version label as shown in readelf output, e.g. {@code foo@@VER_2}. */
    public String decoratedName() {
        if (versionName == null) {
            return name;
        }
        String sep = defaultVersion ? "@@" : "@";
        return name + sep + versionName;
    }
}
