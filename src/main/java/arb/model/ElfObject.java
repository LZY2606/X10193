package arb.model;

import java.util.ArrayList;
import java.util.List;

public class ElfObject {
    private String name;
    private boolean elf64;
    private boolean littleEndian;
    private int machine;
    private int type;
    private List<String> sectionNames = new ArrayList<>();
    private List<SymbolDef> symbols = new ArrayList<>();
    private List<SymbolDef> dynamicSymbols = new ArrayList<>();
    private List<Reloc> relocations = new ArrayList<>();
    private List<SectionGroup> groups = new ArrayList<>();
    private List<VersionInfo.Def> versionDefs = new ArrayList<>();
    private List<VersionInfo.Need> versionNeeds = new ArrayList<>();
    private List<String> needed = new ArrayList<>();
    private String soname;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isElf64() {
        return elf64;
    }

    public void setElf64(boolean elf64) {
        this.elf64 = elf64;
    }

    public boolean isLittleEndian() {
        return littleEndian;
    }

    public void setLittleEndian(boolean littleEndian) {
        this.littleEndian = littleEndian;
    }

    public int getMachine() {
        return machine;
    }

    public void setMachine(int machine) {
        this.machine = machine;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public List<String> getSectionNames() {
        return sectionNames;
    }

    public void setSectionNames(List<String> sectionNames) {
        this.sectionNames = sectionNames;
    }

    public List<SymbolDef> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<SymbolDef> symbols) {
        this.symbols = symbols;
    }

    public List<SymbolDef> getDynamicSymbols() {
        return dynamicSymbols;
    }

    public void setDynamicSymbols(List<SymbolDef> dynamicSymbols) {
        this.dynamicSymbols = dynamicSymbols;
    }

    public List<Reloc> getRelocations() {
        return relocations;
    }

    public void setRelocations(List<Reloc> relocations) {
        this.relocations = relocations;
    }

    public List<SectionGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<SectionGroup> groups) {
        this.groups = groups;
    }

    public List<VersionInfo.Def> getVersionDefs() {
        return versionDefs;
    }

    public void setVersionDefs(List<VersionInfo.Def> versionDefs) {
        this.versionDefs = versionDefs;
    }

    public List<VersionInfo.Need> getVersionNeeds() {
        return versionNeeds;
    }

    public void setVersionNeeds(List<VersionInfo.Need> versionNeeds) {
        this.versionNeeds = versionNeeds;
    }

    public List<String> getNeeded() {
        return needed;
    }

    public void setNeeded(List<String> needed) {
        this.needed = needed;
    }

    public String getSoname() {
        return soname;
    }

    public void setSoname(String soname) {
        this.soname = soname;
    }

    public Enums.InputKind kind() {
        if (type == 1) {
            return Enums.InputKind.OBJECT;
        }
        if (type == 2) {
            return Enums.InputKind.SHARED;
        }
        if (type == 3) {
            return Enums.InputKind.EXEC;
        }
        return Enums.InputKind.OBJECT;
    }

    public String sectionName(int idx) {
        if (idx < 0 || idx >= sectionNames.size()) {
            return "sh" + idx;
        }
        return sectionNames.get(idx);
    }
}
