package org.tribunal.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tribunal.model.ArchiveMember;
import org.tribunal.model.BinaryKind;
import org.tribunal.model.DependencyEdge;
import org.tribunal.model.ElfClass;
import org.tribunal.model.ElfFile;
import org.tribunal.model.RelocationInfo;
import org.tribunal.model.SymbolDefinition;
import org.tribunal.model.SymbolReference;
import org.tribunal.model.VersionDefinition;
import org.tribunal.model.VersionEdge;
import org.tribunal.model.VersionRequirement;

public class ElfParser {
    private static final byte[] ELF_MAGIC = {0x7f, 'E', 'L', 'F'};

    private final String unitId;
    private final String unitName;
    private final String archiveUnit;
    private byte[] data;
    private ElfReader reader;
    private ElfClass elfClass;
    private boolean littleEndian;
    private int elfType;
    private int machine;
    private List<Section> sections = List.of();
    private final Map<Integer, String> sectionNames = new HashMap<>();
    private final Map<Integer, List<String>> groupSignaturesBySection = new HashMap<>();
    private final Map<Integer, Integer> extendedSectionIndices = new HashMap<>();

    public ElfParser(String unitId, String unitName) {
        this(unitId, unitName, null);
    }

    public ElfParser(String unitId, String unitName, String archiveUnit) {
        this.unitId = unitId;
        this.unitName = unitName;
        this.archiveUnit = archiveUnit;
    }

    public ElfFile parse(byte[] bytes) throws ElfFormatException {
        if (bytes == null || bytes.length < 64) {
            throw new ElfFormatException("Input is smaller than an ELF identification/header");
        }
        this.data = bytes;
        for (int i = 0; i < ELF_MAGIC.length; i++) {
            if (data[i] != ELF_MAGIC[i]) {
                throw new ElfFormatException("Invalid ELF magic");
            }
        }
        int eiClass = data[4] & 0xff;
        if (eiClass == 1) {
            elfClass = ElfClass.ELF_32;
        } else if (eiClass == 2) {
            elfClass = ElfClass.ELF_64;
        } else {
            throw new ElfFormatException("Unsupported ELF class " + eiClass);
        }
        int eiData = data[5] & 0xff;
        if (eiData == 1) {
            littleEndian = true;
        } else if (eiData == 2) {
            littleEndian = false;
        } else {
            throw new ElfFormatException("Unsupported ELF data encoding");
        }
        reader = new ElfReader(data, littleEndian);
        parseHeader();
        parseSections();
        parseSectionHeaderExtension();
        parseSectionNames();
        parseSectionGroups();
        List<VersionDefinition> definitions = parseVersionDefinitions();
        List<VersionRequirement> requirements = parseVersionRequirements();
        ParsedSymbols symbols = parseSymbolTables(definitions, requirements);
        List<RelocationInfo> relocations = parseRelocations();
        List<DependencyEdge> dependencies = parseDependencies();
        List<VersionEdge> edges = buildVersionEdges(definitions, requirements, symbols.definitions());
        List<String> comdatGroups = groupSignaturesBySection.values().stream()
                .flatMap(List::stream).distinct().sorted().toList();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("archiveUnit", archiveUnit);
        metadata.put("sectionCount", Integer.toString(sections.size()));
        metadata.put("byteOrder", littleEndian ? "little-endian" : "big-endian");
        metadata.put("elfClass", elfClass == ElfClass.ELF_64 ? "ELF64" : "ELF32");
        metadata.put("machine", machineName(machine));
        return new ElfFile(unitId, unitName, binaryKind(), elfType, machine, elfClass, littleEndian,
                symbols.definitions(), symbols.references(), relocations, definitions, requirements,
                edges, dependencies, comdatGroups, metadata);
    }

    private void parseHeader() throws ElfFormatException {
        if (elfClass == ElfClass.ELF_64 && data.length < 64) {
            throw new ElfFormatException("Truncated ELF64 header");
        }
        if (elfClass == ElfClass.ELF_32 && data.length < 52) {
            throw new ElfFormatException("Truncated ELF32 header");
        }
        elfType = reader.u16(16);
        machine = reader.u16(18);
        if (elfType != ElfConstants.ET_REL && elfType != ElfConstants.ET_EXEC && elfType != ElfConstants.ET_DYN) {
            throw new ElfFormatException("Unsupported ELF type " + elfType);
        }
    }

    private void parseSections() throws ElfFormatException {
        long shoff = elfClass == ElfClass.ELF_64 ? reader.u64(40) : reader.u32(32);
        int shentsize = reader.u16(elfClass == ElfClass.ELF_64 ? 58 : 46);
        int shnum = reader.u16(elfClass == ElfClass.ELF_64 ? 60 : 48);
        int shstrndx = reader.u16(elfClass == ElfClass.ELF_64 ? 62 : 50);
        int minimumEntry = elfClass == ElfClass.ELF_64 ? 64 : 40;
        if (shnum == 0) {
            sections = List.of();
            return;
        }
        if (shentsize < minimumEntry) {
            throw new ElfFormatException("Section header entry is too small");
        }
        if (shoff < 0 || shoff > Integer.MAX_VALUE) {
            throw new ElfFormatException("Invalid section header table offset");
        }
        long required = Math.addExact(shoff, Math.multiplyExact((long) shnum, shentsize));
        if (required > data.length) {
            throw new ElfFormatException("Section header table exceeds file length");
        }
        List<Section> result = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            int base = Math.toIntExact(shoff + (long) i * shentsize);
            if (elfClass == ElfClass.ELF_64) {
                result.add(new Section(i, (int) reader.u32(base), (int) reader.u32(base + 4), reader.u64(base + 8),
                        reader.u64(base + 16), reader.u64(base + 24), reader.u64(base + 32),
                        (int) reader.u32(base + 40), (int) reader.u32(base + 44), reader.u64(base + 48),
                        reader.u64(base + 56)));
            } else {
                result.add(new Section(i, (int) reader.u32(base), (int) reader.u32(base + 4), reader.u32(base + 8),
                        reader.u32(base + 12), reader.u32(base + 16), reader.u32(base + 20),
                        (int) reader.u32(base + 24), (int) reader.u32(base + 28), (int) reader.u32(base + 32),
                        reader.u32(base + 36)));
            }
        }
        if (shstrndx != 0) {
            Section nameSection = getSectionExtended(result, shstrndx);
            if (nameSection.type() != ElfConstants.SHT_STRTAB) {
                throw new ElfFormatException("Section name string table is not SHT_STRTAB");
            }
        }
        sections = result;
    }

    private Section getSectionExtended(List<Section> list, int index) throws ElfFormatException {
        if (index < 0 || index >= list.size()) {
            throw new ElfFormatException("Section index " + index + " is out of bounds");
        }
        return list.get(index);
    }

    private void parseSectionHeaderExtension() throws ElfFormatException {
        for (Section section : sections) {
            if (section.type() == ElfConstants.SHT_SYMTAB_SHNDX) {
                int entrySize = section.entrySize() == 0 ? 4 : Math.toIntExact(section.entrySize());
                if (entrySize < 4) {
                    throw new ElfFormatException("Invalid extended section index entry size");
                }
                long count = section.size() / entrySize;
                for (long i = 0; i < count; i++) {
                    int offset = Math.toIntExact(section.offset() + i * entrySize);
                    extendedSectionIndices.put(Math.toIntExact(i), Math.toIntExact(reader.u32(offset)));
                }
            }
        }
    }

    private void parseSectionNames() throws ElfFormatException {
        int shstrndx = reader.u16(elfClass == ElfClass.ELF_64 ? 62 : 50);
        if (shstrndx == 0) {
            return;
        }
        Section strings = sections.get(shstrndx);
        for (Section section : sections) {
            if (section.index() == 0) {
                sectionNames.put(0, "");
            } else {
                sectionNames.put(section.index(), reader.cString(Math.toIntExact(section.nameOffset()), Math.toIntExact(strings.size())));
            }
        }
    }

    private BinaryKind binaryKind() {
        return switch (elfType) {
            case ElfConstants.ET_REL -> BinaryKind.OBJECT;
            case ElfConstants.ET_DYN -> BinaryKind.SHARED;
            case ElfConstants.ET_EXEC -> BinaryKind.EXECUTABLE;
            default -> BinaryKind.UNKNOWN;
        };
    }

    private void parseSectionGroups() throws ElfFormatException {
        for (Section group : sections) {
            if (group.type() != ElfConstants.SHT_GROUP) {
                continue;
            }
            Section linkedSymbolSection = sections.get(group.link());
            if (linkedSymbolSection.type() != ElfConstants.SHT_SYMTAB && linkedSymbolSection.type() != ElfConstants.SHT_DYNSYM) {
                throw new ElfFormatException("SHT_GROUP link must reference a symbol table");
            }
            if (group.size() < 8) {
                throw new ElfFormatException("Truncated SHT_GROUP section");
            }
            int count = Math.toIntExact(elfClass == ElfClass.ELF_64 ? reader.u32((int) group.offset() + 4) : reader.u32((int) group.offset() + 4));
            long required = 4L + (elfClass == ElfClass.ELF_64 ? 4L : 4L) + (long) count * (elfClass == ElfClass.ELF_64 ? 4 : 4);
            if (required > group.size()) {
                throw new ElfFormatException("SHT_GROUP section indices exceed section size");
            }
            String signature = resolveSymbolName(linkedSymbolSection, group.info());
            for (int i = 0; i < count; i++) {
                int memberSectionIndex = (int) reader.u32(Math.toIntExact(group.offset() + 8 + 4L * i));
                if (memberSectionIndex > 0 && memberSectionIndex < sections.size()) {
                    groupSignaturesBySection.computeIfAbsent(memberSectionIndex, ignored -> new ArrayList<>()).add(signature);
                }
            }
        }
    }

    private String resolveSymbolName(Section symtab, int symbolIndex) throws ElfFormatException {
        if (symbolIndex <= 0 || symbolIndex >= countSymbols(symtab)) {
            return "";
        }
        SymbolEntry entry = readSymbol(symtab, symbolIndex);
        Section strings = sections.get(symtab.link());
        return reader.cString((int) (strings.offset() + entry.nameOffset()), (int) strings.size());
    }

    private int countSymbols(Section symtab) throws ElfFormatException {
        int entrySize = Math.toIntExact(symtab.entrySize());
        int minimum = elfClass == ElfClass.ELF_64 ? 24 : 16;
        if (entrySize < minimum) {
            throw new ElfFormatException("Invalid symbol table entry size");
        }
        return Math.toIntExact(symtab.size() / entrySize);
    }

    private SymbolEntry readSymbol(Section symtab, int index) throws ElfFormatException {
        int entrySize = Math.toIntExact(symtab.entrySize());
        int base = Math.toIntExact(symtab.offset() + (long) index * entrySize);
        if (elfClass == ElfClass.ELF_64) {
            long info = reader.u8(base + 4);
            long other = reader.u8(base + 5);
            return new SymbolEntry(reader.u32(base), (int) info, (int) other, reader.u16(base + 6),
                    reader.u16(base + 8), reader.u64(base + 16), reader.u64(base));
        }
        long info = reader.u8(base + 12);
        long other = reader.u8(base + 13);
        return new SymbolEntry(reader.u32(base), (int) info, (int) other, reader.u16(base + 14),
                reader.u16(base + 6), reader.u32(base + 4), reader.u32(base));
    }

    private record SymbolEntry(long nameOffset, int info, int other, int shndx, int binding,
                               long value, long size) {
        SymbolEntry(long nameOffset, int info, int other, int shndx, long value, long size) {
            this(nameOffset, info, other, shndx, info >> 4, value, size);
        }
    }

    private List<VersionDefinition> parseVersionDefinitions() throws ElfFormatException {
        Section dynamic = firstSection(ElfConstants.SHT_DYNAMIC);
        if (dynamic == null || dynamic.link() >= sections.size()) {
            return List.of();
        }
        Map<Long, Long> tags = parseDynamicTags(dynamic);
        Long address = tags.get((long) ElfConstants.DT_VERDEF);
        Long count = tags.get((long) ElfConstants.DT_VERDEFNUM);
        if (address == null || count == null || count <= 0) {
            return List.of();
        }
        long fileOffset = dynamicVirtualToFileOffset(dynamic, address);
        Section strings = sections.get(dynamic.link());
        List<VersionDefinition> result = new ArrayList<>();
        long cursor = fileOffset;
        for (long i = 0; i < count; i++) {
            requireDynamicRecord(cursor, 20);
            int version = reader.u16((int) cursor);
            int flags = reader.u16((int) cursor + 2);
            int ndx = reader.u16((int) cursor + 4);
            int cnt = reader.u16((int) cursor + 6);
            long aux = reader.u32((int) cursor + 12);
            long next = reader.u32((int) cursor + 16);
            List<String> names = new ArrayList<>();
            long auxCursor = cursor + aux;
            for (int a = 0; a < cnt; a++) {
                requireDynamicRecord(auxCursor, 8);
                long nameOffset = reader.u32((int) auxCursor);
                long auxNext = reader.u32((int) auxCursor + 4);
                names.add(reader.cString((int) (strings.offset() + nameOffset), (int) strings.size()));
                if (auxNext == 0) {
                    break;
                }
                auxCursor += auxNext;
            }
            result.add(new VersionDefinition(ndx & 0x7fff, List.copyOf(names),
                    (flags & ElfConstants.VER_FLG_BASE) != 0, (ndx & ElfConstants.VERSYM_HIDDEN) != 0));
            if (next == 0) {
                break;
            }
            cursor += next;
        }
        return result;
    }

    private List<VersionRequirement> parseVersionRequirements() throws ElfFormatException {
        Section dynamic = firstSection(ElfConstants.SHT_DYNAMIC);
        if (dynamic == null || dynamic.link() >= sections.size()) {
            return List.of();
        }
        Map<Long, Long> tags = parseDynamicTags(dynamic);
        Long address = tags.get((long) ElfConstants.DT_VERNEED);
        Long count = tags.get((long) ElfConstants.DT_VERNEEDNUM);
        if (address == null || count == null || count <= 0) {
            return List.of();
        }
        long cursor = dynamicVirtualToFileOffset(dynamic, address);
        Section strings = sections.get(dynamic.link());
        List<VersionRequirement> result = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            requireDynamicRecord(cursor, 16);
            int auxCount = reader.u16((int) cursor + 4);
            long fileNameOffset = reader.u32((int) cursor + 8);
            long auxOffset = reader.u32((int) cursor + 12);
            long next = reader.u32((int) cursor + 16);
            String fileName = reader.cString((int) (strings.offset() + fileNameOffset), (int) strings.size());
            long auxCursor = cursor + auxOffset;
            for (int a = 0; a < auxCount; a++) {
                requireDynamicRecord(auxCursor, 16);
                int flags = reader.u16((int) auxCursor + 2);
                int other = reader.u16((int) auxCursor + 4);
                long nameOffset = reader.u32((int) auxCursor + 8);
                long auxNext = reader.u32((int) auxCursor + 12);
                String versionName = reader.cString((int) (strings.offset() + nameOffset), (int) strings.size());
                result.add(new VersionRequirement(other & 0x7fff, fileName, versionName,
                        (other & ElfConstants.VERSYM_HIDDEN) != 0 || flags == ElfConstants.VER_FLG_WEAK));
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
        return result;
    }

    private void requireDynamicRecord(long offset, int length) throws ElfFormatException {
        if (offset < 0 || offset > data.length - length) {
            throw new ElfFormatException("Dynamic version record is outside file bounds");
        }
    }

    private long dynamicVirtualToFileOffset(Section dynamic, long virtualAddress) throws ElfFormatException {
        long relative = virtualAddress - dynamic.address();
        long offset = dynamic.offset() + relative;
        if (relative < 0 || offset >= data.length) {
            throw new ElfFormatException("Dynamic pointer does not map to an allocated section");
        }
        return offset;
    }

    private Map<Long, Long> parseDynamicTags(Section dynamic) throws ElfFormatException {
        int entrySize = elfClass == ElfClass.ELF_64 ? 16 : 8;
        if (dynamic.size() % entrySize != 0) {
            throw new ElfFormatException("Invalid dynamic section size");
        }
        long count = dynamic.size() / entrySize;
        Map<Long, Long> tags = new LinkedHashMap<>();
        for (long i = 0; i < count; i++) {
            int base = (int) (dynamic.offset() + i * entrySize);
            long tag = elfClass == ElfClass.ELF_64 ? reader.u64(base) : reader.u32(base);
            long value = elfClass == ElfClass.ELF_64 ? reader.u64(base + 8) : reader.u32(base + 4);
            tags.put(tag, value);
            if (tag == 0) {
                break;
            }
        }
        return tags;
    }

    private Section firstSection(int type) {
        return sections.stream().filter(section -> section.type() == type).findFirst().orElse(null);
    }

    private ParsedSymbols parseSymbolTables(List<VersionDefinition> definitions,
                                            List<VersionRequirement> requirements) throws ElfFormatException {
        List<SymbolDefinition> symbolDefinitions = new ArrayList<>();
        List<SymbolReference> references = new ArrayList<>();
        int ordinal = 0;
        for (Section symtab : sections) {
            if (symtab.type() != ElfConstants.SHT_SYMTAB && symtab.type() != ElfConstants.SHT_DYNSYM) {
                continue;
            }
            if (symtab.link() <= 0 || symtab.link() >= sections.size()) {
                throw new ElfFormatException("Symbol table string link is out of bounds");
            }
            Section strings = sections.get(symtab.link());
            if (strings.type() != ElfConstants.SHT_STRTAB) {
                throw new ElfFormatException("Symbol table link is not a string table");
            }
            int[] versionIndices = parseVersionIndices(symtab);
            int symbolCount = countSymbols(symtab);
            for (int index = 1; index < symbolCount; index++) {
                SymbolEntry entry = readSymbol(symtab, index);
                int type = entry.info() & 0xf;
                if (type == ElfConstants.STT_SECTION) {
                    continue;
                }
                String name = reader.cString((int) (strings.offset() + entry.nameOffset()), (int) strings.size());
                if (name.isEmpty()) {
                    continue;
                }
                int effectiveShndx = resolveSectionIndex(index, entry.shndx());
                String binding = bindingName(entry.binding());
                String visibility = visibilityName(entry.other() & 3);
                boolean undefined = effectiveShndx == ElfConstants.SHN_UNDEF;
                boolean common = effectiveShndx == ElfConstants.SHN_COMMON || type == ElfConstants.STT_COMMON;
                int rawVersion = versionIndices == null || index >= versionIndices.length ? 1 : versionIndices[index];
                VersionName resolvedVersion = resolveVersion(rawVersion, definitions, requirements);
                String sectionName = sectionName(effectiveShndx);
                String specialSection = specialSection(effectiveShndx);
                String groupSignature = groupSignature(effectiveShndx);
                if (!undefined && !common && !"LOCAL".equals(binding) && !"HIDDEN".equals(visibility)
                        && !"INTERNAL".equals(visibility)) {
                    String providerKey = providerKey(name, resolvedVersion.versionKey());
                    symbolDefinitions.add(new SymbolDefinition(unitId, unitName, binaryKind(), ordinal++,
                            archiveUnit, name, binding, visibility, effectiveShndx, sectionName,
                            specialSection, entry.value(), entry.size(), true, false,
                            resolvedVersion.versionName(), resolvedVersion.versionKey(), rawVersion,
                            resolvedVersion.defaultVersion(), resolvedVersion.hiddenVersion(),
                            groupSignature, providerKey));
                } else if (common) {
                    String commonVersion = resolvedVersion.versionName();
                    symbolDefinitions.add(new SymbolDefinition(unitId, unitName, binaryKind(), ordinal++,
                            archiveUnit, name, binding, visibility, effectiveShndx, sectionName,
                            specialSection, entry.value(), entry.size(), false, true,
                            commonVersion, "COMMON:" + name, rawVersion, true, false, groupSignature,
                            "COMMON:" + name));
                }
                if (undefined) {
                    boolean strong = "GLOBAL".equals(binding);
                    references.add(new SymbolReference(unitId, unitName, name, binding,
                            resolvedVersion.versionName(), requestKey(name, resolvedVersion.versionName()),
                            resolvedVersion.versionName() != null, rawVersion, null, strong, false));
                }
            }
        }
        return new ParsedSymbols(symbolDefinitions, references);
    }

    private int resolveSectionIndex(int symbolIndex, int shndx) throws ElfFormatException {
        if (shndx != ElfConstants.SHN_XINDEX) {
            if (shndx != 0 && shndx < 0xff00 && shndx >= sections.size()) {
                throw new ElfFormatException("Symbol section index " + shndx + " is out of bounds");
            }
            return shndx;
        }
        Integer extended = extendedSectionIndices.get(symbolIndex);
        if (extended == null || (extended != 0 && extended < 0xff00 && extended >= sections.size())) {
            throw new ElfFormatException("Extended section index is missing or out of bounds");
        }
        return extended;
    }

    private int[] parseVersionIndices(Section symtab) throws ElfFormatException {
        for (Section candidate : sections) {
            if (candidate.type() == 0x6fffffff && candidate.link() == symtab.index()) {
                long count = candidate.size() / 2;
                int[] result = new int[Math.toIntExact(count)];
                for (int i = 0; i < count; i++) {
                    result[i] = reader.u16((int) (candidate.offset() + 2L * i));
                }
                return result;
            }
        }
        if (symtab.type() == ElfConstants.SHT_DYNSYM) {
            int count = countSymbols(symtab);
            int[] result = new int[count];
            java.util.Arrays.fill(result, ElfConstants.VER_NDX_GLOBAL);
            return result;
        }
        return null;
    }

    private VersionName resolveVersion(int raw, List<VersionDefinition> definitions,
                                       List<VersionRequirement> requirements) {
        boolean hidden = (raw & ElfConstants.VERSYM_HIDDEN) != 0;
        int index = raw & 0x7fff;
        if (index == ElfConstants.VER_NDX_LOCAL || index == ElfConstants.VER_NDX_GLOBAL
                || index == ElfConstants.VER_NDX_GNU_ELIMINATED) {
            return new VersionName(null, null, true, false);
        }
        for (VersionDefinition definition : definitions) {
            if (definition.index() == index) {
                String name = definition.names().isEmpty() ? null : definition.names().get(0);
                return new VersionName(name, name, !hidden && !definition.hidden(), hidden || definition.hidden());
            }
        }
        for (VersionRequirement requirement : requirements) {
            if (requirement.index() == index) {
                return new VersionName(requirement.versionName(), requirement.versionName(),
                        !hidden && !requirement.hidden(), hidden || requirement.hidden());
            }
        }
        return new VersionName("VER<" + index + ">", "VER<" + index + ">", false, hidden);
    }

    private record VersionName(String versionName, String versionKey, boolean defaultVersion,
                               boolean hiddenVersion) {
    }

    private String bindingName(int binding) {
        return switch (binding) {
            case ElfConstants.STB_LOCAL -> "LOCAL";
            case ElfConstants.STB_GLOBAL -> "GLOBAL";
            case ElfConstants.STB_WEAK -> "WEAK";
            default -> "UNKNOWN(" + binding + ")";
        };
    }

    private String visibilityName(int visibility) {
        return switch (visibility) {
            case ElfConstants.STV_DEFAULT -> "DEFAULT";
            case ElfConstants.STV_INTERNAL -> "INTERNAL";
            case ElfConstants.STV_HIDDEN -> "HIDDEN";
            case ElfConstants.STV_PROTECTED -> "PROTECTED";
            default -> "UNKNOWN";
        };
    }

    private String sectionName(int shndx) {
        if (shndx > 0 && shndx < sections.size()) {
            return sectionNames.getOrDefault(shndx, ".section[" + shndx + "]");
        }
        return null;
    }

    private String specialSection(int shndx) {
        return switch (shndx) {
            case ElfConstants.SHN_UNDEF -> "UND";
            case ElfConstants.SHN_ABS -> "ABS";
            case ElfConstants.SHN_COMMON -> "COMMON";
            default -> null;
        };
    }

    private String groupSignature(int shndx) {
        List<String> groups = groupSignaturesBySection.get(shndx);
        return groups == null || groups.isEmpty() ? null : groups.get(0);
    }

    private String providerKey(String name, String version) {
        return unitId + "#" + name + (version == null ? "@@" + "BASE" : "@@" + version);
    }

    private String requestKey(String name, String version) {
        return name + (version == null ? "@" + "BASE" : "@" + version);
    }

    private record ParsedSymbols(List<SymbolDefinition> definitions, List<SymbolReference> references) {
    }

    private List<RelocationInfo> parseRelocations() throws ElfFormatException {
        List<RelocationInfo> result = new ArrayList<>();
        for (Section relocationSection : sections) {
            if (relocationSection.type() != ElfConstants.SHT_REL && relocationSection.type() != ElfConstants.SHT_RELA) {
                continue;
            }
            if (relocationSection.link() <= 0 || relocationSection.link() >= sections.size()) {
                throw new ElfFormatException("Relocation symbol table link is out of bounds");
            }
            Section symtab = sections.get(relocationSection.link());
            if (symtab.type() != ElfConstants.SHT_SYMTAB && symtab.type() != ElfConstants.SHT_DYNSYM) {
                throw new ElfFormatException("Relocation link is not a symbol table");
            }
            int targetIndex = relocationSection.info();
            String targetName = sectionName(targetIndex);
            boolean rela = relocationSection.type() == ElfConstants.SHT_RELA;
            int entrySize = elfClass == ElfClass.ELF_64 ? (rela ? 24 : 16) : (rela ? 12 : 8);
            long count = relocationSection.size() / entrySize;
            if (relocationSection.size() % entrySize != 0) {
                throw new ElfFormatException("Relocation section size is not a whole number of entries");
            }
            Section strings = sections.get(symtab.link());
            int[] versions = parseVersionIndices(symtab);
            for (long i = 0; i < count; i++) {
                int base = (int) (relocationSection.offset() + i * entrySize);
                long offset;
                long info;
                long addend = 0;
                if (elfClass == ElfClass.ELF_64) {
                    offset = reader.u64(base);
                    info = reader.u64(base + 8);
                    if (rela) {
                        addend = reader.u64(base + 16);
                    }
                } else {
                    offset = reader.u32(base);
                    info = reader.u32(base + 4);
                    if (rela) {
                        addend = reader.u32(base + 8);
                    }
                }
                int symbolIndex = elfClass == ElfClass.ELF_64 ? (int) (info >> 32) : (int) (info >> 8);
                int type = elfClass == ElfClass.ELF_64 ? (int) info : (int) (info & 0xff);
                if (symbolIndex < 0 || symbolIndex >= countSymbols(symtab)) {
                    throw new ElfFormatException("Relocation symbol index is out of bounds");
                }
                SymbolEntry symbol = readSymbol(symtab, symbolIndex);
                String symbolName = reader.cString((int) (strings.offset() + symbol.nameOffset()), (int) strings.size());
                int rawVersion = versions == null || symbolIndex >= versions.length ? 1 : versions[symbolIndex];
                VersionName version = resolveVersion(rawVersion, parseVersionDefinitions(), parseVersionRequirements());
                boolean copy = isCopyRelocation(type);
                result.add(new RelocationInfo(unitId, offset, type, relocationTypeName(type), symbolIndex,
                        symbolName, version.versionName(), addend, rela, targetName, copy));
            }
        }
        return result;
    }

    private boolean isCopyRelocation(int type) {
        return machine == ElfConstants.EM_X86_64 && type == 5
                || machine == ElfConstants.EM_386 && type == 20
                || machine == ElfConstants.EM_AARCH64 && type == 1024
                || machine == ElfConstants.EM_ARM && type == 20
                || machine == ElfConstants.EM_RISCV && type == 6;
    }

    private String relocationTypeName(int type) {
        if (isCopyRelocation(type)) {
            return "R_COPY";
        }
        if (machine == ElfConstants.EM_X86_64) {
            return switch (type) {
                case 1 -> "R_X86_64_64";
                case 2 -> "R_X86_64_PC32";
                case 4 -> "R_X86_64_PLT32";
                case 6 -> "R_X86_64_GLOB_DAT";
                case 7 -> "R_X86_64_JUMP_SLOT";
                case 8 -> "R_X86_64_RELATIVE";
                default -> "R_X86_64_" + type;
            };
        }
        return "R_" + machine + "_" + type;
    }

    private List<DependencyEdge> parseDependencies() throws ElfFormatException {
        Section dynamic = firstSection(ElfConstants.SHT_DYNAMIC);
        if (dynamic == null) {
            return List.of();
        }
        Map<Long, Long> tags = parseDynamicTags(dynamic);
        Section strings = sections.get(dynamic.link());
        List<DependencyEdge> result = new ArrayList<>();
        for (Map.Entry<Long, Long> tag : tags.entrySet()) {
            if (tag.getKey() == ElfConstants.DT_NEEDED) {
                String needed = reader.cString((int) (strings.offset() + tag.getValue()), (int) strings.size());
                result.add(new DependencyEdge(unitId, needed, "NEEDED"));
            } else if (tag.getKey() == ElfConstants.DT_SONAME) {
                String soname = reader.cString((int) (strings.offset() + tag.getValue()), (int) strings.size());
                result.add(new DependencyEdge(unitId, soname, "SONAME"));
            }
        }
        return result;
    }

    private List<VersionEdge> buildVersionEdges(List<VersionDefinition> definitions,
                                                List<VersionRequirement> requirements,
                                                List<SymbolDefinition> localDefinitions) {
        List<VersionEdge> result = new ArrayList<>();
        for (VersionDefinition definition : definitions) {
            String version = definition.names().isEmpty() ? "VER<" + definition.index() + ">" : definition.names().get(0);
            for (int i = 1; i < definition.names().size(); i++) {
                result.add(new VersionEdge(unitId, version, definition.names().get(i), "ALIAS", null));
            }
        }
        for (VersionRequirement requirement : requirements) {
            result.add(new VersionEdge(unitId, requirement.fileName(), requirement.versionName(), "NEEDS_VERSION", null));
        }
        for (SymbolDefinition definition : localDefinitions) {
            if (definition.versionName() != null) {
                result.add(new VersionEdge(unitId, definition.name(), definition.versionName(), "DEFINES_VERSION",
                        definition.defaultVersion() ? "default" : "non-default"));
            }
        }
        return result;
    }

    private String machineName(int value) {
        return switch (value) {
            case ElfConstants.EM_386 -> "i386";
            case ElfConstants.EM_ARM -> "ARM";
            case ElfConstants.EM_X86_64 -> "x86-64";
            case ElfConstants.EM_AARCH64 -> "AArch64";
            case ElfConstants.EM_RISCV -> "RISC-V";
            default -> "EM_" + value;
        };
    }
}
