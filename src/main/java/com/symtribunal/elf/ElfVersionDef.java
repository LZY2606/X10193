package com.symtribunal.elf;

import java.util.ArrayList;
import java.util.List;

public class ElfVersionDef {
    public int index;
    public long flags;
    public String name = "";
    public List<String> aliases = new ArrayList<>();

    public boolean defaultVersion() {
        return (flags & Elf.VERSYM_HIDDEN) == 0;
    }
}
