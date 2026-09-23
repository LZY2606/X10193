package com.tribunal.elf;

import java.util.List;
import java.util.Map;

/** Full parse product for one ELF object or shared object. */
public final class ParsedElf {
    public final String objectName;
    public final int elfClass;       // 1 = 32-bit, 2 = 64-bit
    public final int machine;
    public final int type;           // ET_REL / ET_DYN / ET_EXEC
    public final List<ElfSection> sections;
    public final List<ElfSymbol> symbols;       // merged symtab + dynsym (dynsym first kept if overlap)
    public final List<ElfRelocation> relocations;
    public final Map<Integer, VersionTag> versionByIndex; // raw .gnu.version index -> tag
    public final List<ComdatGroup> groups;
    public final List<String> needed;             // DT_NEEDED sonames (best effort)
    public final String soname;
    public final boolean shared;

    public ParsedElf(String objectName, int elfClass, int machine, int type, boolean shared,
                     List<ElfSection> sections, List<ElfSymbol> symbols,
                     List<ElfRelocation> relocations, Map<Integer, VersionTag> versionByIndex,
                     List<ComdatGroup> groups, List<String> needed, String soname) {
        this.objectName = objectName;
        this.elfClass = elfClass;
        this.machine = machine;
        this.type = type;
        this.shared = shared;
        this.sections = sections;
        this.symbols = symbols;
        this.relocations = relocations;
        this.versionByIndex = versionByIndex;
        this.groups = groups;
        this.needed = needed;
        this.soname = soname;
    }
}
