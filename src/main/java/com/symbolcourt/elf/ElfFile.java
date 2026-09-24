package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

public class ElfFile {
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public int elfClass;
    public int elfType;
    public int machine;
    public List<ElfSection> sections = new ArrayList<>();
    public List<ElfSymbol> symbols = new ArrayList();
    public List<ElfSymbol> dynSymbols = new ArrayList();
    public List<ElfReloc> relocations = new ArrayList();
    public List<VerDef> verDefs = new ArrayList();
    public List<VerNeed> verNeeds = new ArrayList();
    public List<GroupInfo> groups = new ArrayList();

    public String typeName() {
        return switch (elfType) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            default -> "TYPE#" + elfType;
        };
    }

    public String sectionName(int shndx) {
        if (shndx == ElfSymbol.SHN_ABS) return "ABS";
        if (shndx == ElfSymbol.SHN_COMMON) return "COMMON";
        if (shndx >= 0 && shndx < sections.size()) return sections.get(shndx).name;
        return "#" + shndx;
    }
}
