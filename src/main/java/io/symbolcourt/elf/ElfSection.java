package io.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** One section header plus resolved link/info helpers and COMDAT group data. */
public class ElfSection {
    public int index;
    public long headerNameOffset;
    public String name = "";
    public long type;
    public long flags;
    public long addr;
    public long offset;
    public long size;
    public int link;
    public int info;
    public long addralign;
    public long entsize;

    /** SHT_GROUP only: GRP_COMDAT flag plus member section header indices. */
    public boolean comdat;
    public List<Integer> groupMembers = new ArrayList<>();

    public boolean isStringTable() {
        return type == 3; // SHT_STRTAB
    }

    public boolean isSymbolTable() {
        return type == 2 || type == 11; // SHT_SYMTAB / SHT_DYNSYM
    }

    public boolean isDynamicSymbolTable() {
        return type == 11;
    }
}
