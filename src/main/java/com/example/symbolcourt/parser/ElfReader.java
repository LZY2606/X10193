package com.example.symbolcourt.parser;

import com.example.symbolcourt.model.CourtModels.LoadedObject;
import com.example.symbolcourt.model.CourtModels.Relocation;
import com.example.symbolcourt.model.CourtModels.SectionGroup;
import com.example.symbolcourt.model.CourtModels.SectionInfo;
import com.example.symbolcourt.model.CourtModels.SymbolDef;
import com.example.symbolcourt.model.CourtModels.VersionDefinition;
import com.example.symbolcourt.model.CourtModels.VersionRequirement;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ElfReader {
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_RELA = 4;
    private static final int SHT_GROUP = 17;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_REL = 9;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;

    private static final int STB_LOCAL = 0;
    private static final int STB_GLOBAL = 1;
    private static final int STB_WEAK = 2;
    private static final int STT_COMMON = 5;
    private static final int STV_DEFAULT = 0;
    private static final int STV_INTERNAL = 1;
    private static final int STV_HIDDEN = 2;
    private static final int STV_PROTECTED = 3;
    private static final int SHN_UNDEF = 0;
    private static final int SHN_COMMON = 0xfff2;
    private static final int SHN_XINDEX = 0xffff;

    private static final int DT_NULL = 0;
    private static final int DT_NEEDED = 1;
    private static final int DT_SONAME = 14;

    private static final int VER_NDX_LOCAL = 0;
    private static final int VER_NDX_GLOBAL = 1;
    private static final int VERSYM_HIDDEN = 0x8000;
    private static final int VER_FLG_BASE = 1;

    private final byte[] data;
    private boolean elf64;
    private boolean littleEndian;

    private ElfReader(byte[] data) {
        this.data = data;
    }

    public static LoadedObject read(byte[] data, String fileName, String archiveName, String memberName,
                                   long memberOffset, int loadOrder, String id) throws ElfFormatException {
        ElfReader reader = new ElfReader(data);
        return reader.readObject(fileName, archiveName, memberName, memberOffset, loadOrder, id);
    }

    private LoadedObject readObject(String fileName, String archiveName, String memberName,
                                    long memberOffset, int loadOrder, String id) throws ElfFormatException {
        require(data.length >= 16, "ELF header truncated");
        require(data[0] == 0x7f && data[1] == 'E' && data[2] == 'L' && data[3] == 'F', "bad ELF magic");
        int clazz = u8(4);
        require(clazz == 1 || clazz == 2, "unsupported ELF class");
        elf64 = clazz == 2;
        int encoding = u8(5);
        require(encoding == 1 || encoding == 2, "unsupported ELF encoding");
        littleEndian = encoding == 1;
        require(u8(6) == 1, "unsupported ELF version");

        LoadedObject object = new LoadedObject();
        object.id = id;
        object.fileName = fileName;
        object.archiveName = archiveName;
        object.memberName = memberName;
        object.displayName = archiveName == null ? fileName : fileName + "(" + memberName + ")";
        object.memberOffset = memberOffset;
        object.loadOrder = loadOrder;
        object.archiveMember = archiveName != null;
        object.elf64 = elf64;
        object.littleEndian = littleEndian;

        int ehsize = unsigned(elf64 ? u16(52) : u16(40));
        int shentsize = unsigned(elf64 ? u16(58) : u16(46));
        int shnum = unsigned(elf64 ? u16(60) : u16(48));
        long shoff = elf64 ? u64(40) : u32(32);
        int shstrndx = unsigned(elf64 ? u16(62) : u16(50));
        int expectedEnt = elf64 ? 64 : 40;

        object.elfType = unsigned(elf64 ? u16(16) : u16(16));
        object.machine = unsigned(elf64 ? u16(18) : u16(18));
        object.shared = object.elfType == 3;
        object.relocatable = object.elfType == 1;
        require(ehsize >= (elf64 ? 64 : 52), "bad ELF header size");
        require(shnum >= 0 && shnum < 200000, "bad section count");
        require(shentsize >= expectedEnt, "bad section header size");
        if (shnum == 0) {
            throw new ElfFormatException("section headers are not supported");
        }
        requireRange(shoff, (long) shnum * shentsize, "section headers truncated");

        List<SectionInfo> sections = readSections(shoff, shnum, shentsize);
        if (shstrndx == 0xffff && !sections.isEmpty()) {
            shstrndx = (int) sections.get(0).link;
        }
        require(shstrndx < sections.size(), "bad section name table index");
        readSectionNames(sections, shstrndx);
        object.sections = sections;

        Map<Integer, VersionDefinition> definitions = readVersionDefinitions(sections);
        Map<Integer, VersionRequirement> requirements = readVersionRequirements(sections);
        object.versionDefinitions = new ArrayList<>(definitions.values());
        object.versionRequirements = new ArrayList<>(requirements.values());

        List<SymbolDef> symbols = readAllSymbols(sections, definitions, requirements, object);
        object.symbols = symbols;
        readGroups(sections, symbols);
        object.groups = collectGroups(sections);
        object.relocations = readRelocations(sections, symbols, object.machine);
        object.needed = readNeeded(sections);
        return object;
    }

    private List<SectionInfo> readSections(long shoff, int shnum, int shentsize) throws ElfFormatException {
        List<SectionInfo> sections = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            long base = shoff + (long) i * shentsize;
            SectionInfo section = new SectionInfo();
            section.index = i;
            if (elf64) {
                section.nameOffset = (int) u32(base);
                section.type = (int) u32(base + 4);
                section.address = u64(base + 16);
                section.offset = u64(base + 24);
                section.size = u64(base + 32);
                section.link = (int) u32(base + 40);
                section.info = (int) u32(base + 44);
                section.alignment = u64(base + 48);
            } else {
                section.nameOffset = (int) u32(base);
                section.type = (int) u32(base + 4);
                section.address = u32(base + 12);
                section.offset = u32(base + 16);
                section.size = u32(base + 20);
                section.link = (int) u32(base + 24);
                section.info = (int) u32(base + 28);
                section.alignment = u32(base + 32);
            }
            require(section.link < shnum, "bad section link");
            if (section.type != 8) {
                requireRange(section.offset, section.size, "section data truncated: " + i);
            }
            sections.add(section);
        }
        return sections;
    }

    private void readSectionNames(List<SectionInfo> sections, int shstrndx) throws ElfFormatException {
        String[] names = readStrings(sections.get(shstrndx));
        for (SectionInfo section : sections) {
            section.name = getString(names, section.nameOffset);
        }
    }

    private String[] readStrings(SectionInfo section) throws ElfFormatException {
        requireRange(section.offset, section.size, "string table truncated");
        int size = (int) Math.min(section.size, 16_000_000L);
        String[] result = new String[size];
        int start = (int) section.offset;
        int end = (int) (section.offset + section.size);
        int valueStart = start;
        for (int pos = start; pos < end; pos++) {
            if (data[pos] == 0) {
                result[pos - start] = new String(data, valueStart, pos - valueStart, StandardCharsets.UTF_8);
                valueStart = pos + 1;
            }
        }
        return result;
    }

    private String getString(String[] strings, int offset) {
        if (offset < 0 || offset >= strings.length || strings[offset] == null) {
            return "";
        }
        return strings[offset];
    }

    private List<SymbolDef> readAllSymbols(List<SectionInfo> sections,
                                          Map<Integer, VersionDefinition> definitions,
                                          Map<Integer, VersionRequirement> requirements,
                                          LoadedObject object) throws ElfFormatException {
        List<SymbolDef> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SectionInfo section : sections) {
            if (section.type != SHT_SYMTAB && section.type != SHT_DYNSYM) {
                continue;
            }
            require(section.link < sections.size(), "symbol string table out of range");
            String[] strings = readStrings(sections.get(section.link));
            int entrySize = elf64 ? 24 : 16;
            require(section.size <= Integer.MAX_VALUE, "symbol table too large");
            require(section.size % entrySize == 0, "bad symbol table size");
            int[] extended = readExtendedSectionIndexes(sections, section);
            for (int index = 0; index < section.size / entrySize; index++) {
                SymbolDef symbol = readSymbol(section.offset + (long) index * entrySize, index, strings, extended);
                symbol.dynamic = section.type == SHT_DYNSYM;
                symbol.sectionName = symbol.sectionIndex >= 0 && symbol.sectionIndex < sections.size()
                        ? sections.get(symbol.sectionIndex).name : "";
                String dedupe = section.type + ":" + index;
                if (seen.add(dedupe)) {
                    result.add(symbol);
                }
            }
        }
        attachVersions(sections, result, definitions, requirements, object.shared);
        return result;
    }

    private int[] readExtendedSectionIndexes(List<SectionInfo> sections, SectionInfo symbolTable)
            throws ElfFormatException {
        for (SectionInfo candidate : sections) {
            if (candidate.type == 0x6fffffff && candidate.link == symbolTable.index && candidate.size % 4 == 0) {
                int count = (int) (candidate.size / 4);
                requireRange(candidate.offset, candidate.size, "extended section index table truncated");
                int[] values = new int[count];
                for (int i = 0; i < count; i++) {
                    values[i] = (int) u32(candidate.offset + 4L * i);
                }
                return values;
            }
        }
        return null;
    }

    private SymbolDef readSymbol(long base, int index, String[] strings, int[] extended) throws ElfFormatException {
        SymbolDef symbol = new SymbolDef();
        symbol.index = index;
        if (elf64) {
            symbol.name = getString(strings, (int) u32(base));
            symbol.info = u8(base + 4);
            symbol.other = u8(base + 5);
            symbol.sectionIndex = (short) u16(base + 6);
            symbol.value = u64(base + 8);
            symbol.size = u64(base + 16);
        } else {
            symbol.name = getString(strings, (int) u32(base));
            symbol.value = u32(base + 4);
            symbol.size = u32(base + 8);
            symbol.info = u8(base + 12);
            symbol.other = u8(base + 13);
            symbol.sectionIndex = (short) u16(base + 14);
        }
        symbol.bind = symbol.info >>> 4;
        symbol.type = symbol.info & 0xf;
        symbol.visibility = symbol.other & 3;
        if (symbol.sectionIndex == SHN_XINDEX) {
            require(extended != null && index < extended.length, "extended section index missing");
            symbol.sectionIndex = extended[index];
        }
        symbol.common = symbol.type == STT_COMMON || symbol.sectionIndex == SHN_COMMON;
        symbol.defined = symbol.sectionIndex != SHN_UNDEF && symbol.sectionIndex >= 0;
        if (symbol.common) {
            symbol.defined = true;
        }
        return symbol;
    }

    private void attachVersions(List<SectionInfo> sections, List<SymbolDef> symbols,
                                Map<Integer, VersionDefinition> definitions,
                                Map<Integer, VersionRequirement> requirements,
                                boolean shared) throws ElfFormatException {
        for (SectionInfo section : sections) {
            if (section.type != SHT_GNU_VERSYM || section.size / 2 < symbols.size()) {
                continue;
            }
            int dynamicIndex = 0;
            for (SymbolDef symbol : symbols) {
                if (!symbol.dynamic) {
                    continue;
                }
                if (dynamicIndex * 2L >= section.size) {
                    break;
                }
                int raw = (int) u16(section.offset + 2L * dynamicIndex);
                int index = raw & ~VERSYM_HIDDEN;
                symbol.rawVersionIndex = raw;
                symbol.versionIndex = index;
                symbol.nonDefaultVersion = (raw & VERSYM_HIDDEN) != 0;
                if (index == VER_NDX_LOCAL) {
                    symbol.versionName = "(local)";
                } else if (index == VER_NDX_GLOBAL) {
                    symbol.versionName = "(*global*)";
                } else if (definitions.containsKey(index)) {
                    VersionDefinition definition = definitions.get(index);
                    symbol.versionName = firstVersionName(definition.names);
                    symbol.nonDefaultVersion |= (definition.flags & 1) == 0 && definition.index > 1;
                } else if (requirements.containsKey(index)) {
                    symbol.versionName = requirements.get(index).versionName;
                } else {
                    symbol.versionName = "(missing " + index + ")";
                }
                dynamicIndex++;
            }
        }
    }

    private String firstVersionName(List<String> names) {
        return names.isEmpty() ? "(anonymous)" : names.get(0);
    }

    private Map<Integer, VersionDefinition> readVersionDefinitions(List<SectionInfo> sections)
            throws ElfFormatException {
        Map<Integer, VersionDefinition> result = new HashMap<>();
        for (SectionInfo section : sections) {
            if (section.type != SHT_GNU_VERDEF) {
                continue;
            }
            require(section.link < sections.size(), "version definition string table bad");
            String[] strings = readStrings(sections.get(section.link));
            long cursor = section.offset;
            long end = section.offset + section.size;
            while (cursor + 20 <= end) {
                int version = (int) u16(cursor);
                int flags = (int) u16(cursor + 2);
                int ndx = (int) u16(cursor + 4);
                int cnt = (int) u16(cursor + 6);
                long aux = u32(cursor + 8);
                long next = u32(cursor + 12);
                require(version == 1, "unsupported Verdef version");
                VersionDefinition definition = new VersionDefinition();
                definition.index = ndx;
                definition.flags = flags;
                List<String> names = new ArrayList<>();
                long auxCursor = cursor + aux;
                for (int i = 0; i < cnt && auxCursor + 8 <= end; i++) {
                    int nameOffset = (int) u32(auxCursor);
                    long auxNext = u32(auxCursor + 4);
                    names.add(getString(strings, nameOffset));
                    if (auxNext == 0) {
                        break;
                    }
                    auxCursor += auxNext;
                }
                definition.names = names;
                result.put(ndx, definition);
                if (next == 0) {
                    break;
                }
                cursor += next;
            }
        }
        return result;
    }

    private Map<Integer, VersionRequirement> readVersionRequirements(List<SectionInfo> sections)
            throws ElfFormatException {
        Map<Integer, VersionRequirement> result = new HashMap<>();
        for (SectionInfo section : sections) {
            if (section.type != SHT_GNU_VERNEED) {
                continue;
            }
            require(section.link < sections.size(), "version requirement string table bad");
            String[] strings = readStrings(sections.get(section.link));
            long cursor = section.offset;
            long end = section.offset + section.size;
            while (cursor + 16 <= end) {
                int version = (int) u16(cursor);
                int count = (int) u16(cursor + 2);
                int fileOffset = (int) u32(cursor + 4);
                long aux = u32(cursor + 8);
                long next = u32(cursor + 12);
                require(version == 1, "unsupported Verneed version");
                String fileName = getString(strings, fileOffset);
                long auxCursor = cursor + aux;
                for (int i = 0; i < count && auxCursor + 16 <= end; i++) {
                    int flags = (int) u16(auxCursor);
                    int other = (int) u16(auxCursor + 2);
                    int nameOffset = (int) u32(auxCursor + 4);
                    long auxNext = u32(auxCursor + 12);
                    VersionRequirement requirement = new VersionRequirement();
                    requirement.index = other;
                    requirement.fileName = fileName;
                    requirement.versionName = getString(strings, nameOffset);
                    requirement.versionName = requirement.versionName.isBlank()
                            ? "(required " + other + ")" : requirement.versionName;
                    result.put(other, requirement);
                    if (auxNext == 0) {
                        break;
                    }
                    auxCursor += auxNext;
                }
                if (next == 0) {
                    break;
                }
                cursor += next;
            }
        }
        return result;
    }

    private void readGroups(List<SectionInfo> sections, List<SymbolDef> symbols) throws ElfFormatException {
        for (SectionInfo groupSection : sections) {
            if (groupSection.type != SHT_GROUP) {
                continue;
            }
            require(groupSection.link < sections.size(), "group symbol link bad");
            SectionInfo symbolTable = sections.get(groupSection.link);
            require(symbolTable.type == SHT_SYMTAB || symbolTable.type == SHT_DYNSYM, "group link is not symbol table");
            require(symbolTable.link < sections.size(), "group string table bad");
            String[] strings = readStrings(sections.get(symbolTable.link));
            int entrySize = elf64 ? 24 : 16;
            long signatureSymbol = elf64 ? u32(groupSection.offset + 4) : u32(groupSection.offset + 4);
            require(signatureSymbol * entrySize < symbolTable.size, "group signature out of range");
            String signature = getString(strings,
                    (int) u32(symbolTable.offset + signatureSymbol * entrySize));
            require(groupSection.size % 4 == 0, "bad group size");
            for (long cursor = groupSection.offset + 8; cursor < groupSection.offset + groupSection.size; cursor += 4) {
                int memberSection = (int) u32(cursor);
                if (memberSection < sections.size()) {
                    for (SymbolDef symbol : symbols) {
                        if (symbol.sectionIndex == memberSection) {
                            symbol.groupSignature = signature;
                        }
                    }
                }
            }
        }
    }

    private List<SectionGroup> collectGroups(List<SectionInfo> sections) throws ElfFormatException {
        List<SectionGroup> groups = new ArrayList<>();
        for (SectionInfo groupSection : sections) {
            if (groupSection.type != SHT_GROUP) {
                continue;
            }
            SectionGroup group = new SectionGroup();
            group.groupSectionIndex = groupSection.index;
            group.signature = groupSignature(sections, groupSection);
            List<Integer> members = new ArrayList<>();
            for (long cursor = groupSection.offset + 8; cursor < groupSection.offset + groupSection.size; cursor += 4) {
                members.add((int) u32(cursor));
            }
            group.memberSections = members;
            groups.add(group);
        }
        return groups;
    }

    private String groupSignature(List<SectionInfo> sections, SectionInfo groupSection) throws ElfFormatException {
        SectionInfo symbolTable = sections.get(groupSection.link);
        require(symbolTable.link < sections.size(), "group string table bad");
        String[] strings = readStrings(sections.get(symbolTable.link));
        int entrySize = elf64 ? 24 : 16;
        long signatureSymbol = u32(groupSection.offset + 4);
        require(signatureSymbol * entrySize < symbolTable.size, "group signature out of range");
        return getString(strings, (int) u32(symbolTable.offset + signatureSymbol * entrySize));
    }

    private List<Relocation> readRelocations(List<SectionInfo> sections, List<SymbolDef> symbols, int machine)
            throws ElfFormatException {
        List<Relocation> relocations = new ArrayList<>();
        int counter = 0;
        for (SectionInfo section : sections) {
            if (section.type != SHT_REL && section.type != SHT_RELA) {
                continue;
            }
            require(section.link < sections.size(), "relocation symbol table bad");
            int entrySize = section.type == SHT_RELA ? (elf64 ? 24 : 12) : (elf64 ? 16 : 8);
            require(section.size % entrySize == 0, "bad relocation size");
            int targetIndex = section.info;
            String targetName = targetIndex < sections.size() ? sections.get(targetIndex).name : "";
            for (long cursor = section.offset; cursor < section.offset + section.size; cursor += entrySize) {
                Relocation relocation = new Relocation();
                relocation.id = "R" + (counter++);
                relocation.sectionIndex = targetIndex;
                relocation.sectionName = targetName;
                if (elf64) {
                    relocation.offset = u64(cursor);
                    long info = u64(cursor + 8);
                    relocation.symbolIndex = (int) (info >>> 32);
                    relocation.type = (int) info;
                    relocation.addend = section.type == SHT_RELA ? u64(cursor + 16) : 0;
                } else {
                    relocation.offset = u32(cursor);
                    long info = u32(cursor + 4);
                    relocation.symbolIndex = (int) (info >>> 8);
                    relocation.type = (int) info;
                    relocation.addend = section.type == SHT_RELA ? u32(cursor + 8) : 0;
                }
                if (relocation.symbolIndex < symbols.size()) {
                    relocation.symbolName = symbols.get(relocation.symbolIndex).name;
                }
                relocation.copy = isCopyRelocation(relocation.type, machine);
                relocations.add(relocation);
            }
        }
        return relocations;
    }

    private boolean isCopyRelocation(int type, int machine) {
        return (machine == 62 && type == 5)
                || (machine == 3 && type == 10)
                || (machine == 40 && type == 21)
                || (machine == 183 && type == 8)
                || (machine == 243 && type == 4);
    }

    private List<String> readNeeded(List<SectionInfo> sections) throws ElfFormatException {
        List<String> needed = new ArrayList<>();
        for (SectionInfo dynamic : sections) {
            if (dynamic.type != 6) {
                continue;
            }
            Integer stringTableIndex = null;
            for (SectionInfo candidate : sections) {
                if (candidate.type == SHT_STRTAB && candidate.offset > 0) {
                    stringTableIndex = candidate.index;
                }
            }
            for (long cursor = dynamic.offset; cursor + (elf64 ? 16 : 8) <= dynamic.offset + dynamic.size; ) {
                long tag;
                long value;
                if (elf64) {
                    tag = u64(cursor);
                    value = u64(cursor + 8);
                    cursor += 16;
                } else {
                    tag = u32(cursor);
                    value = u32(cursor + 4);
                    cursor += 8;
                }
                if (tag == DT_NULL) {
                    break;
                }
                if ((tag == DT_NEEDED || tag == DT_SONAME) && stringTableIndex != null) {
                    String[] strings = readStrings(sections.get(stringTableIndex));
                    needed.add(getString(strings, (int) value));
                }
            }
        }
        return needed;
    }

    private int u8(long offset) throws ElfFormatException {
        require(offset >= 0 && offset < data.length, "read past end of ELF");
        return data[(int) offset] & 0xff;
    }

    private int u16(long offset) throws ElfFormatException {
        requireRange(offset, 2, "16-bit field truncated");
        if (littleEndian) {
            return (data[(int) offset] & 0xff) | ((data[(int) offset + 1] & 0xff) << 8);
        }
        return ((data[(int) offset] & 0xff) << 8) | (data[(int) offset + 1] & 0xff);
    }

    private long u32(long offset) throws ElfFormatException {
        requireRange(offset, 4, "32-bit field truncated");
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int byteValue = data[(int) offset + i] & 0xff;
            value |= (long) byteValue << (littleEndian ? 8 * i : 8 * (3 - i));
        }
        return value & 0xffffffffL;
    }

    private long u64(long offset) throws ElfFormatException {
        requireRange(offset, 8, "64-bit field truncated");
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int byteValue = data[(int) offset + i] & 0xff;
            value |= (long) byteValue << (littleEndian ? 8 * i : 8 * (7 - i));
        }
        return value;
    }

    private int unsigned(int value) {
        return value & 0xffff;
    }

    private void requireRange(long offset, long length, String message) throws ElfFormatException {
        require(offset >= 0 && length >= 0 && offset <= data.length && offset + length <= data.length, message);
    }

    private void require(boolean condition, String message) throws ElfFormatException {
        if (!condition) {
            throw new ElfFormatException(message);
        }
    }
}
