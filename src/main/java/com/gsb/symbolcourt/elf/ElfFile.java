package com.gsb.symbolcourt.elf;

import java.util.List;
import java.util.Optional;

public class ElfFile {
    private final boolean elf64;
    private final boolean littleEndian;
    private final int machine;
    private final int type;
    private final List<ElfSection> sections;
    private final List<ElfSymbol> staticSymbols;
    private final List<ElfSymbol> dynamicSymbols;
    private final List<Relocation> staticRelocations;
    private final List<Relocation> dynamicRelocations;
    private final List<VersionInfo.VersionNode> versionDefinitions;
    private final List<VersionInfo.NeededVersion> versionNeeds;
    private final List<ComdatGroup> groups;
    private final List<String> neededLibraries;
    private final String soname;

    public ElfFile(boolean elf64, boolean littleEndian, int machine, int type,
                   List<ElfSection> sections,
                   List<ElfSymbol> staticSymbols,
                   List<ElfSymbol> dynamicSymbols,
                   List<Relocation> staticRelocations,
                   List<Relocation> dynamicRelocations,
                   List<VersionInfo.VersionNode> versionDefinitions,
                   List<VersionInfo.NeededVersion> versionNeeds,
                   List<ComdatGroup> groups,
                   List<String> neededLibraries,
                   String soname) {
        this.elf64 = elf64;
        this.littleEndian = littleEndian;
        this.machine = machine;
        this.type = type;
        this.sections = List.copyOf(sections);
        this.staticSymbols = List.copyOf(staticSymbols);
        this.dynamicSymbols = List.copyOf(dynamicSymbols);
        this.staticRelocations = List.copyOf(staticRelocations);
        this.dynamicRelocations = List.copyOf(dynamicRelocations);
        this.versionDefinitions = List.copyOf(versionDefinitions);
        this.versionNeeds = List.copyOf(versionNeeds);
        this.groups = List.copyOf(groups);
        this.neededLibraries = List.copyOf(neededLibraries);
        this.soname = soname;
    }

    public boolean isElf64() {
        return elf64;
    }

    public boolean isLittleEndian() {
        return littleEndian;
    }

    public int getMachine() {
        return machine;
    }

    public int getType() {
        return type;
    }

    public boolean isRelocatable() {
        return type == ElfConstants.ET_REL;
    }

    public boolean isSharedObject() {
        return type == ElfConstants.ET_DYN;
    }

    public boolean isExecutable() {
        return type == ET_EXEC;
    }

    private static final int ET_EXEC = ElfConstants.ET_EXEC;

    public List<ElfSection> getSections() {
        return sections;
    }

    public List<ElfSymbol> getStaticSymbols() {
        return staticSymbols;
    }

    public List<ElfSymbol> getDynamicSymbols() {
        return dynamicSymbols;
    }

    public List<Relocation> getStaticRelocations() {
        return staticRelocations;
    }

    public List<Relocation> getDynamicRelocations() {
        return dynamicRelocations;
    }

    public List<VersionInfo.VersionNode> getVersionDefinitions() {
        return versionDefinitions;
    }

    public List<VersionInfo.NeededVersion> getVersionNeeds() {
        return versionNeeds;
    }

    public List<ComdatGroup> getGroups() {
        return groups;
    }

    public List<String> getNeededLibraries() {
        return neededLibraries;
    }

    public Optional<String> getSoname() {
        return Optional.ofNullable(soname);
    }

    public ElfSection section(int index) {
        if (index < 0 || index >= sections.size()) {
            return null;
        }
        return sections.get(index);
    }

    public String typeName() {
        return switch (type) {
            case ElfConstants.ET_REL -> "REL";
            case ElfConstants.ET_EXEC -> "EXEC";
            case ElfConstants.ET_DYN -> "DYN";
            case ElfConstants.ET_CORE -> "CORE";
            default -> "ET_" + type;
        };
    }
}
