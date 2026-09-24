package com.symtribunal.elf;

import java.util.ArrayList;
import java.util.List;

/** Parses SHT_GROUP sections and maps each member section back to its group. */
final class ElfGroupParser {
    private ElfGroupParser() {}

    /** First pass: read flags and member sections (no symbol table access required). */
    static void mapMembers(ByteReader reader, ElfImage image) {
        for (ElfSection section : image.sections) {
            if (section.type != Elf.SHT_GROUP) {
                continue;
            }
            if (section.size < 8 || section.size % 4 != 0) {
                throw new ElfParseException("SHT_GROUP bad size");
            }
            if (section.link <= 0 || section.link >= image.sections.size()) {
                throw new ElfParseException("SHT_GROUP bad symtab link " + section.link);
            }
            ElfGroup group = new ElfGroup();
            group.sectionIndex = section.index;
            group.flags = reader.u32(section.offset);
            group.signatureSymbolIndex = (int) reader.u32(section.offset + 4);
            group.comdat = (group.flags & Elf.GRP_COMDAT) != 0;
            for (long off = section.offset + 8; off < section.offset + section.size; off += 4) {
                int member = (int) reader.u32(off);
                if (member >= image.sections.size()) {
                    throw new ElfParseException("SHT_GROUP member section index out of range");
                }
                group.memberSections.add(member);
                image.sectionToGroup[member] = section.index;
            }
            image.groupSymtabSection[section.index] = section.link;
            image.groups.add(group);
        }
    }

    /** Second pass: resolve group signature names through the associated symbol table. */
    static void resolveSignatures(ElfImage image) {
        for (ElfGroup group : image.groups) {
            int symtabSectionIndex = image.groupSymtabSection[group.sectionIndex];
            ElfSection symtabSection = image.sections.get(symtabSectionIndex);
            List<ElfSymbol> table = tableSymbols(image, symtabSection.type);
            if (group.signatureSymbolIndex >= table.size()
                    || table.get(group.signatureSymbolIndex) == null) {
                throw new ElfParseException("SHT_GROUP signature symbol index out of range");
            }
            group.signature = table.get(group.signatureSymbolIndex).name;
        }
    }

    private static List<ElfSymbol> tableSymbols(ElfImage image, int type) {
        String kind = type == Elf.SHT_DYNSYM ? "dynsym" : "symtab";
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
}
