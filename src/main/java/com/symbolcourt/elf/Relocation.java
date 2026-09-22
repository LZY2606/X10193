package com.symbolcourt.elf;

public final class Relocation {
    public final long offset;
    public final int type;
    public final String typeName;
    public final long symbolIndex;
    public final String symbolName;
    public final String sectionName;
    public final long addend;
    public final boolean relative; // R_*_RELATIVE style / non-symbol
    public final boolean copyLike;

    public Relocation(long offset, int type, String typeName, long symbolIndex,
                      String symbolName, String sectionName, long addend,
                      boolean relative, boolean copyLike) {
        this.offset = offset;
        this.type = type;
        this.typeName = typeName;
        this.symbolIndex = symbolIndex;
        this.symbolName = symbolName;
        this.sectionName = sectionName;
        this.addend = addend;
        this.relative = relative;
        this.copyLike = copyLike;
    }
}
