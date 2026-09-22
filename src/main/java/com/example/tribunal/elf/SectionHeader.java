package com.example.tribunal.elf;

record SectionHeader {
    final int nameOffset;
    final int type;
    final long flags;
    final long address;
    final long offset;
    final long size;
    final int link;
    final int info;
    final long alignment;
    final long entrySize;

    SectionHeader(int nameOffset, int type, long flags, long address, long offset, long size,
                  int link, int info, long alignment, long entrySize) {
        this.nameOffset = nameOffset;
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
}
