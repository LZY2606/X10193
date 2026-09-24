package com.symtribunal.engine;

import com.symtribunal.elf.ElfImage;
import com.symtribunal.elf.ElfSection;
import com.symtribunal.elf.ElfSymbol;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A participant in one resolution: a standalone object/shared object or one archive member. */
class Unit {
    final String key;
    final String label;
    final String binaryId;
    final String fileName;
    final String member;
    final ElfImage image;
    final int kind;
    final int explicitOrder;
    boolean fromArchive;
    boolean lazyExtracted;
    boolean active;
    long baseAddress;
    /** signature -> group section kept after COMDAT dedup; member section index -> discarded. */
    final Map<String, Integer> keptComdats = new LinkedHashMap<>();
    final java.util.Set<Integer> discardedSections = new java.util.HashSet<>();

    Unit(String key, String label, String binaryId, String fileName, String member,
         ElfImage image, int explicitOrder) {
        this.key = key;
        this.label = label;
        this.binaryId = binaryId;
        this.fileName = fileName;
        this.member = member;
        this.image = image;
        this.kind = image.type;
        this.explicitOrder = explicitOrder;
    }

    List<ElfSymbol> dynsyms() {
        return table("dynsym");
    }

    List<ElfSymbol> allSymbols() {
        return image.symbols;
    }

    private List<ElfSymbol> table(String kind) {
        List<ElfSymbol> out = new ArrayList<>();
        for (ElfSymbol symbol : image.symbols) {
            if (kind.equals(symbol.tableKind)) {
                while (out.size() <= symbol.index) {
                    out.add(null);
                }
                out.set(symbol.index, symbol);
            }
        }
        return out;
    }

    boolean symbolSectionLive(ElfSymbol symbol) {
        if (symbol.sectionIndex == com.symtribunal.elf.Elf.SHN_UNDEF
                || symbol.sectionIndex == 0) {
            return true;
        }
        if (symbol.sectionIndex >= image.sections.size()) {
            return false;
        }
        return !discardedSections.contains(symbol.sectionIndex);
    }

    ElfSection section(int index) {
        return index >= 0 && index < image.sections.size() ? image.sections.get(index) : null;
    }
}
