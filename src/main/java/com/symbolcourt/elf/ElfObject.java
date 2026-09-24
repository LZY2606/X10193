package com.symbolcourt.elf;

import java.util.List;

/** Fully parsed, immutable view of one ELF object (ET_REL or ET_DYN/EXEC). */
public class ElfObject {

    public enum Kind { RELOCATABLE, SHARED, EXECUTABLE }

    private final String displayName;
    private final boolean elf64;
    private final int machine;
    private final Kind kind;
    private final List<SectionInfo> sections;
    private final List<SymbolInfo> staticSymbols;
    private final List<SymbolInfo> dynamicSymbols;
    private final List<RelocInfo> relocations;
    private final List<VersionDefinition> versionDefinitions;
    private final List<VersionNeed> versionNeeds;
    private final List<SectionGroup> groups;
    private final List<String> needed;
    private final String soname;
    private final boolean symbolic;

    public ElfObject(String displayName, boolean elf64, int machine, Kind kind,
                     List<SectionInfo> sections,
                     List<SymbolInfo> staticSymbols,
                     List<SymbolInfo> dynamicSymbols,
                     List<RelocInfo> relocations,
                     List<VersionDefinition> versionDefinitions,
                     List<VersionNeed> versionNeeds,
                     List<SectionGroup> groups,
                     List<String> needed, String soname, boolean symbolic) {
        this.displayName = displayName;
        this.elf64 = elf64;
        this.machine = machine;
        this.kind = kind;
        this.sections = List.copyOf(sections);
        this.staticSymbols = List.copyOf(staticSymbols);
        this.dynamicSymbols = List.copyOf(dynamicSymbols);
        this.relocations = List.copyOf(relocations);
        this.versionDefinitions = List.copyOf(versionDefinitions);
        this.versionNeeds = List.copyOf(versionNeeds);
        this.groups = List.copyOf(groups);
        this.needed = List.copyOf(needed);
        this.soname = soname;
        this.symbolic = symbolic;
    }

    public String displayName() { return displayName; }
    public boolean elf64() { return elf64; }
    public int machine() { return machine; }
    public Kind kind() { return kind; }
    public List<SectionInfo> sections() { return sections; }
    public List<SymbolInfo> staticSymbols() { return staticSymbols; }
    public List<SymbolInfo> dynamicSymbols() { return dynamicSymbols; }
    public List<RelocInfo> relocations() { return relocations; }
    public List<VersionDefinition> versionDefinitions() { return versionDefinitions; }
    public List<VersionNeed> versionNeeds() { return versionNeeds; }
    public List<SectionGroup> groups() { return groups; }
    public List<String> needed() { return needed; }
    public String soname() { return soname; }
    public boolean symbolic() { return symbolic; }

    public List<SymbolInfo> allSymbols() {
        java.util.List<SymbolInfo> all = new java.util.ArrayList<>(staticSymbols);
        all.addAll(dynamicSymbols);
        return all;
    }
}
