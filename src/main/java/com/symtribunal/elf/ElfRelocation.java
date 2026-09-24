package com.symtribunal.elf;

public class ElfRelocation {
    public long offset;
    public long info;
    public Long addend;
    public int symbolIndex;
    public int type;
    public int sectionIndex;
    public String symbolTable;
    public String symbolName = "";
    public int symbolBinding;
    public int symbolVisibility;
    public boolean symbolDefined;
    public String symbolVersion;
}
