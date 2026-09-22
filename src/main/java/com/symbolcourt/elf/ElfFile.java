package com.symbolcourt.elf;

import java.util.List;

public class ElfFile {
    public final boolean elf64;
    public final boolean bigEndian;
    public final int machine;
    public final int type;
    public final int flags;
    public final long entry;
    public final List<ElfSection> sections;
    public final List<ElfSymbol> staticSymbols;
    public final List<ElfSymbol> dynamicSymbols;
    public final List<ElfRelocation> relocations;
    public final List<VersionDef> versionDefs;
    public final List<VersionNeed> versionNeeds;
    public final List<Integer> staticVersions;
    public final List<Integer> dynamicVersions;
    public final List<String> needed;
    public final String soname;
    public final String sourceName;

    public ElfFile(boolean elf64, boolean bigEndian, int machine, int type, int flags, long entry,
                   List<ElfSection> sections, List<ElfSymbol> staticSymbols, List<ElfSymbol> dynamicSymbols,
                   List<ElfRelocation> relocations, List<VersionDef> versionDefs, List<VersionNeed> versionNeeds,
                   List<Integer> staticVersions, List<Integer> dynamicVersions, List<String> needed,
                   String soname, String sourceName) {
        this.elf64 = elf64;
        this.bigEndian = bigEndian;
        this.machine = machine;
        this.type = type;
        this.flags = flags;
        this.entry = entry;
        sections = sections;
        this.staticSymbols = staticSymbols;
        this.dynamicSymbols = dynamicSymbols;
        this.relocations = relocations;
        this.versionDefs = versionDefs;
        this.versionNeeds = versionNeeds;
        this.staticVersions = staticVersions;
        this.dynamicVersions = dynamicVersions;
        this.needed = needed;
        this.soname = soname;
        this.sourceName = sourceName;
    }

    public ElfSection section(int index) {
        if (index < 0 || index >= sections.size()) {
            return null;
        }
        ElfSection section = sections.get(index);
        return section.type == Constants.SHT_NULL ? null : section;
    }

    public String sectionName(int index) {
        ElfSection section = section(index);
        return section == null ? null : section.name;
    }
}
