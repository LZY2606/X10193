package com.symtribunal.engine;

import com.symtribunal.elf.Elf;
import com.symtribunal.elf.ElfSymbol;

/** A single defining symbol that can satisfy an undefined reference. */
class Provider {
    final Unit unit;
    final ElfSymbol symbol;
    final int strength;
    final String version;
    final boolean defaultVersion;
    final int loadRank;
    /** Provider source class for ranking: 0 object strong/unique, 1 object weak, 2 common, 3 DSO. */
    final int sourceClass;

    Provider(Unit unit, ElfSymbol symbol, int loadRank) {
        this.unit = unit;
        this.symbol = symbol;
        this.version = symbol.version;
        this.defaultVersion = !symbol.versionHidden;
        this.loadRank = loadRank;
        boolean common = symbol.sectionIndex == Elf.SHN_COMMON
                || (symbol.type == Elf.STT_COMMON && !symbol.defined);
        this.strength = symbol.binding == Elf.STB_WEAK ? 1 : 2;
        boolean dso = unit.kind == Elf.ET_DYN;
        if (dso) {
            this.sourceClass = 3;
        } else if (common) {
            this.sourceClass = 2;
        } else if (this.strength == 1) {
            this.sourceClass = 1;
        } else {
            this.sourceClass = 0;
        }
    }

    String providerKey() {
        return unit.key + "#" + symbol.name + (version == null ? "" : "@" + version);
    }
}
