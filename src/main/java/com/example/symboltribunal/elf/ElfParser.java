package com.example.symboltribunal.elf;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ElfParser {
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_SYMTAB_SHNDX = 18;

    public ElfModel.LoadedInput parse(String name, int order, byte[] bytes) {
        if (bytes != null && bytes.length >= 8 && "!<arch>\n".equals(new String(bytes, 0, 8))) {
            return parseArchive(name, order, bytes);
        }
        try {
            return ElfModel.LoadedInput.elf(name, order, parseElf(name, name, order, -1, null, true, bytes));
        } catch (RuntimeException ex) {
            return ElfModel.LoadedInput.corrupt(name, order, ex.getMessage());
        }
    }

    ElfObject parseElf(String sourceName, String objectName, int inputOrder, int memberOrder,
                       String archiveName, boolean explicit, byte[] bytes) {
        try {
            if (bytes.length < 16 || bytes[0] != 0x7f || bytes[1] != 'E' || bytes[2] != 'L' || bytes[3] != 'F') {
                throw new ElfFormatException("不是 ELF 文件");
            }
            int eiClass = bytes[4] & 0xff;
            int eiData = bytes[5] & 0xff;
            boolean elf64 = eiClass == 2;
            if (!elf64 && eiClass != 1) {
                throw new ElfFormatException("未知 ELF class: " + eiClass);
            }
            boolean littleEndian = eiData == 1;
            if (!littleEndian && eiData != 2) {
                throw new ElfFormatException("未知 ELF data encoding: " + eiData);
            }
            SafeBuffer buffer = new SafeBuffer(bytes, littleEndian);
            int type = buffer.u16(16);
            int machine = buffer.u16(18);
            long sectionHeaderOffset = elf64 ? buffer.u64(40) : buffer.u32(32);
            int sectionHeaderSize = buffer.u16(elf64 ? 58 : 46);
            int sectionCount = buffer.u16(elf64 ? 60 : 48);
            int sectionNameIndex = buffer.u16(elf64 ? 62 : 50);
            if (sectionCount == 0) {
                throw new ElfFormatException("缺少 section header");
            }
            if (sectionHeaderSize < (elf64 ? 64 : 40)) {
                throw new ElfFormatException("section header 长度过短");
            }
            List<ElfSection> sections = parseSections(buffer, elf64, sectionHeaderOffset, sectionCount, sectionHeaderSize);
            if (sectionNameIndex >= sections.size()) {
                throw new ElfFormatException("section name 索引越界");
            }
            long sectionStringOffset = sections.get(sectionNameIndex).offset();
            List<ElfSection> named = new ArrayList<>();
            for (ElfSection section : sections) {
                named.add(new ElfSection(section.index(), section.nameIndex(),
                        buffer.stringTable(sectionStringOffset, section.nameIndex()), section.type(),
                        section.flags(), section.address(), section.offset(), section.size(), section.link(),
                        section.info(), section.addressAlignment(), section.entrySize()));
            }
            sections = named;
            Map<Integer, List<Integer>> extendedSectionIndices = parseExtendedIndices(buffer, sections);
            GroupIndex groupIndex = buildGroupIndex(buffer, sections);
            List<VersionDefinition> definitions = parseVersionDefinitions(buffer, sections);
            List<VersionRequirement> requirements = parseVersionRequirements(buffer, sections);
            List<VersionSlot> slots = parseVersionSlots(sections);
            List<ElfSymbol> symbols = parseSymbols(buffer, sections, groupIndex, extendedSectionIndices);
            List<ElfRelocation> relocations = parseRelocations(buffer, elf64, sections);
            DynamicInfo dynamicInfo = parseDynamic(buffer, elf64, sections);
            return new ElfObject(sourceName, objectName, inputOrder, memberOrder, archiveName, explicit,
                    elf64, littleEndian, type, machine, List.copyOf(sections), List.copyOf(symbols),
                    List.copyOf(relocations), List.copyOf(slots), List.copyOf(definitions),
                    List.copyOf(requirements), List.copyOf(dynamicInfo.needed), dynamicInfo.soname,
                    false, null);
        } catch (RuntimeException ex) {
            return new ElfObject(sourceName, objectName, inputOrder, memberOrder, archiveName, explicit,
                    false, true, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), null, true, ex.getMessage());
        }
    }

    private List<ElfSection> parseSections(SafeBuffer buffer, boolean elf64, long offset, int count, int entrySize) {
        List<ElfSection> sections = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long at = offset + (long) index * entrySize;
            int name = (int) buffer.u32(at);
            if (elf64) {
                int type = (int) buffer.u32(at + 4);
                long flags = buffer.u64(at + 8);
                long address = buffer.u64(at + 16);
                long sectionOffset = buffer.u64(at + 24);
                long size = buffer.u64(at + 32);
                int link = (int) buffer.u32(at + 40);
                int info = (int) buffer.u32(at + 44);
                long alignment = buffer.u64(at + 48);
                long entrySize = buffer.u64(at + 56);
                sections.add(new ElfSection(index, name, "", type, flags, address, sectionOffset, size,
                        link, info, alignment, entrySize));
            } else {
                int type = (int) buffer.u32(at + 4);
                long flags = buffer.u32(at + 8);
                long address = buffer.u32(at + 12);
                long sectionOffset = buffer.u32(at + 16);
                long size = buffer.u32(at + 20);
                int link = (int) buffer.u32(at + 24);
                int info = (int) buffer.u32(at + 28);
                long alignment = buffer.u32(at + 32);
                long entrySize = buffer.u32(at + 36);
                sections.add(new ElfSection(index, name, "", type, flags, address, sectionOffset, size,
                        link, info, alignment, entrySize));
            }
        }
        return sections;
    }

    private Map<Integer, List<Integer>> parseExtendedIndices(SafeBuffer buffer, List<ElfSection> sections) {
        Map<Integer, List<Integer>> result = new HashMap<>();
        for (ElfSection section : sections) {
            if (section.type() != SHT_SYMTAB_SHNDX) {
                continue;
            }
            if (section.link() < 0 || section.link() >= sections.size()) {
                continue;
            }
            int count = Math.toIntExact(section.size() / 4);
            List<Integer> values = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                values.add((int) buffer.u32(section.offset() + (long) i * 4));
            }
            result.put(section.link(), values);
        }
        return result;
    }

    private GroupIndex buildGroupIndex(SafeBuffer buffer, List<ElfSection> sections) {
        Map<Integer, String> sectionGroups = new HashMap<>();
        Set<Integer> comdatSections = new HashSet<>();
        for (ElfSection group : sections) {
            if (group.type() != ElfConstants.SHT_GROUP || group.link() < 0 || group.link() >= sections.size()) {
                continue;
            }
            ElfSection symbolTable = sections.get(group.link());
            String signature = symbolAt(buffer, sections, symbolTable, group.info());
            if (signature == null) {
                continue;
            }
            boolean comdat = (buffer.u32(group.offset()) & ElfConstants.GRP_COMDAT) != 0;
            long entryCount = group.size() / 4;
            for (long entry = 1; entry < entryCount; entry++) {
                int memberSection = (int) buffer.u32(group.offset() + entry * 4);
                if (memberSection >= 0 && memberSection < sections.size()) {
                    sectionGroups.put(memberSection, signature);
                    if (comdat) {
                        comdatSections.add(memberSection);
                    }
                }
            }
        }
        return new GroupIndex(sectionGroups, comdatSections);
    }

    private String symbolAt(SafeBuffer buffer, List<ElfSection> sections, ElfSection symbolTable, int index) {
        if (symbolTable == null || symbolTable.link() < 0 || symbolTable.link() >= sections.size() || index < 0) {
            return null;
        }
        boolean table64 = symbolTable.entrySize() >= 24;
        long entrySize = symbolTable.entrySize() == 0 ? (table64 ? 24 : 16) : symbolTable.entrySize();
        long offset = symbolTable.offset() + (long) index * entrySize;
        if (offset + entrySize > symbolTable.offset() + symbolTable.size()) {
            return null;
        }
        int nameIndex = (int) buffer.u32(offset);
        return buffer.stringTable(sections.get(symbolTable.link()).offset(), nameIndex);
    }

    private List<ElfSymbol> parseSymbols(SafeBuffer buffer, List<ElfSection> sections, GroupIndex groups,
                                         Map<Integer, List<Integer>> extendedIndices) {
        List<ElfSymbol> symbols = new ArrayList<>();
        for (ElfSection section : sections) {
            if (section.type() != ElfConstants.SHT_SYMTAB && section.type() != ElfConstants.SHT_DYNSYM) {
                continue;
            }
            if (section.link() < 0 || section.link() >= sections.size()) {
                throw new ElfFormatException("symbol string table 索引越界");
            }
            long stringOffset = sections.get(section.link()).offset();
            boolean table64 = section.entrySize() >= 24;
            long entrySize = section.entrySize() == 0 ? (table64 ? 24 : 16) : section.entrySize();
            long count = section.size() / entrySize;
            List<Integer> xindex = extendedIndices.get(section.index());
            for (long index = 0; index < count; index++) {
                long at = section.offset() + index * entrySize;
                int nameIndex;
                int info;
                int other;
                int sectionIndex;
                long value;
                long size;
                if (table64) {
                    nameIndex = (int) buffer.u32(at);
                    info = buffer.u8(at + 4) & 0xff;
                    other = buffer.u8(at + 5) & 0xff;
                    sectionIndex = buffer.u16(at + 6);
                    value = buffer.u64(at + 8);
                    size = buffer.u64(at + 16);
                } else {
                    nameIndex = (int) buffer.u32(at);
                    value = buffer.u32(at + 4);
                    size = buffer.u32(at + 8);
                    info = buffer.u8(at + 12) & 0xff;
                    other = buffer.u8(at + 13) & 0xff;
                    sectionIndex = buffer.u16(at + 14);
                }
                if (sectionIndex == ElfConstants.SHN_XINDEX && xindex != null
                        && (int) index < xindex.size()) {
                    sectionIndex = xindex.get((int) index);
                }
                String sectionName = sectionIndex >= 0 && sectionIndex < sections.size()
                        ? sections.get(sectionIndex).name() : "";
                String groupSignature = sectionIndex >= 0 ? groups.signatures.get(sectionIndex) : null;
                boolean comdatGroup = sectionIndex >= 0 && groups.comdatSections.contains(sectionIndex);
                symbols.add(new ElfSymbol((int) index, nameIndex, buffer.stringTable(stringOffset, nameIndex),
                        value, size, info >>> 4, info & 0xf, other, other & 3, sectionIndex, sectionName,
                        groupSignature, comdatGroup, section.type() == ElfConstants.SHT_DYNSYM));
            }
        }
        return symbols;
    }

    private List<ElfRelocation> parseRelocations(SafeBuffer buffer, boolean elf64, List<ElfSection> sections) {
        List<ElfRelocation> relocations = new ArrayList<>();
        for (ElfSection section : sections) {
            boolean rela = section.type() == ElfConstants.SHT_RELA;
            if (!rela && section.type() != ElfConstants.SHT_REL) {
                continue;
            }
            long entrySize = section.entrySize() == 0 ? (rela ? (elf64 ? 24 : 12) : (elf64 ? 16 : 8))
                    : section.entrySize();
            if (entrySize <= 0 || section.size() / entrySize() > 1_000_000) {
                continue;
            }
            String targetName = section.info() >= 0 && section.info() < sections.size()
                    ? sections.get(section.info()).name() : "";
            for (long index = 0; index < section.size() / entrySize; index++) {
                long at = section.offset() + index * entrySize;
                long offset = elf64 ? buffer.u64(at) : buffer.u32(at);
                long infoValue = elf64 ? buffer.u64(at + 8) : buffer.u32(at + 4);
                long symbolIndex = elf64 ? infoValue >>> 32 : infoValue >>> 8;
                int type = (int) (elf64 ? infoValue & 0xffffffffL : infoValue & 0xff);
                Long addend = rela ? (elf64 ? buffer.u64(at + 16) : Integer.toUnsignedLong((int) buffer.u32(at + 8))) : null;
                relocations.add(new ElfRelocation(offset, symbolIndex, type, addend, rela, section.info(), targetName));
            }
        }
        return relocations;
    }

    private List<VersionSlot> parseVersionSlots(List<ElfSection> sections) {
        for (ElfSection section : sections) {
            if (section.type() != ElfConstants.SHT_GNU_VERSYM) {
                continue;
            }
            SafeBuffer localBuffer = null;
        }
        return List.of();
    }

    private record GroupIndex(Map<Integer, String> signatures, Set<Integer> comdatSections) {
    }

    private record DynamicInfo(List<NeededEntry> needed, String soname) {
    }
}
