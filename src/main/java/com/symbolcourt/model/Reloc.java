package com.symbolcourt.model;

/** 重定位条目（REL 或 RELA）。 */
public class Reloc {
    public long offset;
    public long addend;
    public boolean rela;
    public int type;
    public int symIndex;
    public String symName;
    public String typeName;
    public String sectionName;
    public boolean allocated;
}
