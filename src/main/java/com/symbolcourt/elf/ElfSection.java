package com.symbolcourt.elf;

public final class ElfSection {
    public final int index;
    public final String name;
    public final int type;
    public final long flags;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long entsize;
    /** True when this section was discarded by COMDAT group deduplication. */
    public volatile boolean discarded;

    public ElfSection(int index, String name, int type, long flags, long offset, long size,
                      int link, int info, long entsize) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.entsize = entsize;
    }
}
