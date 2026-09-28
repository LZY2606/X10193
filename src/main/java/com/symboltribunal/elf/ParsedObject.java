package com.symboltribunal.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parse result for one ELF object (ET_REL / ET_EXEC / ET_DYN). */
public class ParsedObject {
    public boolean elf64 = true;
    public boolean littleEndian = true;
    public int machine;
    public int type;
    public String machineName = "UNKNOWN";

    public final List<ElfSection> sections = new ArrayList<>();
    public final List<ElfSymbol> staticSymbols = new ArrayList<>();
    public final List<ElfSymbol> dynamicSymbols = new ArrayList<>();
    public final List<Relocation> relocations = new ArrayList<>();

    /** Index aligned .gnu.version entries for .dynsym; map sym index -> raw version index. */
    public final Map<Integer, Integer> dynVersionIndex = new LinkedHashMap<>();

    /** All defined versions: version name -> isDefault. */
    public final Map<String, Boolean> versionDefinitions = new LinkedHashMap<>();
    /** Parent version name for each alias. */
    public final Map<String, String> versionParent = new LinkedHashMap<>();
    /** Needed version requirements: name@version as declared by this object. */
    public final List<String> versionRequirements = new ArrayList<>();
    /** dynsym index -> required version name (from verneed/verdef resolution). */
    public final Map<Integer, String> dynVersionName = new LinkedHashMap<>();
    /** dynsym index -> non-default flag for the bound version. */
    public final Map<Integer, Boolean> dynVersionNonDefault = new LinkedHashMap<>();

    /** COMDAT groups: signature -> member section indices. */
    public final Map<String, List<Integer>> comdats = new LinkedHashMap<>();
    /** signature -> owning symbol name for display. */
    public final Map<String, String> comdatOwner = new LinkedHashMap<>();

    public String soname;
    public final List<String> needed = new ArrayList<>();
    public final List<Long> dynamicEntries = new ArrayList<>();

    /** Set when parsing failed; the tribunal isolates the object but keeps going. */
    public String parseError;
}
