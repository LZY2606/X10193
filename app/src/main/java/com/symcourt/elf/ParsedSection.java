package com.symcourt.elf;

public final class ParsedSection {
    public final int index;
    public String name;
    public final int type;
    public final long flags;
    public final long address;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long alignment;
    public final long entrySize;

    ParsedSection(int index, String name, int type, long flags, long address, long offset,
                  long size, int link, int info, long alignment, long entrySize) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.address = address;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.alignment = alignment;
        this.entrySize = entrySize;
    }

    void setName(String name) {
        this.name = name;
    }

    public boolean allocated() {
        return (flags & ElfConstants.SHF_ALLOC) != 0;
    }
}
