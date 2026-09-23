package com.example.symbolcourt.parser;

import com.example.symbolcourt.domain.Domain.Binding;
import com.example.symbolcourt.domain.Domain.ComdatGroup;
import com.example.symbolcourt.domain.Domain.ElfObject;
import com.example.symbolcourt.domain.Domain.ElfSymbol;
import com.example.symbolcourt.domain.Domain.Relocation;
import com.example.symbolcourt.domain.Domain.Section;
import com.example.symbolcourt.domain.Domain.SymbolType;
import com.example.symbolcourt.domain.Domain.SymbolVersion;
import com.example.symbolcourt.domain.Domain.VersionNode;
import com.example.symbolcourt.domain.Domain.Visibility;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ElfParser {
    private static final int SHT_PROGBITS = 1;
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_RELA = 4;
    private static final int SHT_GROUP = 17;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_REL = 9;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final long DT_NULL = 0;
    private static final long DT_NEEDED = 1;
    private static final long DT_STRTAB = 5;
    private static final long DT_SONAME = 14;
    private static final long DT_VERDEF = 0x6ffffffc;
    private static final long DT_VERDEFNUM = 0x6ffffffd;
    private static final long DT_VERNEED = 0x6ffffffe;
    private static final long DT_VERNEEDNUM = 0x6fffffff;

    public ElfObject parse(String key, String displayName, String path, String archivePath, String memberName,
                           byte[] data) {
        if (data.length < 16 || data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new BinaryParseException("missing ELF magic");
        }
        int elfClass = data[4] & 0xff;
        if (elfClass != 1 && elfClass != 2) {
            throw new BinaryParseException("unsupported ELF class " + elfClass);
        }
        int endianValue = data[5] & 0xff;
        if (endianValue != 1 && endianValue != 2) {
            throw new BinaryParseException("unsupported ELF endianness " + endianValue);
        }
        boolean littleEndian = endianValue == 1;
        ByteReader reader = new ByteReader(data, littleEndian);
        boolean is64 = elfClass == 2;
        int type = reader.u16(16);
        int machine = reader.u16(18);
        int sectionHeaderOffsetIndex = is64 ? 40 : 32;
        int sectionHeaderSizeIndex = is64 ? 58 : 46;
        int sectionCountIndex = is64 ? 60 : 48;
        int sectionNameIndexIndex = is64 ? 62 : 50;
        long sectionOffset = is64 ? reader.u64(sectionHeaderOffsetIndex) : reader.u32(sectionHeaderOffsetIndex);
        int sectionHeaderSize = reader.u16(sectionHeaderSizeIndex);
        int sectionCount = reader.u16(sectionCountIndex);
        int sectionNameIndex = reader.u16(sectionNameIndexIndex);
        int minimumHeaderSize = is64 ? 64 : 40;
        if (sectionHeaderSize < minimumHeaderSize) {
            throw new BinaryParseException("ELF section header size too small");
        }
        if (sectionCount > 0 && sectionOffset > 0 && multiplyRange(sectionOffset, sectionHeaderSize, sectionCount, data.length)) {
            throw new BinaryParseException("ELF section headers outside binary");
        }
        List<Section> rawSections = parseSections(reader, is64, sectionOffset, sectionHeaderSize, sectionCount);
        String[] sectionNames = parseSectionNames(reader, rawSections, sectionNameIndex);
        List<Section> sections = new ArrayList<>();
        for (Section section : rawSections) {
            sections.add(new Section(section.index(), sectionNames[section.index()], section.type(), section.flags(),
                    section.address(), section.offset(), section.size(), section.link(), section.info(),
                    section.alignment(), section.entrySize()));
        }
        Map<String, Long> dynamicValues = parseDynamic(reader, is64, sections, sectionNames, data);
        List<VersionNode> versions = new ArrayList<>(parseVersions(reader, is64, sections, sectionNames, dynamicValues));
        Map<Integer, SymbolVersion> versionByIndex = parseVersionSymbolTable(reader, is64, sections, versions);
        List<ElfSymbol> symbols = parseSymbols(reader, is64, sections, sectionNames, versionByIndex);
        List<Relocation> relocations = parseRelocations(reader, is64, sections);
        List<ComdatGroup> groups = parseGroups(reader, is64, sections, sectionNames, symbols);
        String soname = readDynamicString(reader, is64, sections, dynamicValues, DT_SONAME);
        List<String> needed = parseNeeded(reader, is64, sections, dynamicValues);
        return new ElfObject(key, displayName, archivePath, memberName, path, elfClass, littleEndian, machine,
                type, soname.isBlank() ? null : soname, needed, sections, symbols, relocations, groups, versions,
                type == 2 || type == 3);
    }

    private boolean multiplyRange(long offset, long headerSize, long count, long length) {
        long end;
        try {
            end = Math.addExact(offset, Math.multiplyExact(headerSize, count));
        } catch (ArithmeticException ex) {
            return true;
        }
        return offset < 0 || end > length;
    }

    private List<Section> parseSections(ByteReader reader, boolean is64, long offset, int headerSize, int count) {
        List<Section> sections = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long pos = offset + (long) index * headerSize;
            long nameOffset = reader.u32(pos);
            long type = is64 ? reader.u32(pos + 4) : reader.u32(pos + 4);
            long flags = is64 ? reader.u64(pos + 8) : reader.u32(pos + 8);
            long address = is64 ? reader.u64(pos + 16) : reader.u32(pos + 12);
            long sectionOffset = is64 ? reader.u64(pos + 24) : reader.u32(pos + 16);
            long size = is64 ? reader.u64(pos + 32) : reader.u32(pos + 20);
            int link = reader.u32(pos + (is64 ? 40 : 24));
            int info = reader.u32(pos + (is64 ? 44 : 28));
            long alignment = is64 ? reader.u64(pos + 48) : reader.u32(pos + 32);
            long entrySize = is64 ? reader.u64(pos + 56) : reader.u32(pos + 36);
            sections.add(new Section(index, "", type, flags, address, sectionOffset, size, link, info,
                    alignment, entrySize));
        }
        return sections;
    }

    private String[] parseSectionNames(ByteReader reader, List<Section> sections, int nameIndex) {
        String[] names = new String[sections.size()];
        if (nameIndex <= 0 || nameIndex >= sections.size()) {
            return names;
        }
        Section stringTable = sections.get(nameIndex);
        byte[] table = sectionBytes(reader, stringTable);
        for (int i = 0; i < sections.size(); i++) {
            names[i] = tableString(table, sections.get(i).name());
        }
        return names;
    }

    private byte[] sectionBytes(ByteReader reader, Section section) {
        return reader.range(section.offset(), section.size());
    }

    private String tableString(byte[] table, long offset) {
        if (offset < 0 || offset >= table.length) {
            return "";
        }
        int end = (int) offset;
        while (end < table.length && table[end] != 0) {
            end++;
        }
        return new String(table, (int) offset, end - (int) offset, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Map<String, Long> parseDynamic(ByteReader reader, boolean is64, List<Section> sections,
                                           String[] names, byte[] data) {
        Map<String, Long> values = new HashMap<>();
        Section dynamic = findSection(sections, SHT_DYNAMIC);
        if (dynamic == null) {
            return values;
        }
        long entrySize = dynamic.entrySize() > 0 ? dynamic.entrySize() : (is64 ? 16 : 8);
        if (dynamic.size() % entrySize != 0) {
            throw new BinaryParseException("invalid dynamic section size");
        }
        for (long pos = dynamic.offset(); pos < dynamic.offset() + dynamic.size(); pos += entrySize) {
            long tag = is64 ? reader.u64(pos) : reader.u32(pos);
            long value = is64 ? reader.u64(pos + 8) : reader.u32(pos + 4);
            if (tag == DT_NULL) {
                break;
            }
            values.put("tag:" + tag, value);
        }
        return values;
    }

    private Section findSection(List<Section> sections, long type) {
        for (Section section : sections) {
            if (section.type() == type) {
                return section;
            }
        }
        return null;
    }

    private List<Section> findSections(List<Section> sections, long type) {
        List<Section> result = new ArrayList<>();
        for (Section section : sections) {
            if (section.type() == type) {
                result.add(section);
            }
        }
        return result;
    }

    private byte[] bytesForDynamicStringTable(ByteReader reader, boolean is64, List<Section> sections,
                                              Map<String, Long> dynamic) {
        Long strtabAddress = dynamic.get("tag:" + DT_STRTAB);
        if (strtabAddress == null) {
            Section strtab = findSection(sections, SHT_STRTAB);
            return strtab == null ? new byte[0] : sectionBytes(reader, strtab);
        }
        for (Section section : sections) {
            if (section.address() != 0 && strtabAddress >= section.address()
                    && strtabAddress < section.address() + Math.max(section.size(), 1)) {
                long delta = strtabAddress - section.address();
                return reader.range(section.offset() + delta, Math.max(0, section.size() - delta));
            }
        }
        Section fallback = findSection(sections, SHT_STRTAB);
        return fallback == null ? new byte[0] : sectionBytes(reader, fallback);
    }

    private String readDynamicString(ByteReader reader, boolean is64, List<Section> sections,
                                     Map<String, Long> dynamic, long tag) {
        Long value = dynamic.get("tag:" + tag);
        if (value == null) {
            return "";
        }
        return tableString(bytesForDynamicStringTable(reader, is64, sections, dynamic), value);
    }

    private List<String> parseNeeded(ByteReader reader, boolean is64, List<Section> sections,
                                     Map<String, Long> dynamic) {
        Section dynamicSection = findSection(sections, SHT_DYNAMIC);
        List<String> needed = new ArrayList<>();
        if (dynamicSection == null) {
            return needed;
        }
        long entrySize = dynamicSection.entrySize() > 0 ? dynamicSection.entrySize() : (is64 ? 16 : 8);
        byte[] strings = bytesForDynamicStringTable(reader, is64, sections, dynamic);
        for (long pos = dynamicSection.offset(); pos < dynamicSection.offset() + dynamicSection.size(); pos += entrySize) {
            long tag = is64 ? reader.u64(pos) : reader.u32(pos);
            long value = is64 ? reader.u64(pos + 8) : reader.u32(pos + 4);
            if (tag == DT_NULL) {
                break;
            }
            if (tag == DT_NEEDED) {
                needed.add(tableString(strings, value));
            }
        }
        return needed;
    }

    private List<VersionNode> parseVersions(ByteReader reader, boolean is64, List<Section> sections,
                                            String[] names, Map<String, Long> dynamic) {
        List<VersionNode> versions = new ArrayList<>();
        versions.add(new VersionNode(0, "LOCAL", 0, false, true, List.of(), List.of()));
        versions.add(new VersionNode(1, "GLOBAL", 0, false, true, List.of(), List.of()));
        parseVerdef(reader, is64, sections, dynamic, versions);
        parseVerneed(reader, is64, sections, dynamic, versions);
        return versions;
    }

    private void parseVerdef(ByteReader reader, boolean is64, List<Section> sections,
                             Map<String, Long> dynamic, List<VersionNode> versions) {
        Section section = findSection(sections, SHT_GNU_VERDEF);
        long start;
        long count;
        if (section != null) {
            start = section.offset();
            count = section.size();
        } else {
            Long address = dynamic.get("tag:" + DT_VERDEF);
            if (address == null) {
                return;
            }
            section = sectionByAddress(sections, address);
            if (section == null) {
                return;
            }
            start = section.offset() + (address - section.address());
            count = section.size() - (address - section.address());
        }
        long cursor = start;
        long limit = start + count;
        Long declared = dynamic.get("tag:" + DT_VERDEFNUM);
        long parsed = 0;
        while ((declared == null || parsed < declared) && cursor < limit) {
            long version = reader.u16(cursor);
            long flags = reader.u16(cursor + 2);
            long nameIndex = is64 ? reader.u32(cursor + 8) : reader.u32(cursor + 8);
            long auxOffset = is64 ? reader.u32(cursor + 12) : reader.u32(cursor + 12);
            long nextOffset = is64 ? reader.u32(cursor + 16) : reader.u32(cursor + 16);
            if (version <= 1) {
                throw new BinaryParseException("invalid verdef index " + version);
            }
            long auxPos = cursor + auxOffset;
            String name = readVersionAuxName(reader, is64, section, auxPos, nameIndex);
            List<String> aliases = new ArrayList<>();
            long nextAux = is64 ? reader.u32(auxPos + 4) : reader.u32(auxPos + 4);
            while (nextAux != 0) {
                auxPos = auxPos + nextAux;
                long aliasOffset = is64 ? reader.u32(auxPos) : reader.u32(auxPos);
                aliases.add(readVersionAuxName(reader, is64, section, auxPos, aliasOffset));
                nextAux = is64 ? reader.u32(auxPos + 4) : reader.u32(auxPos + 4);
            }
            versions.add(new VersionNode((int) version, name, 0, true, (flags & 1) != 0, List.of(), aliases));
            parsed++;
            if (nextOffset == 0) {
                break;
            }
            cursor += nextOffset;
        }
    }

    private String readVersionAuxName(ByteReader reader, boolean is64, Section containing, long auxPos, long nameOffset) {
        long relativeNameOffset = is64 ? reader.u32(auxPos) : reader.u32(auxPos);
        long absolute = containing.offset() + relativeNameOffset;
        if (nameOffset != relativeNameOffset) {
            absolute = containing.offset() + nameOffset;
        }
        return reader.cString(absolute);
    }

    private Section sectionByAddress(List<Section> sections, long address) {
        for (Section section : sections) {
            if (section.address() != 0 && address >= section.address() && address < section.address() + section.size()) {
                return section;
            }
        }
        return null;
    }
