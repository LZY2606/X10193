package com.verdict.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed ELF object (relocatable, executable or shared). Immutable after parsing. */
public final class ElfFile {
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public String name;
    public boolean is64;
    public boolean littleEndian;
    public int elfType;
    public int machine;

    public final List<ElfSection> sections = new ArrayList<>();
    /** All symbols from SHT_SYMTAB (index 0 = null symbol). */
    public final List<ElfSymbol> symbols = new ArrayList<>();
    /** All symbols from SHT_DYNSYM (index 0 = null symbol). */
    public final List<ElfSymbol> dynsyms = new ArrayList<>();
    public final List<ElfRelocation> relocations = new ArrayList<>();
    public final List<String> neededLibraries = new ArrayList<>();
    public String soname;

    /** Defined version names: version index -> version name (from .gnu.version_d). */
    public final Map<Integer, String> versionDefs = new LinkedHashMap<>();
    /** Needed versions: version index -> version name (from .gnu.version_r). */
    public final Map<Integer, String> versionNeeds = new LinkedHashMap<>();
    /** Needed version -> library file that requires it. */
    public final Map<String, String> versionNeedFile = new LinkedHashMap<>();

    /** Section index -> group signature it belongs to (from SHT_GROUP contents). */
    public final Map<Integer, String> sectionGroup = new LinkedHashMap<>();

    public String typeName() {
        return switch (elfType) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            default -> "TYPE_" + elfType;
        };
    }

    public boolean isShared() {
        return elfType == ET_DYN;
    }

    public ElfSection section(int index) {
        if (index <= 0 || index >= sections.size()) {
            return null;
        }
        return sections.get(index);
    }

    public String sectionName(int index) {
        ElfSection s = section(index);
        return s == null ? "<bad:" + index + ">" : s.name;
    }
}
