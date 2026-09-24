package com.symtribunal.elf;

import java.util.List;

/** Reads SHT_SYMTAB / SHT_DYNSYM with linked string tables and versym versions. */
final class ElfSymbolParser {
    private ElfSymbolParser() {}

    static void parse(ByteReader reader, ElfImage image) {
        for (ElfSection section : image.sections) {
            if (section.type != Elf.SHT_SYMTAB && section.type != Elf.SHT_DYNSYM) {
                continue;
            }
            String kind = section.type == Elf.SHT_DYNSYM ? "dynsym" : "symtab";
            if (section.link <= 0 || section.link >= image.sections.size()) {
                throw new ElfParseException(kind + " bad strtab link " + section.link);
            }
            ElfSection strtab = image.sections.get(section.link);
            if (strtab.type != Elf.SHT_STRTAB) {
                throw new ElfParseException(kind + " linked section is not SHT_STRTAB");
            }
            int entrySize = image.elf64 ? 24 : 16;
            if (section.entsize != 0 && section.entsize != entrySize) {
                throw new ElfParseException(kind + " bad entsize " + section.entsize);
            }
            long count = section.size / entrySize;
            int[] extended = image.symSectionIndices == null ? null
                    : image.symSectionIndices[section.index];
            if (extended != null && extended.length < count) {
                throw new ElfParseException("SHT_SYMTAB_SHNDX shorter than symbol table");
            }
            for (int i = 0; i < count; i++) {
                long base = section.offset + (long) i * entrySize;
                ElfSymbol symbol = new ElfSymbol();
                symbol.index = i;
                symbol.tableKind = kind;
                if (image.elf64) {
                    int nameOffset = (int) reader.u32(base);
                    symbol.size = reader.u64(base + 8);
                    symbol.value = reader.u64(base + 16);
                    int info = reader.u8(base + 4);
                    symbol.binding = info >> 4;
                    symbol.type = info & 0xf;
                    symbol.visibility = reader.u8(base + 5) & 0x3;
                    int shndx = (int) reader.u16(base + 6);
                    symbol.sectionIndex = extended != null ? extended[i] : shndx;
                    symbol.name = i == 0 ? "" : readName(reader, strtab, nameOffset);
                } else {
                    int nameOffset = (int) reader.u32(base);
                    symbol.value = reader.u32(base + 4);
                    symbol.size = reader.u32(base + 8);
                    symbol.binding = reader.u8(base + 12) >> 4;
                    symbol.type = reader.u8(base + 12) & 0xf;
                    symbol.visibility = reader.u8(base + 13) & 0x3;
                    int shndx = (int) reader.u16(base + 14);
                    symbol.sectionIndex = extended != null ? extended[i] : shndx;
                    symbol.name = i == 0 ? "" : readName(reader, strtab, nameOffset);
                }
                symbol.defined = symbol.sectionIndex != Elf.SHN_UNDEF
                        && symbol.sectionIndex != 0;
                if ("dynsym".equals(kind) && image.dynVersions != null
                        && i < image.dynVersions.size()) {
                    symbol.version = image.dynVersions.get(i);
                    symbol.versionHidden = image.dynVersionHidden[i];
                }
                image.symbols.add(symbol);
            }
        }
    }

    private static String readName(ByteReader reader, ElfSection strtab, int offset) {
        long absolute = strtab.offset + (offset & 0xffffffffL);
        if (absolute < strtab.offset || absolute > strtab.offset + strtab.size) {
            throw new ElfParseException("symbol name offset out of strtab");
        }
        return reader.cString(absolute);
    }
}
