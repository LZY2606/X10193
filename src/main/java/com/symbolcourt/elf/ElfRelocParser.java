package com.symbolcourt.elf;

import com.symbolcourt.model.ElfRelocation;
import com.symbolcourt.model.ElfSection;
import com.symbolcourt.model.ElfSymbol;
import com.symbolcourt.model.Enums.Machine;
import com.symbolcourt.model.Enums.RelocKind;
import com.symbolcourt.model.ParsedElf;

import java.util.List;

class ElfRelocParser {

    void parseRelocations(ByteReader r, ParsedElf elf, boolean elf64) {
        for (ElfSection s : elf.sections) {
            if (s.type != ElfSection.SHT_REL && s.type != ElfSection.SHT_RELA) continue;
            boolean rela = s.type == ElfSection.SHT_RELA;
            boolean dynamic = isDynamicReloc(elf, s);
            List<ElfSymbol> syms = symbolsFor(elf, s);
            ElfSection apply = elf.sectionAt(s.info);
            String targetName = apply != null ? apply.name : ("section#" + s.info);
            int entSize = (int) (s.entsize > 0 ? s.entsize : elf64 ? (rela ? 24 : 16) : (rela ? 12 : 8));
            long count = entSize <= 0 ? 0 : s.data.length / (long) entSize;
            if (count > 5_000_000L) throw new ElfFormatException("relocation table implausibly large");
            for (long i = 0; i < count; i++) {
                long base = s.offset + i * entSize;
                ElfRelocation rel = new ElfRelocation();
                rel.sectionName = s.name;
                rel.targetSectionName = targetName;
                rel.dynamic = dynamic;
                int symIdx;
                long type;
                if (elf64) {
                    long info = r.u64(base + 8);
                    symIdx = (int) (info >> 32);
                    type = info & 0xffffffffL;
                    rel.offset = r.u64(base);
                    if (rela) rel.addend = r.u64(base + 16);
                } else {
                    long info = r.u32(base + 4);
                    symIdx = (int) (info >> 8);
                    type = info & 0xff;
                    rel.offset = r.u32(base);
                    if (rela) rel.addend = r.u32(base + 8);
                }
                rel.symIndex = symIdx;
                rel.type = (int) type;
                rel.typeName = typeName(elf, rel.type);
                rel.kind = classify(rel.typeName, dynamic);
                if (symIdx >= 0 && symIdx < syms.size()) {
                    ElfSymbol sym = syms.get(symIdx);
                    rel.symbolName = sym.name;
                } else if (symIdx == 0) {
                    rel.symbolName = "";
                } else {
                    rel.symbolName = "<sym-out-of-range>";
                }
                elf.relocations.add(rel);
            }
        }
    }

    private boolean isDynamicReloc(ParsedElf elf, ElfSection s) {
        ElfSection link = elf.sectionAt(s.link);
        return link != null && link.type == ElfSection.SHT_DYNSYM;
    }

    private List<ElfSymbol> symbolsFor(ParsedElf elf, ElfSection s) {
        ElfSection link = elf.sectionAt(s.link);
        if (link == null) return List.of();
        return link.type == ElfSection.SHT_DYNSYM ? elf.dynamicSymbols : elf.symbols;
    }

    private RelocKind classify(String name, boolean dynamic) {
        return switch (name) {
            case "R_X86_64_COPY", "R_AARCH64_COPY", "R_ARM_COPY", "R_386_COPY" -> RelocKind.COPY;
            case "R_X86_64_JUMP_SLOT", "R_AARCH64_JUMP_SLOT", "R_ARM_JUMP_SLOT", "R_386_JMP_SLOT" ->
                    RelocKind.JUMP_SLOT;
            case "R_X86_64_GLOB_DAT", "R_AARCH64_GLOB_DAT", "R_ARM_GLOB_DAT", "R_386_GLOB_DAT" ->
                    RelocKind.GLOB_DAT;
            case "R_X86_64_RELATIVE", "R_AARCH64_RELATIVE", "R_ARM_RELATIVE", "R_386_RELATIVE" ->
                    RelocKind.RELATIVE;
            case "R_X86_64_IRELATIVE", "R_AARCH64_IRELATIVE" -> RelocKind.IRELATIVE;
            default -> dynamic ? RelocKind.OTHER_DYNAMIC : RelocKind.REGULAR;
        };
    }

    private String typeName(ParsedElf elf, int type) {
        Machine m = elf.machine;
        if (m == Machine.X86_64 && elf.machineValue == 62) {
            return switch (type) {
                case 0 -> "R_X86_64_NONE";
                case 1 -> "R_X86_64_64";
                case 2 -> "R_X86_64_PC32";
                case 6 -> "R_X86_64_GLOB_DAT";
                case 7 -> "R_X86_64_JUMP_SLOT";
                case 8 -> "R_X86_64_RELATIVE";
                case 9 -> "R_X86_64_GOTPCREL";
                case 10 -> "R_X86_64_32";
                case 11 -> "R_X86_64_32S";
                case 20 -> "R_X86_64_PC64";
                case 37 -> "R_X86_64_IRELATIVE";
                case 41 -> "R_X86_64_COPY";
                default -> "R_X86_64_" + type;
            };
        }
        if (m == Machine.X86_64 && elf.machineValue == 3) {
            return switch (type) {
                case 1 -> "R_386_32";
                case 2 -> "R_386_PC32";
                case 6 -> "R_386_GLOB_DAT";
                case 7 -> "R_386_JMP_SLOT";
                case 8 -> "R_386_RELATIVE";
                case 9 -> "R_386_COPY";
                default -> "R_386_" + type;
            };
        }
        if (elf.machineValue == 183) {
            return switch (type) {
                case 0 -> "R_AARCH64_NONE";
                case 257 -> "R_AARCH64_ABS64";
                case 1024 -> "R_AARCH64_COPY";
                case 1025 -> "R_AARCH64_GLOB_DAT";
                case 1026 -> "R_AARCH64_JUMP_SLOT";
                case 1027 -> "R_AARCH64_RELATIVE";
                case 1032 -> "R_AARCH64_IRELATIVE";
                default -> "R_AARCH64_" + type;
            };
        }
        return "R_" + type;
    }
}
