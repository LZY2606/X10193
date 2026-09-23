package com.tribunal.elf;

public class ElfRelocation {
    public String section;      // section the relocation applies to
    public long offset;
    public long type;
    public int symIndex;
    public String symbolName;   // resolved from the linked symbol table
    public long addend;
    public boolean rela;
    public String sourceTable;  // symtab / dynsym
}
