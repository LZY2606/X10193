package com.symbolcourt.model;

/**
 * 单个符号（symtab/dynsym 合并视图）。
 */
public class Sym {
    public int index;
    public String name;
    public int bind;
    public int type;
    public int visibility;
    public int shndx;
    public long value;
    public long size;
    public boolean defined;
    public boolean common;
    public boolean dynamic;

    /** .gnu.versym 中的原始版本索引（含 0x8000 隐藏位）；未参与版本控制为 -1。 */
    public int versym = -1;

    @Override
    public String toString() {
        return name + "[" + com.symbolcourt.elf.Elf.bindName(bind) + "]";
    }
}
