package com.symbolcourt.elf;

public class ElfSection {
    public final String name;
    public final int nameIndex;
    public final int type;
    public final long flags;
    public final long address;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long addralign;
    public final long entsize;

    public ElfSection(String name, int nameIndex, int type, long flags, long address, long offset, long size,
                      int link, int info, long addralign, long entsize) {
        this.name = name;
        this.nameIndex = nameIndex;
        this.type = type;
        this.flags = flags;
        this.address = address;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.addralign = addralign;
        this.entsize = entsize;
    }
}
