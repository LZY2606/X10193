package com.example.tribunal.elf;

/**
 * One ELF section header.
 */
public record ElfSection(
        int index,
        String name,
        int type,
        long flags,
        long address,
        long offset,
        long size,
        long link,
        long info,
        long alignment,
        long entrySize) {

    public static final int SHT_NULL = 0;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final long SHF_ALLOC = 0x2L;

    public boolean isSymbolTable() {
        return type == SHT_SYMTAB || type == SHT_DYNSYM;
    }

    public boolean isRelocation() {
        return type == SHT_REL || type == SHT_RELA;
    }
}
