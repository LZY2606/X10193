package com.court.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One parsed binary participant: an ELF object / shared object / executable,
 * or a member extracted from an archive.
 */
public class ElfFile {
    public enum Role { EXTRACTED_OBJECT, INPUT_OBJECT, SHARED, EXECUTABLE }

    private final String sourcePath;
    private final String memberName;
    private Role role;
    private boolean active;
    private boolean executable;
    private boolean shared;
    private boolean relocatable;
    private int machine;
    private long entry;

    private final List<ElfSection> sections = new ArrayList<>();
    private final List<ElfSymbol> staticSymbols = new ArrayList<>();
    private final List<ElfSymbol> dynamicSymbols = new ArrayList<>();
    private final List<Relocation> relocations = new ArrayList<>();
    private final List<VersionDef> versionDefs = new ArrayList<>();
    private final List<VersionReq> versionReqs = new ArrayList<>();
    private final List<SectionGroup> groups = new ArrayList<>();
    private final List<String> needed = new ArrayList<>();
    private String soname;

    /** versym entries aligned with dynamicSymbols: version index (0 = unversioned). */
    private final List<Integer> dynVersym = new ArrayList<>();
    /** Symbols discarded by COMDAT de-duplication (keyed globally later). */
    private final List<ElfSymbol> comdatDiscardedSymbols = new ArrayList<>();
    private final List<Relocation> comdatDiscardedRelocs = new ArrayList<>();

    public ElfFile(String sourcePath, String memberName) {
        this.sourcePath = sourcePath;
        this.memberName = memberName;
    }

    public String id() { return memberName == null ? sourcePath : sourcePath + "(" + memberName + ")"; }
    public String getSourcePath() { return sourcePath; }
    public String getMemberName() { return memberName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isExecutable() { return executable; }
    public void setExecutable(boolean executable) { this.executable = executable; }
    public boolean isShared() { return shared; }
    public void setShared(boolean shared) { this.shared = shared; }
    public boolean isRelocatable() { return relocatable; }
    public void setRelocatable(boolean relocatable) { this.relocatable = relocatable; }
    public int getMachine() { return machine; }
    public void setMachine(int machine) { this.machine = machine; }
    public long getEntry() { return entry; }
    public void setEntry(long entry) { this.entry = entry; }
    public List<ElfSection> getSections() { return sections; }
    public List<ElfSymbol> getStaticSymbols() { return staticSymbols; }
    public List<ElfSymbol> getDynamicSymbols() { return dynamicSymbols; }
    public List<Relocation> getRelocations() { return relocations; }
    public List<VersionDef> getVersionDefs() { return versionDefs; }
    public List<VersionReq> getVersionReqs() { return versionReqs; }
    public List<SectionGroup> getGroups() { return groups; }
    public List<String> getNeeded() { return needed; }
    public String getSoname() { return soname; }
    public void setSoname(String soname) { this.soname = soname; }
    public List<Integer> getDynVersym() { return dynVersym; }
    public List<ElfSymbol> getComdatDiscardedSymbols() { return comdatDiscardedSymbols; }
    public List<Relocation> getComdatDiscardedRelocs() { return comdatDiscardedRelocs; }

    public boolean isDynamic() { return shared || executable; }
}
