package com.symbolcourt.elf;

/**
 * One entry of {@code .symtab} or {@code .dynsym}, after name and version
 * resolution. {@code sectionIndex} keeps the raw st_shndx value, including
 * SHN_UNDEF (0), SHN_ABS (0xfff1), SHN_COMMON (0xfff2) and extended indices.
 */
public record SymbolInfo(
        int index,
        String name,
        int binding,       // STB_*
        int type,          // STT_*
        int visibility,    // STV_*
        int sectionIndex,
        long value,
        long size,
        boolean dynamic,  // came from .dynsym
        int versionIndex, // raw Versym value: 0/1 local/global, >1 named, 0x8000 hidden
        String versionName // resolved version name ("" for none, "@" marks default internally)
) {
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public boolean undefined() {
        return sectionIndex == SHN_UNDEF;
    }

    public boolean common() {
        return sectionIndex == SHN_COMMON;
    }

    public boolean hiddenVersion() {
        return (versionIndex & 0x8000) != 0;
    }

    public int plainVersionIndex() {
        return versionIndex & 0x7fff;
    }
}
