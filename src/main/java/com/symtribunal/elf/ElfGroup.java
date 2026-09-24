package com.symtribunal.elf;

import java.util.ArrayList;
import java.util.List;

public class ElfGroup {
    public int sectionIndex;
    public long flags;
    public int signatureSymbolIndex;
    public String signature = "";
    public boolean comdat;
    public List<Integer> memberSections = new ArrayList<>();
}
