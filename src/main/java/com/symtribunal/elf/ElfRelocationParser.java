package com.symtribunal.elf;

import java.util.ArrayList;
import java.util.List;

/** Parses SHT_REL / SHT_RELA and annotates each entry from its linked symbol table. */
final class ElfRelocationParser {
    private ElfRelocationParser() {}

    static void parse(ByteReader reader, ElfImage image) {
        for (ElfSection section : image.sections) {
            if (section.type != Elf.SHT_REL && section.type != Elf.SHT_RELA) {
                continue;
            }
            boolean rela = section.type == Elf.SHT_RELA;
            int entrySize = rela ? (image.elf64 ? 24 : 12) : (image.elf64 ? 16 : 8);
            if (section.size % entrySize != 0) {
                throw new ElfParseException("relocation section size not a multiple of entry size");
            }
            if (section.link <= 0 || section.link >= image.sections.size()) {
                throw new ElfParseException("relocation section bad symbol link " + section.link);
            }
            ElfSection symtab = image.sections.get(section.link);
            if (symtab.type != Elf.SHT_SYMTAB && symtab.type != Elf.SHT_DYNSYM) {
                throw new ElfParseException("relocation section does not link a symbol table");
            }
            List<ElfSymbol> table = table(image, symtab.type);
            long count = section.size / entrySize;
            for (int i = 0; i < count; i++) {
                long base = section.offset + (long) i * entrySize;
                ElfRelocation reloc = new ElfRelocation();
                reloc.sectionIndex = section.index;
                reloc.symbolTable = symtab.type == Elf.SHT_DYNSYM ? "dynsym" : "symtab";
                if (image.elf64) {
                    reloc.offset = reader.u64(base);
                    reloc.info = reader.u64(base + 8);
                    reloc.symbolIndex = (int) (reloc.info >>> 32);
                    reloc.type = (int) (reloc.info & 0xffffffffL);
                    reloc.addend = rela ? reader.u64(base + 16) : null;
                } else {
                    reloc.offset = reader.u32(base);
                    reloc.info = reader.u32(base + 4);
                    reloc.symbolIndex = (int) (reloc.info >>> 8);
                    reloc.type = (int) (reloc.info & 0xff);
                    reloc.addend = rela ? reader.u32(base + 8) : null;
                }
                if (reloc.symbolIndex >= table.size() || table.get(reloc.symbolIndex) == null) {
                    throw new ElfParseException(
                            "relocation symbol index " + reloc.symbolIndex + " out of range");
                }
                ElfSymbol symbol = table.get(reloc.symbolIndex);
                reloc.symbolName = symbol.name;
                reloc.symbolBinding = symbol.binding;
                reloc.symbolVisibility = symbol.visibility;
                reloc.symbolDefined = symbol.defined;
                reloc.symbolVersion = symbol.version;
                image.relocations.add(reloc);
            }
        }
    }

    private static List<ElfSymbol> table(ElfImage image, int type) {
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
