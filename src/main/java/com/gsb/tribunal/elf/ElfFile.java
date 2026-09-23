package com.gsb.tribunal.elf;

import java.util.List;

public record ElfFile(
        String name,
        int elfClass,
        int dataEncoding,
        int machine,
        int type,
        List<Section> sections,
        List<Symbol> symtab,
        List<Symbol> dynsym,
        List<Relocation> relocations,
        List<VerDef> verDefs,
        List<VerNeed> verNeeds,
        List<Group> groups,
        List<String> needed,
        String soname) {

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public List<Symbol> activeTable() {
        return type == ET_DYN && dynsym != null && !dynsym.isEmpty() ? dynsym : symtab;
    }
}
