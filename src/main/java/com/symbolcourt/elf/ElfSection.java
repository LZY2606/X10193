package com.symbolcourt.elf;

public final class ElfSection {
    private final String name;
    private final int index;
    private final int type;
    private final long flags;
    private final long addr;
    private final long offset;
    private final long size;
    private final int link;
    private final int info;
    private final long alignment;
    private final long entrySize;

    public ElfSection(String name, int index, int type, long flags, long addr, long offset,
                      long size, int link, int info, long alignment, long entrySize) {
        this.name = name;
        this.index = index;
        this.type = type;
        this.flags = flags;
        this.addr = addr;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.alignment = alignment;
        this.entrySize = entrySize;
    }

    public String getName() { return name; }
    public int getIndex() { return index; }
    public int getType() { return type; }
    public long getFlags() { return flags; }
    public long getAddr() { return addr; }
    public long getOffset() { return offset; }
    public long getSize() { return size; }
    public int getLink() { return link; }
    public int getInfo() { return info; }
    public long getAlignment() { return alignment; }
    public long getEntrySize() { return entrySize; }

    public boolean isAllocated() {
        return (flags & ElfConstants.SHF_ALLOC) != 0;
    }
}
