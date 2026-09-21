package com.example.symbolcourt.elf;

import com.example.symbolcourt.domain.Models.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ElfParser {
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_RELA = 4;
    private static final int SHT_GROUP = 17;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_REL = 9;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final int DT_NEEDED = 1;

    public ParsedObject parse(String id, String displayName, String path, byte[] data) {
        return parse(id, displayName, null, null, path, data);
    }

    ParsedObject parse(String id, String displayName, String archiveName, String memberName,
                       String path, byte[] data) {
        try {
            return doParse(id, displayName, archiveName, memberName, path, data);
        } catch (ElfFormatException | RuntimeException ex) {
            return new ParsedObject(id, displayName, archiveName, memberName, path, 0, "unknown",
                    "unknown", 0, "unknown", List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), "损坏 ELF：" + ex.getMessage(), memberName != null, false);
        }
    }

    private ParsedObject doParse(String id, String displayName, String archiveName, String memberName,
                                 String path, byte[] data) throws ElfFormatException {
        if (data == null || data.length < 16 || data[0] != 0x7f || data[1] != 'E'
                || data[2] != 'L' || data[3] != 'F') {
            throw new ElfFormatException("不是 ELF magic");
        }
        int elfClass = data[4] & 0xff;
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfFormatException("非法 ELFCLASS " + elfClass);
        }
        boolean elf64 = elfClass == 2;
        int endian = data[5] & 0xff;
        if (endian != 1 && endian != 2) {
            throw new ElfFormatException("非法 EI_DATA " + endian);
        }
        ElfReader r = new ElfReader(data, endian == 1);
        int machine = r.u16(18);
        int type = r.u16(16);
        long sectionHeaderOffset = elf64 ? r.u64(40) : r.u32(32);
        int sectionHeaderEntrySize = r.u16(elf64 ? 58 : 46);
        int sectionCount = r.u16(elf64 ? 60 : 48);
        int sectionNameIndex = r.u16(elf64 ? 62 : 50);
        if (sectionCount == 0 || sectionHeaderOffset == 0) {
            throw new ElfFormatException("缺少 section header，无法离线裁决");
        }
        long minEntry = elf64 ? 64 : 40;
        if (sectionHeaderEntrySize < minEntry) {
            throw new ElfFormatException("section header entry 过短");
        }
        if (sectionHeaderOffset < 0 || sectionHeaderOffset > data.length
                || sectionCount > (data.length - sectionHeaderOffset) / sectionHeaderEntrySize) {
            throw new ElfFormatException("section header 表越界");
        }
        List<Section> sections = parseSections(r, elf64, sectionHeaderOffset, sectionCount,
                sectionHeaderEntrySize, data);
        if (sectionNameIndex >= sections.size()) {
            throw new ElfFormatException("shstrndx 越界");
        }
        String[] sectionNames = readStrings(r, sections.get(sectionNameIndex), data);
        List<Section> named = nameSections(sections, sectionNames);
        Map<Integer, VersionNode> versions = new HashMap<>();
        List<VersionEdge> edges = new ArrayList<>();
        parseNeeded(r, named, data);
        parseVerdef(r, named, data, versions, id);
        parseVerneed(r, named, data, versions, id);
        int[] versionIndices = parseVersym(r, named);
        List<ElfSymbol> symbols = parseSymbols(r, named, data, versions, versionIndices, id, displayName,
                archiveName, memberName);
        List<SectionGroup> groups = parseGroups(r, named, symbols, data, sectionNames);
        List<SymbolReference> refs = parseRelocations(r, named, symbols, data, id, displayName,
                archiveName, memberName, machine, elf64);
        refs = addSyntheticReferences(refs, symbols, id, displayName, archiveName, memberName);
        List<String> needed = named.stream()
                .filter(section -> section.type() == 6)
                .flatMap(section -> readDynamicNeeded(r, section, data).stream())
                .toList();
        return new ParsedObject(id, displayName, archiveName, memberName, path, elf64 ? 64 : 32,
                endian == 1 ? "little-endian" : "big-endian", machineName(machine), machine,
                elfTypeName(type), named, symbols, refs, groups, needed, new ArrayList<>(versions.values()),
                edges, null, memberName != null, type == 1);
    }

    private List<Section> parseSections(ElfReader r, boolean elf64, long offset, int count,
                                        int entrySize, byte[] data) throws ElfFormatException {
        List<Section> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long base = offset + (long) i * entrySize;
            r.check(base, entrySize);
            int nameOff = r.u16(base);
            int type = (int) r.u32(base + 4);
            long flags = elf64 ? r.u64(base + 8) : r.u32(base + 8);
            long address = elf64 ? r.u64(base + 16) : r.u32(base + 16);
            long sectionOffset = elf64 ? r.u64(base + 24) : r.u32(base + 20);
            long link = elf64 ? r.u64(base + 40) : r.u32(base + 24);
            long info = elf64 ? r.u64(base + 48) : r.u32(base + 28);
            long entry = elf64 ? r.u64(base + 56) : r.u32(base + 36);
            long size = elf64 ? r.u64(base + 32) : r.u32(base + 28);
            if (type != 8) {
                r.checkSection(sectionOffset, size);
            }
            result.add(new Section(i, "", nameOff, type, address, sectionOffset, size, link, info,
                    flags, entry));
        }
        return result;
    }

    private String[] readStrings(ElfReader r, Section section, byte[] data) throws ElfFormatException {
        if (section.size() == 0) {
            return new String[0];
        }
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (long p = section.offset(); p < section.offset() + section.size(); p++) {
            if (data[(int) p] == 0 && p + 1 < section.offset() + section.size()) {
                offsets.add((int) (p + 1 - section.offset()));
            }
        }
        String[] names = new String[(int) section.size()];
        for (Integer off : offsets) {
            names[off] = r.cString(section.offset() + off);
        }
        return names;
    }

    private List<Section> nameSections(List<Section> sections, String[] names) {
        List<Section> result = new ArrayList<>();
        for (Section section : sections) {
            int nameOffset = (int) section.nameOffset();
            String name = nameOffset < names.length && names[nameOffset] != null
                    ? names[nameOffset] : "";
            result.add(new Section(section.index(), name, section.nameOffset(), section.type(), section.address(),
                    section.offset(), section.size(), section.link(), section.info(), section.flags(),
                    section.entrySize()));
        }
        return result;
    }

    private List<ElfSymbol> parseSymbols(ElfReader r, List<Section> sections, byte[] data,
                                         Map<Integer, VersionNode> versions, int[] versionIndices,
                                         String objectId, String objectName, String archiveName,
                                         String memberName) throws ElfFormatException {
        List<ElfSymbol> all = new ArrayList<>();
        for (Section section : sections) {
            if (section.type() != SHT_SYMTAB && section.type() != SHT_DYNSYM) {
                continue;
            }
            int link = (int) section.link();
            if (link <= 0 || link >= sections.size()) {
                throw new ElfFormatException("symbol table 字符串表索引非法");
            }
            int entry = (int) (section.entrySize() > 0 ? section.entrySize() : 0);
            if (entry != 16 && entry != 24) {
                throw new ElfFormatException("非法符号表 entry size: " + entry);
            }
            if (section.size() % entry != 0) {
                throw new ElfFormatException("符号表长度不是 entry size 的整数倍");
            }
            int symbolCount = (int) (section.size() / entry);
            for (int index = 0; index < symbolCount; index++) {
                long base = section.offset() + (long) index * entry;
                ElfSymbol symbol = parseSymbolEntry(r, sections.get(link), sections, data, section, base,
                        index, versionIndices, versions, objectId, objectName, archiveName, memberName);
                all.add(symbol);
            }
        }
        return dedupeSymbols(all);
    }

    private ElfSymbol parseSymbolEntry(ElfReader r, Section strtab, List<Section> sections, byte[] data,
                                       Section table, long base, int index, int[] versionIndices,
                                       Map<Integer, VersionNode> versions, String objectId,
                                       String objectName, String archiveName, String memberName)
            throws ElfFormatException {
        boolean elf64 = table.entrySize() == 24;
        long nameOffset = r.u32(base);
        String name = nameOffset < strtab.size() ? r.cString(strtab.offset() + nameOffset) : "";
        int info;
        int other;
        int sectionIndex;
        long value;
        long size;
        if (elf64) {
            info = r.u8(base + 4);
            other = r.u8(base + 5);
            sectionIndex = r.u16(base + 6);
            value = r.u64(base + 8);
            size = r.u64(base + 16);
        } else {
            value = r.u32(base + 4);
            size = r.u32(base + 8);
            info = r.u8(base + 12);
            other = r.u8(base + 13);
            sectionIndex = r.u16(base + 14);
        }
        int binding = info >> 4;
        int type = info & 15;
        int visibility = other & 3;
        int versionIndex = index < versionIndices.length ? versionIndices[index] : 0;
        VersionNode version = versions.get(versionIndex & 0x7fff);
        boolean hiddenVersion = (versionIndex & 0x8000) != 0;
        String sectionName = sectionIndex < sections.size() ? sections.get(sectionIndex).name()
                : specialSectionName(sectionIndex);
        boolean defined = sectionIndex > 0 && sectionIndex < sections.size();
        boolean common = sectionIndex == 0xfff2;
        String visibilityName = visibilityName(visibility);
        String bindingName = bindingName(binding);
        boolean exported = defined && binding != 0 && visibility != 2;
        return new ElfSymbol(objectId, objectName, archiveName, memberName, index, name, versionIndex,
                version == null ? null : version.name(), version == null ? List.of() : version.aliases(),
                version != null && version.defaultVersion(), hiddenVersion, bindingName, typeName(type),
                visibilityName, sectionIndex, sectionName, value, size, table.type() == SHT_DYNSYM,
                defined, common, exported);
    }

    private List<ElfSymbol> dedupeSymbols(List<ElfSymbol> symbols) {
        Map<String, ElfSymbol> unique = new LinkedHashMap<>();
        for (ElfSymbol symbol : symbols) {
            if (symbol.name().isEmpty() && symbol.symbolIndex() == 0) {
                continue;
            }
            String key = symbol.dynamic() + "|" + symbol.name() + "|" + symbol.versionIndex() + "|"
                    + symbol.symbolIndex();
            unique.putIfAbsent(key, symbol);
        }
        return new ArrayList<>(unique.values());
    }

    private void parseNeeded(ElfReader r, List<Section> sections, byte[] data) {
        sections.stream().filter(section -> section.type() == 6).forEach(
                section -> readDynamicNeeded(r, section, data));
    }

    private List<String> readDynamicNeeded(ElfReader r, Section dynamic, byte[] data) {
        List<String> names = new ArrayList<>();
        try {
            for (long p = dynamic.offset(); p + 16 <= dynamic.offset() + dynamic.size(); p += 16) {
                long tag = r.u64(p);
                long value = r.u64(p + 8);
                if (tag == 0) break;
                if (tag == DT_NEEDED) names.add("DT_NEEDED#" + value);
            }
        } catch (ElfFormatException ignored) {
            return names;
        }
        return names;
    }

    private void parseVerdef(ElfReader r, List<Section> sections, byte[] data,
                             Map<Integer, VersionNode> versions, String objectId)
            throws ElfFormatException {
        for (Section section : sections) {
            if (section.type() != SHT_GNU_VERDEF) continue;
            if (section.link() <= 0 || section.link() >= sections.size()) {
                throw new ElfFormatException("VERDEF 字符串表越界");
            }
            Section strings = sections.get((int) section.link());
            long p = section.offset();
            long end = p + section.size();
            while (p + 20 <= end) {
                int flags = r.u16(p);
                int ndx = r.u16(p + 2);
                long count = r.u16(p + 4);
                long aux = r.u32(p + 10);
                long next = r.u32(p + 16);
                long auxBase = p + aux;
                List<String> aliases = new ArrayList<>();
                String first = null;
                for (long n = 0; n < count && auxBase + 8 <= end; n++) {
                    String alias = r.cString(strings.offset() + r.u32(auxBase + 4));
                    aliases.add(alias);
                    first = first == null ? alias : first;
                    long auxNext = r.u32(auxBase);
                    if (auxNext == 0) break;
                    auxBase += auxNext;
                }
                versions.put(ndx, new VersionNode(objectId + ":verdef:" + ndx, objectId, ndx, first,
                        VersionNode.Kind.DEFINITION, (flags & 1) != 0, (flags & 2) != 0,
                        List.copyOf(aliases), null, null));
                if (next == 0) break;
                p += next;
            }
        }
    }

    private void parseVerneed(ElfReader r, List<Section> sections, byte[] data,
                              Map<Integer, VersionNode> versions, String objectId)
            throws ElfFormatException {
        for (Section section : sections) {
            if (section.type() != SHT_GNU_VERNEED) continue;
            if (section.link() <= 0 || section.link() >= sections.size()) {
                throw new ElfFormatException("VERNEED 字符串表越界");
            }
            Section strings = sections.get((int) section.link());
            long p = section.offset();
            long end = p + section.size();
            while (p + 16 <= end) {
                String fileName = r.cString(strings.offset() + r.u32(p + 4));
                long aux = r.u32(p + 8);
                long next = r.u32(p + 12);
                long auxBase = p + aux;
                String parentId = objectId + ":verneed:" + fileName;
                while (auxBase + 16 <= end) {
                    long auxNext = r.u32(auxBase);
                    int other = r.u16(auxBase + 6);
                    int flags = r.u16(auxBase + 12);
                    String versionName = r.cString(strings.offset() + r.u32(auxBase + 8));
                    versions.put(other, new VersionNode(parentId + ":" + other, objectId, other,
                            versionName, VersionNode.Kind.REQUIREMENT, (flags & 1) != 0,
                            (flags & 2) != 0, List.of(versionName), parentId, fileName));
                    if (auxNext == 0) break;
                    auxBase += auxNext;
                }
                if (next == 0) break;
                p += next;
            }
        }
    }

    private int[] parseVersym(ElfReader r, List<Section> sections) throws ElfFormatException {
        for (Section section : sections) {
            if (section.type() == SHT_GNU_VERSYM && section.entrySize() == 2) {
                int count = (int) (section.size() / 2);
                int[] result = new int[count];
                for (int i = 0; i < count; i++) {
                    result[i] = r.u16(section.offset() + i * 2L);
                }
                return result;
            }
        }
        return new int[0];
    }

    private List<SectionGroup> parseGroups(ElfReader r, List<Section> sections,
                                           List<ElfSymbol> symbols, byte[] data,
                                           String[] sectionNames) throws ElfFormatException {
        List<SectionGroup> groups = new ArrayList<>();
        for (Section section : sections) {
            if (section.type() != SHT_GROUP || section.entrySize() != 4) continue;
            if (section.size() < 8 || section.size() % 4 != 0) {
                throw new ElfFormatException("section group 长度非法");
            }
            int flags = (int) r.u32(section.offset());
            List<Integer> members = new ArrayList<>();
            for (long p = section.offset() + 4; p < section.offset() + section.size(); p += 4) {
                int member = (int) r.u32(p);
                if (member <= 0 || member >= sections.size()) {
                    throw new ElfFormatException("section group 成员索引越界");
                }
                members.add(member);
            }
            String signature = symbols.stream()
                    .filter(symbol -> symbol.symbolIndex() == section.info() && !symbol.dynamic())
                    .map(ElfSymbol::name)
                    .findFirst()
                    .orElse("group#" + section.index());
            groups.add(new SectionGroup(signature, flags == 2, List.copyOf(members)));
        }
        return groups;
    }

    private List<SymbolReference> parseRelocations(ElfReader r, List<Section> sections,
                                                    List<ElfSymbol> symbols, byte[] data,
                                                    String objectId, String objectName,
                                                    String archiveName, String memberName,
                                                    int machine, boolean elf64) throws ElfFormatException {
        Map<Integer, ElfSymbol> byIndex = new LinkedHashMap<>();
        for (ElfSymbol symbol : symbols) {
            byIndex.putIfAbsent(symbol.symbolIndex(), symbol);
        }
        List<SymbolReference> refs = new ArrayList<>();
        for (Section section : sections) {
            if (section.type() != SHT_REL && section.type() != SHT_RELA) continue;
            int entry = (int) section.entrySize();
            int expected = elf64 ? (section.type() == SHT_RELA ? 24 : 16)
                    : (section.type() == SHT_RELA ? 12 : 8);
            if (entry != expected || section.size() % entry != 0) {
                throw new ElfFormatException("relocation 表长度非法");
            }
            int targetSectionIndex = (int) section.info();
            if (targetSectionIndex <= 0 || targetSectionIndex >= sections.size()) {
                throw new ElfFormatException("relocation 目标 section 越界");
            }
            String targetSection = sections.get(targetSectionIndex).name();
            for (long p = section.offset(); p < section.offset() + section.size(); p += entry) {
                long offset = r.u64(p);
                long info;
                int symbolIndex;
                int typeCode;
                long addend;
                if (elf64) {
                    info = r.u64(p + 8);
                    symbolIndex = (int) (info >>> 32);
                    typeCode = (int) info;
                    addend = section.type() == SHT_RELA ? r.u64(p + 16) : 0;
                } else {
                    info = r.u32(p + 4);
                    symbolIndex = (int) (info >>> 8);
                    typeCode = (int) (info & 0xff);
                    addend = section.type() == SHT_RELA ? r.u32(p + 8) : 0;
                }
                ElfSymbol symbol = byIndex.get(symbolIndex);
                if (symbol == null || symbol.name().isEmpty() || symbol.defined() && !symbol.dynamic()) {
                    continue;
                }
                boolean copy = RelocationTypes.isCopy(machine, typeCode);
                refs.add(new SymbolReference(objectId + ":rel:" + refs.size(), objectId, objectName,
                        archiveName, memberName, symbol.name(),
                        symbol.versionIndex() == 0 ? null : symbol.versionName(), symbol.binding(),
                        "GLOBAL".equals(symbol.binding()) || "WEAK".equals(symbol.binding()) && !copy,
                        RelocationTypes.name(machine, typeCode), typeCode, symbolIndex, targetSection,
                        offset, addend, copy, symbol.dynamic()));
            }
        }
        return refs;
    }

    private List<SymbolReference> addSyntheticReferences(List<SymbolReference> relocations,
                                                          List<ElfSymbol> symbols, String objectId,
                                                          String objectName, String archiveName,
                                                          String memberName) {
        List<SymbolReference> result = new ArrayList<>(relocations);
        Set<String> present = relocations.stream()
                .map(reference -> reference.symbolName() + "\u0000"
                        + nullToEmpty(reference.requestedVersion()))
                .collect(java.util.stream.Collectors.toSet());
        for (ElfSymbol symbol : symbols) {
            if (symbol.defined() || symbol.common() || symbol.name().isEmpty()
                    || "LOCAL".equals(symbol.binding())) {
                continue;
            }
            String key = symbol.name() + "\u0000" + nullToEmpty(symbol.versionName());
            if (!present.add(key)) {
                continue;
            }
            result.add(new SymbolReference(objectId + ":undef:" + result.size(), objectId, objectName,
                    archiveName, memberName, symbol.name(),
                    symbol.versionIndex() == 0 ? null : symbol.versionName(), symbol.binding(),
                    "GLOBAL".equals(symbol.binding()), "UNDEFINED_SYMBOL", -1, symbol.symbolIndex(),
                    symbol.sectionName(), symbol.value(), 0, false, symbol.dynamic()));
        }
        return result;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String machineName(int machine) {
        return switch (machine) {
            case 3 -> "i386";
            case 62 -> "x86-64";
            case 183 -> "AArch64";
            case 243 -> "RISC-V";
            default -> "EM_" + machine;
        };
    }

    private String elfTypeName(int type) {
        return switch (type) {
            case 1 -> "REL";
            case 2 -> "EXEC";
            case 3 -> "DYN";
            case 4 -> "CORE";
            default -> "ET_" + type;
        };
    }

    private String bindingName(int binding) {
        return switch (binding) {
            case 0 -> "LOCAL";
            case 1 -> "GLOBAL";
            case 2 -> "WEAK";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "STB_" + binding;
        };
    }

    private String typeName(int type) {
        return switch (type) {
            case 0 -> "NOTYPE";
            case 1 -> "OBJECT";
            case 2 -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            case 6 -> "TLS";
            case 10 -> "IFUNC";
            default -> "STT_" + type;
        };
    }

    private String visibilityName(int visibility) {
        return switch (visibility) {
            case 0 -> "DEFAULT";
            case 1 -> "INTERNAL";
            case 2 -> "HIDDEN";
            case 3 -> "PROTECTED";
            default -> "STV_" + visibility;
        };
    }

    private String specialSectionName(int index) {
        return switch (index) {
            case 0 -> "SHN_UNDEF";
            case 0xff00 -> "SHN_LORESERVE";
            case 0xff01 -> "SHN_AFTER";
            case 0xff02 -> "SHN_AMD64_LCOMMON";
            case 0xfff1 -> "SHN_XINDEX";
            case 0xfff2 -> "SHN_COMMON";
            case 0xffff -> "SHN_HIRESERVE";
            default -> index >= 0xff00 && index <= 0xffff ? "SHN_SPECIAL_" + index : "UNKNOWN";
        };
    }
}
