package com.symcourt.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java ELF parser. Supports ELF32/ELF64 in either endianness, relocatable
 * objects, shared objects/executables, static & dynamic symbol tables,
 * relocations (REL/RELA), section groups (COMDAT), .dynamic and GNU symbol
 * versioning sections. No external tools are invoked.
 */
public final class ElfReader {

    public static ParsedElf parse(byte[] data) throws ElfException {
        if (data == null || data.length < ElfConstants.EI_NIDENT) {
            throw new ElfException("file too short for ELF identification");
        }
        if ((data[0] & 0xff) != ElfConstants.EI_MAG0
                || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfException("bad ELF magic");
        }
        int elfClass = data[ElfConstants.EI_CLASS] & 0xff;
        if (elfClass != ElfConstants.ELFCLASS32 && elfClass != ElfConstants.ELFCLASS64) {
            throw new ElfException("unsupported EI_CLASS=" + elfClass);
        }
        int encoding = data[ElfConstants.EI_DATA] & 0xff;
        if (encoding != ElfConstants.ELFDATA2LSB && encoding != ElfConstants.ELFDATA2MSB) {
            throw new ElfException("unsupported EI_DATA=" + encoding);
        }
        if (data[6] != 1) {
            throw new ElfException("unsupported ELF version byte " + data[6]);
        }
        boolean elf64 = elfClass == ElfConstants.ELFCLASS64;
        boolean le = encoding == ElfConstants.ELFDATA2LSB;
        ElfInput in = new ElfInput(data, le, elf64);

        int type = in.u16(16);
        int machine = in.u16(18);
        long sectionHeaderOffset = elf64 ? in.u64(0x28) : in.u32(0x20);
        int sectionHeaderSize = elf64 ? in.u16(0x3a) : in.u16(0x2e);
        int sectionCount = elf64 ? in.u16(0x3c) : in.u16(0x30);
        int sectionStringIndex = elf64 ? in.u16(0x3e) : in.u16(0x32);

        if (type != ElfConstants.ET_REL && type != ElfConstants.ET_EXEC && type != ElfConstants.ET_DYN) {
            throw new ElfException("unsupported e_type=" + type);
        }
        if (sectionHeaderSize <= 0) {
            throw new ElfException("invalid e_shentsize=" + sectionHeaderSize);
        }
        if (sectionHeaderOffset != 0 && sectionCount == 0) {
            // extended numbering: count lives in sh_size of section 0
            if (sectionHeaderSize < 40) {
                throw new ElfException("extended section numbering impossible for class");
            }
            sectionCount = (int) (elf64 ? in.u64(sectionHeaderOffset + 32)
                    : in.u32(sectionHeaderOffset + 16));
        }
        if (sectionCount < 0 || sectionCount > 65535) {
            throw new ElfException("implausible section count " + sectionCount);
        }
        if (sectionHeaderOffset > 0 && (long) sectionCount * sectionHeaderSize
                > (long) data.length - sectionHeaderOffset) {
            throw new ElfException("section header table exceeds file bounds");
        }

        ParsedElf elf = new ParsedElf(elf64, le, machine, type);
        parseSections(in, elf, sectionHeaderOffset, sectionHeaderSize, sectionCount);

        if (sectionStringIndex == 0xffff && !elf.sections.isEmpty()) {
            sectionStringIndex = elf.sections.get(0).link;
        }
        nameSections(in, elf, sectionStringIndex);
        parseDynamic(in, elf);
        parseSymbolTables(in, elf);
        parseRelocations(in, elf);
        parseGroups(in, elf);
        parseVersionSections(in, elf);
        return elf;
    }

    private static void parseSections(ElfInput in, ParsedElf elf, long off, int entsize, int count)
            throws ElfException {
        for (int i = 0; i < count; i++) {
            long base = off + (long) i * entsize;
            if (in.elf64) {
                int nameOff = in.u32i(base);
                int type = in.u32i(base + 4);
                long flags = in.u64(base + 8);
                long addr = in.u64(base + 16);
                long soff = in.u64(base + 24);
                long size = in.u64(base + 32);
                int link = in.u32i(base + 40);
                int info = in.u32i(base + 44);
                long addralign = in.u64(base + 48);
                long entSize = in.u64(base + 56);
                if (entsize < 64) {
                    throw new ElfException("ELF64 shdr entsize " + entsize + " < 64");
                }
                safeSectionRange(in, type, soff, size, i);
                elf.sections.add(new ParsedSection(i, "sec" + nameOff, type, flags, addr, soff,
                        size, link, info, addralign, entSize));
            } else {
                int nameOff = in.u32i(base);
                int type = in.u32i(base + 4);
                long flags = in.u32(base + 8);
                long addr = in.u32(base + 12);
                long soff = in.u32(base + 16);
                long size = in.u32(base + 20);
                int link = in.u32i(base + 24);
                int info = in.u32i(base + 28);
                long addralign = in.u32(base + 32);
                long entSize = in.u32(base + 36);
                if (entsize < 40) {
                    throw new ElfException("ELF32 shdr entsize " + entsize + " < 40");
                }
                safeSectionRange(in, type, soff, size, i);
                elf.sections.add(new ParsedSection(i, "sec" + nameOff, type, flags, addr, soff,
                        size, link, info, addralign, entSize));
            }
        }
    }

    private static void safeSectionRange(ElfInput in, int type, long soff, long size, int index)
            throws ElfException {
        // SHT_NOBITS (8) consumes no file space; everything else must fit.
        if (size > 0 && soff != 0 && type != 8) {
            in.check(soff, size);
        }
        if (soff < 0 || size < 0) {
            throw new ElfException("section " + index + " negative offset/size");
        }
    }

    private static void nameSections(ElfInput in, ParsedElf elf, int strIndex) throws ElfException {
        if (strIndex <= 0 || strIndex >= elf.sections.size()) {
            return;
        }
        ParsedSection str = elf.sections.get(strIndex);
        for (ParsedSection s : elf.sections) {
            if (s.index == 0) {
                s.setName("");
                continue;
            }
            int nameOff = parseIntPrefix(s.name);
            try {
                s.setName(in.tableString(str.offset, str.size, nameOff));
            } catch (ElfException e) {
                s.setName("<bad-name>");
            }
        }
    }

    private static int parseIntPrefix(String name) {
        // placeholder name was "sec<offset>"
        return Integer.parseInt(name.substring(3));
    }

    private static ParsedSection section(ParsedElf elf, int index) throws ElfException {
        if (index < 0 || index >= elf.sections.size()) {
            throw new ElfException("section index " + index + " out of range");
        }
        return elf.sections.get(index);
    }

    private static void parseDynamic(ElfInput in, ParsedElf elf) throws ElfException {
        for (ParsedSection s : elf.sections) {
            if (s.type != ElfConstants.SHT_DYNAMIC) {
                continue;
            }
            int entryBytes = in.wordBytes() * 2;
            if (entryBytes <= 0 || s.size % entryBytes != 0) {
                throw new ElfException("malformed .dynamic size " + s.size);
            }
            long count = s.size / entryBytes;
            if (count > 1_000_000) {
                throw new ElfException("implausible .dynamic entry count");
            }
            for (long i = 0; i < count; i++) {
                long base = s.offset + i * entryBytes;
                long tag = in.word(base);
                long val = in.word(base + in.wordBytes());
                if (tag == 0) {
                    break;
                }
                if (tag == ElfConstants.DT_NEEDED || tag == ElfConstants.DT_SONAME) {
                    ParsedSection strtab = dynamicStringTable(elf, s);
                    if (strtab != null && val < strtab.size) {
                        String name = in.tableString(strtab.offset, strtab.size, val);
                        if (tag == ElfConstants.DT_NEEDED) {
                            elf.needed.add(name);
                        } else {
                            elf.soname = name;
                        }
                    }
                }
            }
        }
    }

    private static ParsedSection dynamicStringTable(ParsedElf elf, ParsedSection dynamicSection) {
        // The .dynstr is conventionally the link target of .dynamic.
        if (dynamicSection.link > 0 && dynamicSection.link < elf.sections.size()) {
            return elf.sections.get(dynamicSection.link);
        }
        for (ParsedSection s : elf.sections) {
            if (".dynstr".equals(s.name)) {
                return s;
            }
        }
        return null;
    }

    private static void parseSymbolTables(ElfInput in, ParsedElf elf) throws ElfException {
        for (ParsedSection s : elf.sections) {
            if (s.type != ElfConstants.SHT_SYMTAB && s.type != ElfConstants.SHT_DYNSYM) {
                continue;
            }
            boolean dyn = s.type == ElfConstants.SHT_DYNSYM;
            ParsedSection strtab;
            if (s.link >= 0 && s.link < elf.sections.size()) {
                strtab = elf.sections.get(s.link);
            } else {
                strtab = null;
            }
            int ent = in.elf64 ? 24 : 16;
            if (s.entrySize > 0 && s.entrySize != ent) {
                // tolerate only the canonical entry width
                throw new ElfException("unexpected symbol entry size " + s.entrySize);
            }
            if (s.size % ent != 0) {
                throw new ElfException("symbol table size " + s.size + " not a multiple of " + ent);
            }
            long count = s.size / ent;
            if (count > 50_000_000) {
                throw new ElfException("implausible symbol count");
            }
            List<ElfSymbol> target = dyn ? elf.dynamicSymbols : elf.staticSymbols;
            for (long i = 0; i < count; i++) {
                long base = s.offset + i * ent;
                ElfSymbol sym = readSymbol(in, base, (int) i, dyn);
                if (strtab != null && !sym.name.isEmpty()) {
                    try {
                        sym.setName(in.tableString(strtab.offset, strtab.size, sym.nameOffset()));
                    } catch (ElfException e) {
                        sym.setName("<bad-name>");
                    }
                }
                target.add(sym);
            }
        }
    }

    private static ElfSymbol readSymbol(ElfInput in, long base, int index, boolean dyn)
            throws ElfException {
        int nameOff;
        long value;
        long size;
        int info;
        int other;
        int shndx;
        if (in.elf64) {
            nameOff = in.u32i(base);
            info = in.u8(base + 4);
            other = in.u8(base + 5);
            shndx = in.u16(base + 6);
            value = in.u64(base + 8);
            size = in.u64(base + 16);
        } else {
            nameOff = in.u32i(base);
            value = in.u32(base + 4);
            size = in.u32(base + 8);
            info = in.u8(base + 12);
            other = in.u8(base + 13);
            shndx = in.u16(base + 14);
        }
        int binding = info >> 4;
        int type = info & 0xf;
        int visibility = other & 0x3;
        return new ElfSymbol(index, null, nameOff, value, size, binding, type,
                visibility, shndx, dyn);
    }
