package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElfReader {

    private final byte[] data;
    private final ByteReader r;
    private final boolean elf64;
    private final boolean bigEndian;
    private int machine;

    private List<ElfSection> sections;
    private List<int[]> xindexRows;

    public ElfReader(byte[] data) {
        this.data = data;
        if (data == null || data.length < 16) {
            throw new ElfParseException("file too short for ELF identification");
        }
        if (data[0] != Constants.ELFMAG0 || data[1] != Constants.ELFMAG1
                || data[2] != Constants.ELFMAG2 || data[3] != Constants.ELFMAG3) {
            throw new ElfParseException("bad ELF magic");
        }
        int elfClass = data[4] & 0xff;
        if (elfClass != Constants.ELFCLASS32 && elfClass != Constants.ELFCLASS64) {
            throw new ElfParseException("unknown EI_CLASS " + elfClass);
        }
        int encoding = data[5] & 0xff;
        if (encoding != Constants.ELFDATA2LSB && encoding != Constants.ELFDATA2MSB) {
            throw new ElfParseException("unknown EI_DATA " + encoding);
        }
        this.elf64 = elfClass == Constants.ELFCLASS64;
        this.bigEndian = encoding == Constants.ELFDATA2MSB;
        this.r = new ByteReader(data, bigEndian, 16);
    }

    public ElfFile parse(String sourceName) {
        int type;
        int flags;
        long entry;
        int sectionHeaderOffset;
        int sectionHeaderEntrySize;
        int sectionHeaderCount;
        int sectionStringIndex;
        if (elf64) {
            r.seek(16);
            type = r.u16();
            machine = r.u16();
            r.u32();
            entry = r.u64();
            r.u64();
            long shoff = r.u64();
            flags = (int) r.u32();
            r.u16();
            sectionHeaderEntrySize = r.u16();
            sectionHeaderCount = r.u16();
            sectionStringIndex = r.u16();
            if (shoff < 0 || shoff > Integer.MAX_VALUE) {
                throw new ElfParseException("unsupported 64-bit section header offset");
            }
            sectionHeaderOffset = (int) shoff;
        } else {
            r.seek(16);
            type = r.u16();
            machine = r.u16();
            r.u32();
            entry = r.u32() & 0xffffffffL;
            r.u32();
            long shoff = r.u32();
            flags = (int) r.u32();
            r.u16();
            sectionHeaderEntrySize = r.u16();
            sectionHeaderCount = r.u16();
            sectionStringIndex = r.u16();
            sectionHeaderOffset = (int) shoff;
        }
        if (sectionHeaderCount == 0 || sectionHeaderOffset == 0) {
            throw new ElfParseException("object has no section header table");
        }
        parseSections(sectionHeaderOffset, sectionHeaderEntrySize, sectionHeaderCount);
        String shstr = readStringTable(sectionStringIndex);
        applySectionNames(shstr);
        parseSymtabShndx();

        List<ElfSymbol> staticSymbols = parseSymbolTable(Constants.SHT_SYMTAB, false);
        List<ElfSymbol> dynamicSymbols = parseSymbolTable(Constants.SHT_DYNSYM, true);
        List<Integer> staticVersions = parseVersymFor(Constants.SHT_SYMTAB, staticSymbols.size());
        List<Integer> dynamicVersions = parseVersymFor(Constants.SHT_DYNSYM, dynamicSymbols.size());

        List<VersionDef> versionDefs = parseVersionDefs();
        List<VersionNeed> versionNeeds = parseVersionNeeds();
        attachVersionNames(staticSymbols, staticVersions, versionDefs, versionNeeds);
        attachVersionNames(dynamicSymbols, dynamicVersions, versionDefs, versionNeeds);

        List<ElfRelocation> relocations = parseRelocations();
        List<String> needed = new ArrayList<>();
        String soname = parseDynamic(needed);

        return new ElfFile(elf64, bigEndian, machine, type, flags, entry, sections,
                staticSymbols, dynamicSymbols, relocations, versionDefs, versionNeeds,
                staticVersions, dynamicVersions, needed, soname, sourceName);
    }

    private void parseSections(int off, int entrySize, int count) {
        long tableEnd = (long) off + (long) entrySize * count;
        if (off < 0 || entrySize < 1 || count < 1 || tableEnd > data.length) {
            throw new ElfParseException("section header table out of bounds");
        }
        sections = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            r.seek(off + i * entrySize);
            ElfSection section;
            if (elf64) {
                int nameIndex = r.u16();
                int type = (int) r.u32();
                long flags = r.u64();
                long address = r.u64();
                long offset = r.u64();
                long size = r.u64();
                int link = (int) r.u32();
                int info = (int) r.u32();
                long addralign = r.u64();
                long entsize = r.u64();
                section = new ElfSection(null, nameIndex, type, flags, address, offset, size,
                        link, info, addralign, entsize);
            } else {
                int nameIndex = r.u16();
                int type = r.u32();
                long flags = r.u32();
                long address = r.u32();
                long offset = r.u32();
                long size = r.u32();
                int link = (int) r.u32();
                int info = (int) r.u32();
                long addralign = r.u32();
                long entsize = r.u32();
                section = new ElfSection(null, nameIndex, type, flags, address, offset, size,
                        link, info, addralign, entsize);
            }
            sections.add(section);
        }
    }

    private void applySectionNames(String shstr) {
        for (int i = 0; i < sections.size(); i++) {
            ElfSection s = sections.get(i);
            String name;
            if (s.type == Constants.SHT_NULL) {
                name = "";
            } else {
                try {
                    name = cString(shstr, s.nameIndex);
                } catch (ElfParseException e) {
                    name = "";
                }
            }
            sections.set(i, new ElfSection(name, s.nameIndex, s.type, s.flags, s.address, s.offset, s.size,
                    s.link, s.info, s.addralign, s.entsize));
        }
    }

    private String readStringTable(int index) {
        if (index <= 0 || index >= sections.size()) {
            return "";
        }
        ElfSection s = sections.get(index);
        if (s.type != Constants.SHT_STRTAB) {
            throw new ElfParseException("section " + index + " is not a string table");
        }
        byte[] bytes = r.sliceAt(s.offset, s.size);
        return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static String cString(String table, int offset) {
        if (offset < 0 || offset >= table.length()) {
            throw new ElfParseException("string index out of bounds: " + offset);
        }
        int end = table.indexOf(0, offset);
        if (end < 0) {
            end = table.length();
        }
        return table.substring(offset, end);
    }

    private void parseSymtabShndx() {
        xindexRows = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            xindexRows.add(null);
        }
        for (int i = 0; i < sections.size(); i++) {
            ElfSection s = sections.get(i);
            if (s.type != Constants.SHT_SYMTAB_SHNDX) {
                continue;
            }
            int count = s.entsize == 0 ? 0 : (int) (s.size / s.entsize);
            if (s.entsize != 4) {
                throw new ElfParseException("unsupported SYMTAB_SHNDX entry size " + s.entsize);
            }
            int[] row = new int[count];
            for (int j = 0; j < count; j++) {
                r.seek((int) s.offset + j * 4);
                row[j] = (int) r.u32();
            }
            xindexRows.set(i, row);
        }
    }

    private List<ElfSymbol> parseSymbolTable(int sectionType, boolean dynamic) {
        List<ElfSymbol> symbols = new ArrayList<>();
        for (int idx = 0; idx < sections.size(); idx++) {
            ElfSection s = sections.get(idx);
            if (s.type != sectionType) {
                continue;
            }
            if (s.link <= 0 || s.link >= sections.size()) {
                throw new ElfParseException(s.name + " has invalid sh_link " + s.link);
            }
            String strtab;
            try {
                strtab = readStringTable(s.link);
            } catch (ElfParseException e) {
                throw new ElfParseException(s.name + " string table unreadable: " + e.getMessage());
            }
            int entrySize = (int) s.entsize;
            int expected = elf64 ? 24 : 16;
            if (entrySize == 0) {
                entrySize = expected;
            }
            if (entrySize < expected) {
                throw new ElfParseException(s.name + " entry size " + entrySize + " smaller than " + expected);
            }
            if (s.offset < 0 || entrySize == 0 || s.size > (long) (data.length - s.offset)) {
                throw new ElfParseException(s.name + " body out of bounds");
            }
            int count = (int) (s.size / entrySize);
            int[] xindex = xindexRows.get(idx);
            for (int i = 0; i < count; i++) {
                r.seek((int) s.offset + i * entrySize);
                int nameIndex;
                int binding;
                int type;
                int visibility;
                int shndx;
                long value;
                long size;
                if (elf64) {
                    nameIndex = (int) r.u32();
                    int info = r.u8() & 0xff;
                    int other = r.u8() & 0xff;
                    shndx = r.u16();
                    value = r.u64();
                    size = r.u64();
                    binding = info >> 4;
                    type = info & 0xf;
                    visibility = other & 0x3;
                } else {
                    nameIndex = (int) r.u32();
                    value = r.u32();
                    size = r.u32();
                    int info = r.u8() & 0xff;
                    int other = r.u8() & 0xff;
                    shndx = r.u16();
                    binding = info >> 4;
                    type = info & 0xf;
                    visibility = other & 0x3;
                }
                if (shndx == Constants.SHN_XINDEX) {
                    if (xindex == null || i >= xindex.length) {
                        throw new ElfParseException(s.name + " symbol " + i + " needs SHN_XINDEX table");
                    }
                    shndx = xindex[i];
                }
                String name = i == 0 ? "" : cString(strtab, nameIndex);
                symbols.add(new ElfSymbol(name, nameIndex, binding, type, visibility, shndx,
                        value, size, -1, binding == Constants.STB_LOCAL, dynamic));
            }
            break;
        }
        return symbols;
    }

    private List<Integer> parseVersymFor(int symbolTableType, int symbolCount) {
        List<Integer> versions = new ArrayList<>();
        if (symbolCount == 0) {
            return versions;
        }
        for (ElfSection s : sections) {
            if (!".gnu.version".equals(s.name) || s.type != 0x6fffffff) {
                continue;
            }
            if (s.link < 0 || s.link >= sections.size()
                    || sections.get(s.link).type != symbolTableType) {
                continue;
            }
            int count = (int) (s.size / 2);
            if (count < symbolCount) {
                throw new ElfParseException(".gnu.version has " + count + " entries, symbol table has "
                        + symbolCount);
            }
            for (int i = 0; i < count; i++) {
                r.seek((int) s.offset + i * 2);
                versions.add(r.u16());
            }
            return versions;
        }
        return versions;
    }

    private List<VersionDef> parseVersionDefs() {
        List<VersionDef> defs = new ArrayList<>();
        for (ElfSection s : sections) {
            if (!".gnu.version_d".equals(s.name) || s.type != 0x6ffffffe) {
                continue;
            }
            String strtab = readStringTable(s.link);
            long cursor = s.offset;
            long end = s.offset + s.size;
            int guard = 0;
            while (cursor < end) {
                if (guard++ > 100000) {
                    throw new ElfParseException("version definition chain too long");
                }
                r.seek((int) cursor);
                r.u16();
                int flags = r.u16();
                r.u16();
                int auxCount = r.u16();
                int hash = (int) r.u32();
                int ndx = (int) r.u32();
                long auxOffset = cursor + (r.u32() & 0xffffffffL);
                long nextOffset = cursor + (r.u32() & 0xffffffffL);
                List<String> names = new ArrayList<>();
                long auxCursor = auxCount > 0 ? auxOffset : 0;
                for (int a = 0; a < auxCount; a++) {
                    if (auxCursor <= 0 || auxCursor < s.offset || auxCursor >= end) {
                        throw new ElfParseException("verdaux offset out of section");
                    }
                    r.seek((int) auxCursor);
                    int nameOffset = (int) r.u32();
                    long nextAux = auxCursor + (r.u32() & 0xffffffffL);
                    names.add(cString(strtab, nameOffset));
                    auxCursor = nextAux;
                }
                if (names.isEmpty()) {
                    throw new ElfParseException("version definition has no name");
                }
                defs.add(new VersionDef(ndx, flags, names.get(0),
                        names.subList(1, names.size())));
                if (nextOffset <= cursor || nextOffset >= end) {
                    break;
                }
                cursor = nextOffset;
            }
            return defs;
        }
        return defs;
    }

    private List<VersionNeed> parseVersionNeeds() {
        List<VersionNeed> needs = new ArrayList<>();
        for (ElfSection s : sections) {
            if (!".gnu.version_r".equals(s.name) || s.type != 0x6fffffff) {
                continue;
            }
            String strtab = readStringTable(s.link);
            long cursor = s.offset;
            long end = s.offset + s.size;
            int guard = 0;
            while (cursor < end) {
                if (guard++ > 100000) {
                    throw new ElfParseException("version need chain too long");
                }
                r.seek((int) cursor);
                r.u16();
                int auxCount = r.u16();
                r.u32();
                int fileNameOffset = (int) r.u32();
                long auxOffset = cursor + (r.u32() & 0xffffffffL);
                long nextOffset = cursor + (r.u32() & 0xffffffffL);
                String fileName = cString(strtab, fileNameOffset);
                List<VersionNeed.NeedEntry> entries = new ArrayList<>();
                long auxCursor = auxOffset;
                for (int a = 0; a < auxCount; a++) {
                    if (auxCursor < s.offset || auxCursor + 16 > end) {
                        throw new ElfParseException("vernaux offset out of section");
                    }
                    r.seek((int) auxCursor);
                    int hash = (int) r.u32();
                    int flags = r.u16();
                    int other = r.u16();
                    int nameOffset = (int) r.u32();
                    long nextAux = auxCursor + (r.u32() & 0xffffffffL);
                    entries.add(new VersionNeed.NeedEntry(hash, flags, other,
                            cString(strtab, nameOffset)));
                    auxCursor = nextAux;
                }
                needs.add(new VersionNeed(fileName, entries));
                if (nextOffset <= cursor || nextOffset >= end) {
                    break;
                }
                cursor = nextOffset;
            }
            return needs;
        }
        return needs;
    }

    private void attachVersionNames(List<ElfSymbol> symbols, List<Integer> versym,
                                    List<VersionDef> defs, List<VersionNeed> needs) {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (VersionDef def : defs) {
            names.put(def.ndx & 0x7fff, def.name);
        }
        for (VersionNeed need : needs) {
            for (VersionNeed.NeedEntry entry : need.entries) {
                names.putIfAbsent(entry.other & 0x7fff, entry.name);
            }
        }
        for (int i = 0; i < symbols.size(); i++) {
            ElfSymbol s = symbols.get(i);
            int raw = i < versym.size() ? versym.get(i) : Constants.VER_NDX_GLOBAL;
            int key = raw & 0x7fff;
            String versionName = null;
            if (key > Constants.VER_NDX_GLOBAL && names.containsKey(key)) {
                versionName = names.get(key);
            }
            ElfSymbol updated = new ElfSymbol(s.name, s.nameIndex, s.binding, s.type, s.visibility,
                    s.sectionIndex, s.value, s.size, raw, s.local, s.dynamic);
            symbols.set(i, updated);
        }
    }

    private List<ElfRelocation> parseRelocations() {
        List<ElfRelocation> relocations = new ArrayList<>();
        for (int idx = 0; idx < sections.size(); idx++) {
            ElfSection s = sections.get(idx);
            if (s.type != Constants.SHT_REL && s.type != Constants.SHT_RELA) {
                continue;
            }
            boolean rela = s.type == Constants.SHT_RELA;
            int entrySize = rela ? (elf64 ? 24 : 12) : (elf64 ? 16 : 8);
            if (s.entsize != 0 && s.entsize != entrySize) {
                throw new ElfParseException(s.name + " unexpected entsize " + s.entsize);
            }
            if (s.link < 0 || s.link >= sections.size()) {
                throw new ElfParseException(s.name + " invalid sh_link");
            }
            int targetIndex = s.info;
            if (targetIndex < 0 || targetIndex >= sections.size()) {
                throw new ElfParseException(s.name + " relocates invalid section " + targetIndex);
            }
            long tableEnd = s.offset + s.size;
            if (!r.inRange(s.offset, s.size)) {
                throw new ElfParseException(s.name + " body out of bounds");
            }
            int count = (int) (s.size / entrySize);
            for (int i = 0; i < count; i++) {
                r.seek((int) s.offset + i * entrySize);
                long offset = elf64 ? r.u64() : r.u32() & 0xffffffffL;
                long packed = elf64 ? r.u64() : r.u32() & 0xffffffffL;
                long addend = 0;
                if (rela) {
                    addend = elf64 ? r.u64() : r.u32();
                }
                int symbolIndex = elf64 ? (int) (packed >>> 32) : (int) (packed >>> 8);
                long type = elf64 ? packed & 0xffffffffL : packed & 0xffL;
                relocations.add(new ElfRelocation(offset, type, symbolIndex, addend, rela,
                        idx, targetIndex, isCopyType(type), false));
            }
        }
        return relocations;
    }

    private boolean isCopyType(long type) {
        if (machine == 62) {
            return type == 5;
        }
        if (machine == 183) {
            return type == 1024;
        }
        if (machine == 3) {
            return type == 5;
        }
        if (machine == 243) {
            return type == 8;
        }
        return false;
    }

    private String parseDynamic(List<String> neededOut) {
        String soname = null;
        for (ElfSection s : sections) {
            if (s.type != Constants.SHT_DYNAMIC) {
                continue;
            }
            if (s.link < 0 || s.link >= sections.size()) {
                throw new ElfParseException(".dynamic invalid sh_link");
            }
            String strtab = readStringTable(s.link);
            int entrySize = elf64 ? 16 : 8;
            int count = (int) (s.size / entrySize);
            if (!r.inRange(s.offset, s.size)) {
                throw new ElfParseException(".dynamic body out of bounds");
            }
            for (int i = 0; i < count; i++) {
                r.seek((int) s.offset + i * entrySize);
                long tag = elf64 ? r.u64() : r.u32() & 0xffffffffL;
                long value = elf64 ? r.u64() : r.u32() & 0xffffffffL;
                if (tag == Constants.DT_NULL) {
                    break;
                }
                if (tag == Constants.DT_NEEDED) {
                    neededOut.add(cString(strtab, (int) value));
                } else if (tag == Constants.DT_SONAME && soname == null) {
                    soname = cString(strtab, (int) value);
                }
            }
            return soname;
        }
        return soname;
    }
}
