package com.symbolcourt.model;

/** 一条版本需求：需要某个版本名（vernaux 可能带别名）。 */
public class VersionNeed {
    public int index;
    public String file;
    /** vernaux 名称（同名多个 aux 视为别名）。 */
    public java.util.List<String> names = new ArrayList<>();
    public int flags;
    public int hash;

    public boolean weak() {
        return (flags & com.symbolcourt.elf.Elf.VER_FLG_WEAK) != 0;
    }
}
