package com.symbolcourt.elf;

public class ElfRelocation {
    public final long offset;
    public final long type;
    public final int symbolIndex;
    public final long addend;
    public final boolean hasAddend;
    public final int sectionIndex;
    public final int targetSectionIndex;
    public final boolean copy;
    public final boolean pltBinder;

    public ElfRelocation(long offset, long type, int symbolIndex, long addend, boolean hasAddend,
                         int sectionIndex, int targetSectionIndex, boolean copy, boolean pltBinder) {
        this.offset = offset;
        this.type = type;
        this.symbolIndex = symbolIndex;
        this.addend = addend;
        this.hasAddend = hasAddend;
        this.sectionIndex = sectionIndex;
        this.targetSectionIndex = targetSectionIndex;
        this.copy = copy;
        this.pltBinder = pltBinder;
    }
}
