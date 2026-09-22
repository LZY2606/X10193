package com.symcourt.elf;

public final class ElfRelocation {
    public final int sectionIndex;
    public final long offset;
    public final int type;
    public final long symbolIndex;
    public final long addend; // 0 for SHT_REL
    public final boolean rela;
    public final boolean dynamicTable;

    ElfRelocation(int sectionIndex, long offset, int type, long symbolIndex,
                  long addend, boolean rela, boolean dynamicTable) {
        this.sectionIndex = sectionIndex;
        this.offset = offset;
        this.type = type;
        this.symbolIndex = symbolIndex;
        this.addend = addend;
        this.rela = rela;
        this.dynamicTable = dynamicTable;
    }
}
