package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** A fully parsed ELF object (relocatable, executable or shared object). */
public final class ElfObject {
    public final String name;
    public final int elfClass; // 1 = 32-bit, 2 = 64-bit
    public final int type;     // ET_*
    public final int machine;
    public final List<ElfSection> sections = new ArrayList<>();
    public final List<ElfSymbol> symtab = new ArrayList<>();
    public final List<ElfSymbol> dynsym = new ArrayList<>();
    public final List<ElfRelocation> relocations = new ArrayList<>();
    public final List<VersionDef> versionDefs = new ArrayList<>();
    public final List<VersionNeed> versionNeeds = new ArrayList<>();
    public final List<SectionGroup> groups = new ArrayList<>();

    public ElfObject(String name, int elfClass, int type, int machine) {
        this.name = name;
        this.elfClass = elfClass;
        this.type = type;
        this.machine = machine;
    }

    public String typeName() {
        return switch (type) {
            case Elf.ET_REL -> "ET_REL";
            case Elf.ET_EXEC -> "ET_EXEC";
            case Elf.ET_DYN -> "ET_DYN";
            default -> "type#" + type;
        };
    }

    /** Symbols visible to the adjudicator: symtab when present, else dynsym. */
    public List<ElfSymbol> linkSymbols() {
        return symtab.isEmpty() ? dynsym : symtab;
    }

    /** Exported (dynamic) symbols for shared objects. */
    public List<ElfSymbol> dynamicSymbols() {
        return dynsym.isEmpty() ? symtab : dynsym;
    }

    public ElfSection section(int index) {
        if (index < 0 || index >= sections.size()) {
            return null;
        }
        return sections.get(index);
    }

    public static final class SectionGroup {
        public final String signature;
        public final boolean comdat;
        public final List<Integer> memberSections;

        public SectionGroup(String signature, boolean comdat, List<Integer> memberSections) {
            this.signature = signature;
            this.comdat = comdat;
            this.memberSections = memberSections;
        }
    }
}
