package com.example.tribunal.core;

import java.util.List;

public record ElfObject(
        int machine,
        int type,
        String soname,
        List<ElfSection> sections,
        List<ElfSymbol> symbols,
        List<ElfRelocation> relocations,
        List<String> needed,
        List<VersionInfo> versions,
        List<SectionGroup> groups
) {
    public ElfSymbol symbol(int index) {
        if (index < 0 || index >= symbols.size()) {
            return null;
        }
        return symbols.get(index);
    }

    public boolean sharedObject() {
        return type == 3;
    }

    public boolean relocatable() {
        return type == 1;
    }
}
