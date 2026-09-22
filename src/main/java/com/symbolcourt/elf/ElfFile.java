package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fully parsed, immutable view of one ELF object/shared object. */
public final class ElfFile {
    public String sourceName;
    public boolean elf64;
    public boolean littleEndian;
    public Kind kind;
    public int machine;
    public String machineName;

    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> staticSymbols = new ArrayList<>();   // .symtab
    public final List<Symbol> dynamicSymbols = new ArrayList<>();  // .dynsym
    public final List<Relocation> relocations = new ArrayList<>();
    public final List<ComdatGroup> groups = new ArrayList<>();
    public final List<VersionDef> versionDefs = new ArrayList<>();
    public final List<VersionNeed> versionNeeds = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;

    /** Maps versym index -> resolved version name (e.g. "GLIBC_2.2.5"). */
    public final Map<Integer, String> versionIndexToName = new LinkedHashMap<>();
    /** versym index -> hidden flag (VER_FLG_HIDDEN / special hidden index). */
    public final Map<Integer, Boolean> versionIndexHidden = new LinkedHashMap<>();
    /** dynsym table index -> resolved version name. */
    public Map<Integer, String> dynVersionNameBySymbol = new LinkedHashMap<>();
    /** dynsym table index -> version hidden bit. */
    public Map<Integer, Boolean> dynVersionHiddenBySymbol = new LinkedHashMap<>();
    /** Map symbol-table-index -> versym value for .dynsym. */
    public final Map<Integer, Integer> dynVersionBySymbol = new LinkedHashMap<>();

    public List<Symbol> allSymbols() {
        List<Symbol> all = new ArrayList<>(staticSymbols);
        all.addAll(dynamicSymbols);
        return all;
    }

    /** Defined symbols visible to the relevant kind of link. */
    public List<Symbol> exportedDefinitions() {
        List<Symbol> out = new ArrayList<>();
        List<Symbol> table = kind == Kind.RELOCATABLE ? staticSymbols : dynamicSymbols;
        for (Symbol s : table) {
            if (s.isDefined() && s.binding != SymbolBinding.LOCAL
                    && s.visibility != Visibility.HIDDEN
                    && s.visibility != Visibility.INTERNAL) {
                out.add(s);
            }
        }
        return out;
    }
}
