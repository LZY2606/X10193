package com.symbolcourt.elf;

public final class ElfRelocation {
    public final String section;
    public final long offset;
    public final int symIndex;
    public final int type;
    public final long addend;
    public final boolean rela;
    /** Symbol name resolved against the linked symbol table, or null. */
    public final String symbolName;

    public ElfRelocation(String section, long offset, int symIndex, int type, long addend,
                         boolean rela, String symbolName) {
        this.section = section;
        this.offset = offset;
        this.symIndex = symIndex;
        this.type = type;
        this.addend = addend;
        this.rela = rela;
        this.symbolName = symbolName;
    }
}
