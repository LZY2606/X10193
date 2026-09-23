package com.tribunal.engine;

import com.tribunal.elf.ElfModel.ElfFile;
import com.tribunal.elf.ElfModel.Section;
import com.tribunal.elf.ElfModel.Symbol;

import java.util.HashSet;
import java.util.Set;

/** An input after parsing, before resolution. */
final class ParsedUnit {
    final VerdictModel.UnitId id;
    final InputKind kind;
    final ElfFile elf;
    final String archiveName;
    final String memberName;
    final int order;
    final boolean explicit;
    /** Sections discarded because their COMDAT group lost. */
    final Set<Integer> discardedSections = new HashSet<>();
    boolean active;
    String inactivityReason;

    ParsedUnit(VerdictModel.UnitId id, InputKind kind, ElfFile elf, String archiveName,
               String memberName, int order, boolean explicit) {
        this.id = id;
        this.kind = kind;
        this.elf = elf;
        this.archiveName = archiveName;
        this.memberName = memberName;
        this.order = order;
        this.explicit = explicit;
        this.active = explicit;
    }

    boolean sectionDiscarded(int shndx) {
        return discardedSections.contains(shndx);
    }

    boolean symbolDiscarded(Symbol symbol) {
        return sectionDiscarded(symbol.shndx());
    }

    String sectionName(int shndx) {
        Section s = elf.section(shndx);
        return s == null ? ("shndx=" + shndx) : s.name();
    }
}
