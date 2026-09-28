package com.symboltribunal.elf;

public class Relocation {
    /** Section in which the relocation slot lives. */
    public final String section;
    public final int sectionIndex;
    public final long offset;
    public final long type;
    public final int symIndex;
    public final String symbolName;
    public final long addend;
    public final boolean rela;
    /** True for R_*_COPY dynamic relocations. */
    public boolean copy;
    /** True for R_*_GLOB_DAT / JUMP_SLOT / IRELATIVE-style dynamic slots. */
    public final boolean dynamicSlot;

    public Relocation(String section, int sectionIndex, long offset, long type, int symIndex,
                      String symbolName, long addend, boolean rela, boolean dynamicSlot) {
        this.section = section;
        this.sectionIndex = sectionIndex;
        this.offset = offset;
        this.type = type;
        this.symIndex = symIndex;
        this.symbolName = symbolName;
        this.addend = addend;
        this.rela = rela;
        this.dynamicSlot = dynamicSlot;
    }

    public String typeLabel() {
        return "0x" + Long.toHexString(type);
    }
}
