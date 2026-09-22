package com.symcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable result of parsing one ELF object (never a call out to a system linker). */
public final class ParsedElf {
    public final boolean elf64;
    public final boolean littleEndian;
    public final int machine;
    public final int type;
    public final List<ParsedSection> sections = new ArrayList<>();
    public final List<ElfSymbol> staticSymbols = new ArrayList<>();
    public final List<ElfSymbol> dynamicSymbols = new ArrayList<>();
    public final List<ElfRelocation> relocations = new ArrayList<>();
    public final List<ElfGroup> groups = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;
    /** version index (as found in .gnu.version) -> version name; 0/1 map to null. */
    public final Map<Integer, String> versionNames = new LinkedHashMap<>();
    /** version name -> true when it is the object's base/soname version definition. */
    public final Map<String, Boolean> definedBaseVersion = new LinkedHashMap<>();
    public final Map<Integer, Integer> dynamicVersionIndex = new LinkedHashMap<>();

    ParsedElf(boolean elf64, boolean littleEndian, int machine, int type) {
        this.elf64 = elf64;
        this.littleEndian = littleEndian;
        this.machine = machine;
        this.type = type;
    }

    public boolean isRelocatable() {
        return type == ElfConstants.ET_REL;
    }

    public boolean isSharedObject() {
        return type == ElfConstants.ET_DYN || type == ElfConstants.ET_EXEC;
    }

    public String typeName() {
        return switch (type) {
            case ElfConstants.ET_REL -> "REL";
            case ElfConstants.ET_EXEC -> "EXEC";
            case ElfConstants.ET_DYN -> "DYN";
            default -> "OTHER(" + type + ")";
        };
    }
}
