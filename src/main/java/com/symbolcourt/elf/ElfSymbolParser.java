package com.symbolcourt.elf;

import com.symbolcourt.model.ElfSection;
import com.symbolcourt.model.ElfSymbol;
import com.symbolcourt.model.Enums.Binding;
import com.symbolcourt.model.Enums.SymType;
import com.symbolcourt.model.Enums.Visibility;
import com.symbolcourt.model.ParsedElf;

import java.util.List;

class ElfSymbolParser {

    void parseSymbols(ByteReader r, ParsedElf elf, boolean elf64) {
        for (ElfSection s : elf.sections) {
            if (s.type == ElfSection.SHT_SYMTAB || s.type == ElfSection.SHT_DYNSYM) {
                List<ElfSymbol> out = s.type == ElfSection.SHT_DYNSYM ? elf.dynamicSymbols : elf.symbols;
                parseOneTable(r, elf, s, elf64, s.type == ElfSection.SHT_DYNSYM, out);
            }
        }
    }

    private void parseOneTable(ByteReader r, ParsedElf elf, ElfSection symSec, boolean elf64,
                               boolean dynamic, List<ElfSymbol> out) {
        long entSize = symSec.entsize > 0 ? symSec.entsize : (elf64 ? 24 : 16);
        if (entSize < (elf64 ? 24 : 16)) {
            throw new ElfFormatException("symbol entry size too small");
        }
        ElfSection strSec = elf.sectionAt(symSec.link);
        if (strSec == null) {
            throw new ElfFormatException("symbol string table missing for " + symSec.name);
        }
        long count = entSize == 0 ? 0 : symSec.size / entSize;
        if (count > 1_000_000L) {
            throw new ElfFormatException("symbol table implausibly large: " + count);
        }
        for (long i = 0; i < count; i++) {
            long base = symSec.offset + i * entSize;
            ElfSymbol sym = new ElfSymbol();
            sym.dynamic = dynamic;
            if (elf64) {
                int nameIdx = (int) r.u32(base);
                int info = r.u8(base + 4);
                int other = r.u8(base + 5);
                sym.shndx = r.u16(base + 6);
                sym.value = r.u64(base + 8);
                sym.size = r.u64(base + 16);
                fillName(r, sym, nameIdx, strSec);
                mapInfo(sym, info, other);
            } else {
                int nameIdx = (int) r.u32(base);
                sym.value = r.u32(base + 4);
                sym.size = r.u32(base + 8);
                int info = r.u8(base + 12);
                int other = r.u8(base + 13);
                sym.shndx = r.u16(base + 14);
                fillName(r, sym, nameIdx, strSec);
                mapInfo(sym, info, other);
            }
            if (sym.isDefined()) {
                ElfSection defSec = elf.sectionAt(sym.shndx);
                if (defSec != null) sym.sectionName = defSec.name;
            }
            out.add(sym);
        }
    }

    private void fillName(ByteReader r, ElfSymbol sym, int nameIdx, ElfSection strSec) {
        if (nameIdx == 0) {
            sym.name = "";
            return;
        }
        try {
            sym.name = r.cstringInSection(nameIdx & 0xffffffffL, strSec.offset, strSec.size);
        } catch (ElfFormatException e) {
            sym.name = "<bad-sym-name>";
        }
    }

    private void mapInfo(ElfSymbol sym, int info, int other) {
        int bind = info >> 4;
        int type = info & 0xf;
        sym.binding = switch (bind) {
            case 0 -> Binding.LOCAL;
            case 1 -> Binding.GLOBAL;
            case 2 -> Binding.WEAK;
            default -> Binding.OTHER;
        };
        sym.type = switch (type) {
            case 0 -> SymType.NOTYPE;
            case 1 -> SymType.OBJECT;
            case 2 -> SymType.FUNC;
            case 3 -> SymType.SECTION;
            case 4 -> SymType.FILE;
            case 5 -> SymType.COMMON;
            case 6 -> SymType.TLS;
            default -> SymType.OTHER;
        };
        int vis = other & 0x3;
        sym.visibility = switch (vis) {
            case 0 -> Visibility.DEFAULT;
            case 1 -> Visibility.INTERNAL;
            case 2 -> Visibility.HIDDEN;
            case 3 -> Visibility.PROTECTED;
            default -> Visibility.OTHER;
        };
    }
}
