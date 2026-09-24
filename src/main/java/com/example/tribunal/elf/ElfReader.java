package com.example.tribunal.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounds-checked parser for the subset of ELF needed by the tribunal:
 * headers, sections, static and dynamic symbol tables, relocations,
 * GNU version definitions/needs, section groups and the dynamic dependency
 * list. Supports both ELF32 and ELF64, either endianness.
 *
 * Every read goes through accessors that validate length and index ranges;
 * malformed input surfaces as {@link ElfFormatException}.
 */
public final class ElfReader {

    private static final int ET_REL = 1;
    private static final int ET_EXEC = 2;
    private static final int ET_DYN = 3;

    private final byte[] data;
    private final boolean le;
    private final boolean elf64;

    private ElfReader(byte[] data) throws ElfFormatException {
        if (data == null || data.length < 16) {
            throw new ElfFormatException("file too small for an ELF header");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfFormatException("bad ELF magic");
        }
        int elfClass = u8(4);
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfFormatException("unknown ELF class " + elfClass);
        }
        int dataEncoding = u8(5);
        if (dataEncoding != 1 && dataEncoding != 2) {
            throw new ElfFormatException("unknown ELF data encoding " + dataEncoding);
        }
        this.elf64 = elfClass == 2;
        this.le = dataEncoding == 1;
        this.data = data;
        if (u8(6) != 1) {
            throw new ElfFormatException("unsupported ELF version");
        }
    }

    public static ElfFile parse(byte[] bytes, String displayName, String origin)
            throws ElfFormatException {
        return new ElfReader(bytes).doParse(displayName, origin);
    }

    private ElfFile doParse(String displayName, String origin) throws ElfFormatException {
        int type = u16(16);
        int machine = u16(18);
        long entry = elf64 ? u64(24) : u32(24);
        long flags = elf64 ? u48(48) : 0;

        List<ElfSection> sections = parseSections();
        Map<Integer, StringTable> stringTables = mapStringTables(sections);
        assignSectionNames(sections, stringTables);

        ParsedSymbols staticParsed = null;
        ParsedSymbols dynamicParsed = null;
        for (ElfSection section : sections) {
            if (section.type() == ElfSection.SHT_SYMTAB) {
                staticParsed = parseSymbols(section, stringTables, sections, false);
            } else if (section.type() == ElfSection.SHT_DYNSYM) {
                dynamicParsed = parseSymbols(section, stringTables, sections, true);
            }
        }
        List<ElfSymbol> staticSymbols = staticParsed == null
                ? List.of() : staticParsed.symbols;
        List<ElfSymbol> dynamicSymbols = dynamicParsed == null
                ? List.of() : dynamicParsed.symbols;

        List<ElfRelocation> relocations = parseRelocations(sections, staticParsed, dynamicParsed);
        VersionRecords versions = parseVersions(sections, stringTables, dynamicParsed);
        List<ElfFile.Group> groups = parseGroups(sections, stringTables, staticParsed);

        DynamicInfo dynamicInfo = parseDynamic(sections, stringTables);

        return new ElfFile(displayName, origin, data.clone(), elf64, le,
                elf64 ? 2 : 1, type, machine, entry, flags,
                List.copyOf(sections),
                List.copyOf(staticSymbols),
                List.copyOf(dynamicSymbols),
                List.copyOf(relocations),
                versions,
                List.copyOf(groups),
                List.copyOf(dynamicInfo.needed),
                dynamicInfo.soname);
    }

    // ---- section headers ------------------------------------------------

    private List<ElfSection> parseSections() throws ElfFormatException {
        long shoff;
        int shentsize;
        int shnum;
        int shstrndx;
        if (elf64) {
            shoff = u64(40);
            shentsize = u16(58);
            shnum = u16(60);
            shstrndx = u16(62);
        } else {
            shoff = u32(32);
            shentsize = u16(46);
            shnum = u16(48);
            shstrndx = u16(50);
        }
        if (shnum == 0) {
            // Extended numbering: real count sits in section zero's sh_size.
            if (shoff == 0) {
                return new ArrayList<>();
            }
            int zeroEnt = elf64 ? 64 : 40;
            requireRange(shoff, zeroEnt, "section header[0]");
            shnum = (int) (elf64 ? u64(shoff + 32) : u32(shoff + 20));
            if (shstrndx == 0xffff) {
                shstrndx = (int) (elf64 ? u64(shoff + 24) : u32(shoff + 16));
            }
        }
        int ent = shentsize == 0 ? (elf64 ? 64 : 40) : shentsize;
        long bytesNeeded = (long) shnum * ent;
        if (shnum < 0 || shoff < 0) {
            throw new ElfFormatException("invalid section header offset/count");
        }
        requireRange(shoff, bytesNeeded, "section headers");

        List<ElfSection> result = new ArrayList<>(shnum);
        for (int i = 0; i < shnum; i++) {
            long base = shoff + (long) i * ent;
            if (elf64) {
                int nameOff = (int) u32(base);
                int sType = (int) u32(base + 4);
                long sFlags = u64(base + 8);
                long addr = u64(base + 16);
                long off = u64(base + 24);
                long size = u64(base + 32);
                long link = u32(base + 40);
                long info = u64(base + 48);
                long align = u64(base + 56);
                long entsize = u64(base + 56 + 8 > base + 64 ? base + 56 : base + 56);
                long entrySize = u64(base + 56 + 8);
                result.add(new ElfSection(i, "", sType, sFlags, addr, off, size,
                        link, info, align, entrySize));
            } else {
                int nameOff = (int) u32(base);
                int sType = (int) u32(base + 4);
                long sFlags = u32(base + 8);
                long addr = u32(base + 12);
                long off = u32(base + 16);
                long size = u32(base + 20);
                long link = u32(base + 24);
                long info = u32(base + 28);
                long align = u32(base + 32);
                long entrySize = u32(base + 36);
                result.add(new ElfSection(i, "", sType, sFlags, addr, off, size,
                        link, info, align, entrySize));
            }
        }
        return result;
    }

    private void assignSectionNames(List<ElfSection> sections,
                                    Map<Integer, StringTable> tables) throws ElfFormatException {
        if (sections.isEmpty()) {
            return;
        }
        int shstrndx;
        if (elf64) {
            shstrndx = u16(62);
        } else {
            shstrndx = u16(50);
        }
        StringTable names = tables.get(shstrndx);
        if (names == null) {
            return;
        }
        for (int i = 0; i < sections.size(); i++) {
            ElfSection s = sections.get(i);
            if (s.type() != ElfSection.SHT_NULL) {
                sections.set(i, new ElfSection(i, names.at((int) sections.get(i).name().isEmpty() ? 0 : 0),
                        s.type(), s.flags(), s.address(), s.offset(), s.size(),
                        s.link(), s.info(), s.alignment(), s.entrySize()));
            }
        }
        // redo properly with the name offset stored nowhere; instead parse once more
    }
