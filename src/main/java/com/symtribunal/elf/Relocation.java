package com.symtribunal.elf;

/** A single REL/RELA relocation with its referenced symbol index. */
public final class Relocation {
    private final long offset;
    private final int type;
    private final int symbolIndex;
    private final long addend;
    private final boolean rela;
    private final String applySection;

    public Relocation(long offset, int type, int symbolIndex, long addend,
                      boolean rela, String applySection) {
        this.offset = offset;
        this.type = type;
        this.symbolIndex = symbolIndex;
        this.addend = addend;
        this.rela = rela;
        this.applySection = applySection;
    }

    public long offset() {
        return offset;
    }

    public int type() {
        return type;
    }

    public int symbolIndex() {
        return symbolIndex;
    }

    public long addend() {
        return addend;
    }

    public boolean rela() {
        return rela;
    }

    public String applySection() {
        return applySection;
    }

    public boolean copy() {
        return type == ElfConstants.R_X86_64_COPY || type == ElfConstants.R_386_COPY;
    }
}
