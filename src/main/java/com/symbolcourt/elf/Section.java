package com.symbolcourt.elf;

public final class Section {
    public String name;
    public final long nameOffset;
    public final int type;
    public final long flags;
    public final long addr;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long alignment;
    public final long entrySize;
    public final int index;

    public Section(long nameOffset, int type, long flags, long addr, long offset, long size,
                   int link, int info, long alignment, long entrySize, int index) {
        this.nameOffset = nameOffset;
        this.type = type;
        this.flags = flags;
        this.addr = addr;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.alignment = alignment;
        this.entrySize = entrySize;
        this.index = index;
    }

    public boolean isGroup() {
        return type == 17; // SHT_GROUP
    }
}
