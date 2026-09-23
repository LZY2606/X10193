package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

/** 一个 ELF 对象的解析结果。 */
public class ElfFile {
    public String name;
    public int elfClass;
    public int dataEncoding;
    public int type;
    public int machine;
    public long entry;
    public boolean littleEndian;

    public List<Section> sections = new ArrayList<>();
    public List<Sym> symbols = new ArrayList<>();
    public List<Reloc> relocations = new ArrayList<>();
    public List<VersionDef> versionDefs = new ArrayList<>();
    public List<VersionNeed> versionNeeds = new ArrayList<>();
    public List<Integer> groupSections = new ArrayList<>();
    public List<String> needed = new ArrayList<>();
    public String soname;

    /** shndx -> section 名（供裁决/展示）。 */
    public String sectionName(int shndx) {
        if (shndx >= 0 && shndx < sections.size()) {
            return sections.get(shndx).name;
        }
        if (shndx == com.symbolcourt.elf.Elf.SHN_ABS) return "*ABS*";
        if (shndx == com.symbolcourt.elf.Elf.SHN_COMMON) return "*COMMON*";
        return "*UNDEF*";
    }

    public Sym symbolAt(int idx) {
        for (Sym s : symbols) {
            if (s.index == idx && !s.dynamic) return s;
        }
        return null;
    }

    public Sym dynamicSymbolAt(int idx) {
        for (Sym s : symbols) {
            if (s.index == idx && s.dynamic) return s;
        }
        return null;
    }

    public String typeName() {
        switch (type) {
            case com.symbolcourt.elf.Elf.ET_REL: return "REL";
            case com.symbolcourt.elf.Elf.ET_EXEC: return "EXEC";
            case com.symbolcourt.elf.Elf.ET_DYN: return "DYN";
            default: return "ET_" + type;
        }
    }
}
