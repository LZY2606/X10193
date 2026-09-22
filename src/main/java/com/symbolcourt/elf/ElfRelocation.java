package com.symbolcourt.elf;

public final class ElfRelocation {
    private final long offset;
    private final int symbolIndex;
    private final int type;
    private final long addend;
    private final boolean hasAddend;
    private final String sectionName;
    private final boolean copyRelocation;
    private final String symbolName;

    public ElfRelocation(long offset, int symbolIndex, int type, long addend, boolean hasAddend,
                         String sectionName, boolean copyRelocation, String symbolName) {
        this.offset = offset;
        this.symbolIndex = symbolIndex;
        this.type = type;
        this.addend = addend;
        this.hasAddend = hasAddend;
        this.sectionName = sectionName;
        this.copyRelocation = copyRelocation;
        this.symbolName = symbolName;
    }

    public long getOffset() { return offset; }
    public int getSymbolIndex() { return symbolIndex; }
    public int getType() { return type; }
    public long getAddend() { return addend; }
    public boolean isHasAddend() { return hasAddend; }
    public String getSectionName() { return sectionName; }
    public boolean isCopyRelocation() { return copyRelocation; }
    public String getSymbolName() { return symbolName; }
}
