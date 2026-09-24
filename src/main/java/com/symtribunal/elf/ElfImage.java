package com.symtribunal.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed representation of one ELF object. All raw offsets point back into the original bytes. */
public class ElfImage {
    public String origin;
    public byte[] bytes;
    public boolean elf64;
    public boolean littleEndian;
    public int elfClass;
    public int type;
    public int machine;
    public long entry;
    public long flags;
    public long sectionHeaderOffset;
    public long programHeaderOffset;
    public int sectionHeaderSize;
    public int sectionCount;
    public int sectionNameTableIndex;
    public String soname;
    public List<ElfSection> sections = new ArrayList<>();
    public List<ElfSymbol> symbols = new ArrayList<>();
    public List<ElfRelocation> relocations = new ArrayList<>();
    public List<ElfVersionDef> versionDefs = new ArrayList<>();
    public List<ElfVersionNeed> versionNeeds = new ArrayList<>();
    public List<ElfGroup> groups = new ArrayList<>();
    public List<String> needed = new ArrayList<>();
    /** For each dynsym entry: resolved version name (null = unversioned). */
    public List<String> dynVersions = new ArrayList<>();
    /** For each dynsym entry: true if the alias is hidden (non-default binding). */
    public boolean[] dynVersionHidden;
    /** section index -> symbol table section that owns it (SHT_GROUP sh_link). */
    public int[] groupSymtabSection;
    /** section index -> group section index, for members of COMDAT groups. */
    public int[] sectionToGroup;
    /** resolved section index array (supports SHT_SYMTAB_SHNDX extended indices). */
    public int[][] symSectionIndices;
    public String error;

    public ElfSection section(String name) {
        for (ElfSection section : sections) {
            if (name.equals(section.name)) {
                return section;
            }
        }
        return null;
    }
}
