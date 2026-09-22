package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fully parsed view of one ELF object/shared object. */
public final class ElfFile {

    private final boolean elf64;
    private final boolean littleEndian;
    private final int machine;
    private final int type;
    private String soname;
    private final List<String> needed = new ArrayList<>();
    private final List<ElfSection> sections = new ArrayList<>();
    private final List<ElfSymbol> staticSymbols = new ArrayList<>();
    private final List<ElfSymbol> dynamicSymbols = new ArrayList<>();
    private final List<ElfRelocation> relocations = new ArrayList<>();
    private final List<VersionDefinition> versionDefinitions = new ArrayList<>();
    private final List<VersionRequirement> versionRequirements = new ArrayList<>();
    private final List<SectionGroup> groups = new ArrayList<>();
    private final Map<String, String> parseWarnings = new LinkedHashMap<>();

    void setSoname(String soname) {
        this.soname = soname;
    }

    public ElfFile(boolean elf64, boolean littleEndian, int machine, int type, String soname) {
        this.elf64 = elf64;
        this.littleEndian = littleEndian;
        this.machine = machine;
        this.type = type;
        this.soname = soname;
    }

    public boolean isElf64() { return elf64; }
    public boolean isLittleEndian() { return littleEndian; }
    public int getMachine() { return machine; }
    public int getType() { return type; }
    public String getSoname() { return soname; }
    public List<String> getNeeded() { return needed; }
    public List<ElfSection> getSections() { return sections; }
    public List<ElfSymbol> getStaticSymbols() { return staticSymbols; }
    public List<ElfSymbol> getDynamicSymbols() { return dynamicSymbols; }
    public List<ElfRelocation> getRelocations() { return relocations; }
    public List<VersionDefinition> getVersionDefinitions() { return versionDefinitions; }
    public List<VersionRequirement> getVersionRequirements() { return versionRequirements; }
    public List<SectionGroup> getGroups() { return groups; }
    public Map<String, String> getParseWarnings() { return parseWarnings; }

    public ElfSection sectionByIndex(int idx) {
        return (idx >= 0 && idx < sections.size()) ? sections.get(idx) : null;
    }

    public void addWarning(String key, String message) {
        parseWarnings.putIfAbsent(key, message);
    }
}
