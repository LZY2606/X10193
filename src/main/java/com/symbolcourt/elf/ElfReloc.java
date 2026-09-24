package com.symbolcourt.elf;

public class ElfReloc {
    public final String sectionName;
    public final int targetSection;
    public final long offset;
    public final long type;
    public final int symIndex;
    public final long addend;
    public final boolean rela;
    /** Resolved against the linked symbol table; null for index 0. */
    public String symbolName;
    public boolean dynamicSymbols;

    public ElfReloc(String sectionName, int targetSection, long offset, long type,
                    int symIndex, long addend, boolean rela) {
        this.sectionName = sectionName;
        this.targetSection = targetSection;
        this.offset = offset;
        this.type = type;
        this.symIndex = symIndex;
        this.addend = addend;
        this.rela = rela;
    }
}
