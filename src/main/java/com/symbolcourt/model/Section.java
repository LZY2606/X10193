package com.symbolcourt.model;

/** ELF section header 的裁决相关子集。 */
public class Section {
    public int index;
    public String name;
    public int type;
    public long flags;
    public long addr;
    public long offset;
    public long size;
    public int link;
    public int info;
    public long align;
    public long entsize;
    public int groupIndex = -1;

    public boolean allocated() {
        return (flags & com.symbolcourt.elf.Elf.SHF_ALLOC) != 0;
    }

    public boolean inGroup() {
        return groupIndex >= 0;
    }
}
