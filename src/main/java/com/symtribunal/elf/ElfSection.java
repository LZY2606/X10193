package com.symtribunal.elf;

public class ElfSection {
    public int index;
    public String name = "";
    public int nameOffset;
    public int type;
    public long flags;
    public long address;
    public long offset;
    public long size;
    public int link;
    public int info;
    public long addralign;
    public long entsize;

    public boolean allocated() {
        return (flags & Elf.SHF_ALLOC) != 0;
    }
}
