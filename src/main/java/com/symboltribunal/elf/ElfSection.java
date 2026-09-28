package com.symboltribunal.elf;

public class ElfSection {
    public final int index;
    public final String name;
    public final int type;
    public final long flags;
    public final long addr;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long addralign;
    public final long entsize;

    public ElfSection(int index, String name, int type, long flags, long addr, long offset, long size,
                      int link, int info, long addralign, long entsize) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.addr = addr;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.addralign = addralign;
        this.entsize = entsize;
    }

    @Override
    public String toString() {
        return index + ":" + name;
    }
}
