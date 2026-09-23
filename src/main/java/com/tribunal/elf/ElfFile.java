package com.tribunal.elf;

import java.util.ArrayList;
import java.util.List;

public class ElfFile {
    public static final int ET_REL = 1, ET_EXEC = 2, ET_DYN = 3;

    public int elfClass;          // 1 = 32-bit, 2 = 64-bit
    public boolean littleEndian;
    public int elfType;
    public int machine;
    public List<ElfSection> sections = new ArrayList<>();
    public List<ElfSymbol> symbols = new ArrayList<>();     // symtab + dynsym, see ElfSymbol.table
    public List<ElfRelocation> relocations = new ArrayList<>();
    public List<VersionDef> versionDefs = new ArrayList<>();
    public List<VersionNeed> versionNeeds = new ArrayList<>();
    public List<GroupInfo> groups = new ArrayList<>();
    public List<String> needed = new ArrayList<>();
    public String soname;

    public boolean isShared() {
        return elfType == ET_DYN;
    }

    public String typeName() {
        return switch (elfType) {
            case ET_REL -> "REL(object)";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN(shared)";
            default -> "TYPE#" + elfType;
        };
    }
}
