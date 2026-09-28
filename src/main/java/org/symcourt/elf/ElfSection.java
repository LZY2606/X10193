package org.symcourt.elf;

/** Section header as parsed from an ELF object. */
public final class ElfSection {
    public final int index;
    public final String name;
    public final int type;
    public final long flags;
    public final long addr;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long alignment;
    public final long entrySize;
    /** Group this section belongs to (COMDAT), or {@code null}. */
    public Integer groupIndex;

    public ElfSection(int index, String name, int type, long flags, long addr,
                      long offset, long size, int link, int info,
                      long alignment, long entrySize) {
        this.index = index;
        this.name = name;
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

    public boolean alloc() {
        return (flags & Elf.SHF_ALLOC) != 0;
    }

    public String shortDesc() {
        return name + "(#" + index + ",sh_type=0x" + Integer.toHexString(type) + ")";
    }
}
