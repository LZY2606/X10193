package io.symbolcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Result of successfully parsing one ELF binary (ET_REL or ET_DYN/EXEC). */
public class ElfImage {
    public boolean elf64;
    public boolean littleEndian = true;
    public int elfType;       // ET_REL=1 ET_EXEC=2 ET_DYN=3
    public int machine;
    public String machineName;
    public long entry;
    public long flags;

    public List<ElfSection> sections = new ArrayList<>();
    public List<ElfSymbol> symbols = new ArrayList<>();       // .symtab (relocatable)
    public List<ElfSymbol> dynamicSymbols = new ArrayList<>(); // .dynsym
    public List<Relocation> relocations = new ArrayList<>();
    public List<VersionDef> versionDefs = new ArrayList<>();
    public List<VersionNeed> versionNeeds = new ArrayList<>();
    public List<String> needed = new ArrayList<>();
    public String soname;
    public byte[] hash; // GNU_HASH presence marker not required

    public Map<Integer, VersionDef> versionDefByIndex = new LinkedHashMap<>();
    public Map<Integer, VersionNeed.Aux> versionNeedByIndex = new LinkedHashMap<>();

    public boolean relocatable() {
        return elfType == 1;
    }

    public boolean sharedOrExec() {
        return elfType == 2 || elfType == 3;
    }

    public ElfSection section(int idx) {
        if (idx < 0 || idx >= sections.size()) {
            return null;
        }
        return sections.get(idx);
    }
}
