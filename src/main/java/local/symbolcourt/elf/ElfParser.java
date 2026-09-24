package local.symbolcourt.elf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ElfParser {
    private static final int SHT_PROGBITS = 1;
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_RELA = 4;
    private static final int SHT_REL = 9;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GROUP = 17;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final int DT_NEEDED = 1;
    private static final int DT_STRTAB = 5;
    private static final int DT_STRSZ = 10;
    private static final int DT_SONAME = 14;

    private final byte[] data;
    private final String fileName;
    private final ByteBuffer buffer;
    private final int elfClass;
    private final ByteOrder order;
    private final int objectType;
    private final int machine;
    private final int shoff;
    private final int shstrndx;
    private final int shnum;
    private final int shentsize;

    public ElfParser(byte[] data, String fileName) {
        this.data = data;
        this.fileName = fileName;
        require(data != null && data.length >= 16, "file is too short");
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfParseException("missing ELF magic");
        }
        this.elfClass = u8(4);
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfParseException("unsupported ELF class");
        }
        int dataEncoding = u8(5);
        if (dataEncoding != 1 && dataEncoding != 2) {
            throw new ElfParseException("unsupported ELF endianness");
        }
        this.order = dataEncoding == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        this.buffer = ByteBuffer.wrap(data).order(order);
        this.objectType = u16(16);
        this.machine = u16(18);
        if (elfClass == 2) {
            require(data.length >= 64, "truncated ELF64 header");
            this.shoff = (int) u64(40);
            this.shentsize = u16(58);
            this.shnum = u16(60);
            this.shstrndx = u16(62);
        } else {
            require(data.length >= 52, "truncated ELF32 header");
            this.shoff = u32(32);
            this.shentsize = u16(46);
            this.shnum = u16(48);
            this.shstrndx = u16(50);
        }
    }

    public static ElfModel.ElfFile parse(byte[] data, String fileName) {
        return new ElfParser(data, fileName).parse();
    }

    private ElfModel.ElfFile parse() {
        List<ElfModel.ElfSection> sections = parseSections();
        Map<Integer, List<RawSymbol>> rawByTable = new LinkedHashMap<>();
        List<ElfModel.ElfSymbol> allSymbols = new ArrayList<>();
        List<ElfModel.ElfRelocation> relocations = new ArrayList<>();
        for (ElfModel.ElfSection section : sections) {
            if (section.type() == SHT_SYMTAB || section.type() == SHT_DYNSYM) {
                List<RawSymbol> symbols = parseSymbols(section, sections);
                rawByTable.put(section.index(), symbols);
            }
        }
        Map<Integer, VersionName> versionNames = parseVersionNames(sections);
        for (Map.Entry<Integer, List<RawSymbol>> entry : rawByTable.entrySet()) {
            applyVersions(entry.getValue(), sections, versionNames, allSymbols);
        }
        for (ElfModel.ElfSection section : sections) {
            if (section.type() == SHT_REL || section.type() == SHT_RELA) {
                relocations.addAll(parseRelocations(section, sections, rawByTable, allSymbols));
            }
        }
        List<ElfModel.VersionDefinition> definitions = buildDefinitions(sections, versionNames);
        List<ElfModel.VersionNeed> needs = buildNeeds(sections, versionNames);
        List<ElfModel.SectionGroup> groups = parseGroups(sections, rawByTable);
        DynamicInfo dynamic = parseDynamic(sections);
        return new ElfModel.ElfFile(fileName, elfClass, objectType, machine, sections, allSymbols,
                relocations, definitions, needs, groups, dynamic.needed(), dynamic.soname());
    }

    private List<ElfModel.ElfSection> parseSections() {
        if (shoff == 0 || shnum == 0) {
            return new ArrayList<>();
        }
        int minimumEntry = elfClass == 2 ? 64 : 40;
        require(shentsize >= minimumEntry, "invalid section header size");
        long end = Math.addExact((long) shoff, (long) shnum * shentsize);
        require(end <= data.length, "section headers exceed file");
        ElfModel.ElfSection[] array = new ElfModel.ElfSection[shnum];
        for (int index = 0; index < shnum; index++) {
            int offset = shoff + index * shentsize;
            if (index == 0) {
                array[0] = new ElfModel.ElfSection(0, "", 0, 0, 0, 0, 0, 0, 0, 0, 0);
                continue;
            }
            if (elfClass == 2) {
                String name = readSectionName(u32(offset), shstrndx);
                array[index] = new ElfModel.ElfSection(index, name, u32(offset + 4), u64(offset + 8),
                        u64(offset + 16), u64(offset + 24), u64(offset + 32), (int) u32(offset + 40),
                        (int) u32(offset + 44), u64(offset + 48), u64(offset + 56));
            } else {
                String name = readSectionName(u32(offset), shstrndx);
                array[index] = new ElfModel.ElfSection(index, name, u32(offset + 4), u32(offset + 8),
                        u32(offset + 16), u32(offset + 20), u32(offset + 24), u16(offset + 28),
                        u32(offset + 32), u32(offset + 36), u32(offset + 40));
            }
        }
        return List.of(array);
    }

    private String readSectionName(int offset, int sectionIndex) {
        if (sectionIndex <= 0 || sectionIndex >= shnum) {
            return "";
        }
        int base = shoff + sectionIndex * shentsize;
        int tableOffset = elfClass == 2 ? (int) u64(base + 24) : u32(base + 20);
        long tableSize = elfClass == 2 ? u64(base + 32) : u32(base + 24);
        return readString(tableOffset + offset, tableOffset + tableSize);
    }

    private List<RawSymbol> parseSymbols(ElfModel.ElfSection section, List<ElfModel.ElfSection> sections) {
        require(section.link() > 0 && section.link() < sections.size(), "symbol table has invalid string link");
        ElfModel.ElfSection strings = sections.get(section.link());
        require(strings.type() == 3, "symbol table does not link a string table");
        int entrySize = (int) (section.entrySize() > 0 ? section.entrySize() : (elfClass == 2 ? 24 : 16));
        require(entrySize >= (elfClass == 2 ? 24 : 16), "symbol entry is too small");
        require(section.size() % entrySize == 0, "symbol table has trailing partial entry");
        int count = Math.toIntExact(section.size() / entrySize);
        List<RawSymbol> result = new ArrayList<>(count);
        for (int symbolIndex = 0; symbolIndex < count; symbolIndex++) {
            int offset = Math.toIntExact(section.offset() + (long) symbolIndex * entrySize);
            require(offset + entrySize <= data.length, "symbol entry exceeds file");
            int nameOffset;
            long value;
            long size;
            int info;
            int other;
            int sectionIndex;
            if (elfClass == 2) {
                nameOffset = u32(offset);
                info = u8(offset + 4);
                other = u8(offset + 5);
                sectionIndex = u16(offset + 6);
                value = u64(offset + 8);
                size = u64(offset + 16);
            } else {
                nameOffset = u32(offset);
                value = u32(offset + 4);
                size = u32(offset + 8);
                info = u8(offset + 12);
                other = u8(offset + 13);
                sectionIndex = u16(offset + 14);
            }
            String name = readString((int) (strings.offset() + nameOffset),
                    (int) (strings.offset() + strings.size()));
            result.add(new RawSymbol(section.index(), symbolIndex, name, value, size, info, other, sectionIndex));
        }
        return result;
    }

    private DynamicInfo parseDynamic(List<ElfModel.ElfSection> sections) {
        List<String> needed = new ArrayList<>();
        String soname = null;
        for (ElfModel.ElfSection section : sections) {
            if (section.type() != SHT_DYNAMIC) {
                continue;
            }
            require(section.link() > 0 && section.link() < sections.size(), "dynamic section has invalid link");
            ElfModel.ElfSection strings = sections.get(section.link());
            int entrySize = elfClass == 2 ? 16 : 8;
            require(section.size() % entrySize == 0, "dynamic section has trailing partial entry");
            Long stringAddress = null;
            long stringSize = 0;
            Map<Long, Long> tags = new LinkedHashMap<>();
            for (long cursor = section.offset(); cursor < section.offset() + section.size(); cursor += entrySize) {
                long tag = elfClass == 2 ? u64((int) cursor) : u32((int) cursor);
                long value = elfClass == 2 ? u64((int) cursor + 8) : u32((int) cursor + 4);
                if (tag == 0) {
                    break;
                }
                tags.put(tag, value);
                if (tag == DT_STRTAB) {
                    stringAddress = value;
                }
                if (tag == DT_STRSZ) {
                    stringSize = value;
                }
            }
            long stringOffset = stringAddress == null
                    ? strings.offset()
                    : virtualToOffset(stringAddress, sections).orElse(strings.offset());
            long stringEnd = stringSize > 0 ? stringOffset + stringSize : strings.offset() + strings.size();
            for (Map.Entry<Long, Long> tag : tags.entrySet()) {
                if (tag.getKey() == DT_NEEDED || tag.getKey() == DT_SONAME) {
                    String value = readString(stringOffset + tag.getValue(), stringEnd);
                    if (tag.getKey() == DT_NEEDED) {
                        needed.add(value);
                    } else {
                        soname = value;
                    }
                }
            }
        }
        return new DynamicInfo(needed, soname);
    }

    private java.util.OptionalLong virtualToOffset(long address, List<ElfModel.ElfSection> sections) {
        return sections.stream()
                .filter(section -> section.address() != 0 && address >= section.address()
                        && address < section.address() + Math.max(section.size(), 1))
                .mapToLong(section -> section.offset() + address - section.address())
                .findFirst();
    }

    private String readString(long offset, long end) {
        require(offset >= 0 && offset <= data.length, "string offset outside file");
        long actualEnd = Math.min(end, data.length);
        int start = (int) offset;
        int finish = start;
        while (finish < actualEnd && data[finish] != 0) {
            finish++;
        }
        return new String(data, start, finish - start, StandardCharsets.UTF_8);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ElfParseException(message);
        }
    }

    private int u8(int offset) {
        require(offset >= 0 && offset + 1 <= data.length, "byte read exceeds file");
        return data[offset] & 0xff;
    }

    private int u16(int offset) {
        require(offset >= 0 && offset + 2 <= data.length, "16-bit read exceeds file");
        return Short.toUnsignedInt(buffer.getShort(offset));
    }

    private int u32(int offset) {
        require(offset >= 0 && offset + 4 <= data.length, "32-bit read exceeds file");
        return buffer.getInt(offset);
    }

    private long u64(int offset) {
        require(offset >= 0 && offset + 8 <= data.length, "64-bit read exceeds file");
        return buffer.getLong(offset);
    }

    private record RawSymbol(int tableSectionIndex, int index, String name, long value, long size,
            int info, int other, int sectionIndex) {
    }

    private record VersionName(String name, List<String> aliases) {
    }

    private record DynamicInfo(List<String> needed, String soname) {
    }
}

    private void applyVersions(List<RawSymbol> rawSymbols, List<ElfModel.ElfSection> sections,
            Map<Integer, VersionName> versionNames, List<ElfModel.ElfSymbol> output) {
        int versionSectionIndex = findVersionSection(sections, rawSymbols.get(0).tableSectionIndex());
        int[] rawVersions = versionSectionIndex < 0 ? new int[rawSymbols.size()]
                : readVersionArray(sections.get(versionSectionIndex), rawSymbols.size());
        for (int index = 0; index < rawSymbols.size(); index++) {
            RawSymbol raw = rawSymbols.get(index);
            Integer rawVersion = index < rawVersions.length ? rawVersions[index] : null;
            int cleanVersion = rawVersion == null ? 0 : rawVersion & 0x7fff;
            boolean hidden = rawVersion != null && (rawVersion & 0x8000) != 0;
            VersionName versionName = cleanVersion > 1 ? versionNames.get(cleanVersion) : null;
            Set<String> aliases = versionName == null ? Set.of() : Set.copyOf(versionName.aliases());
            output.add(new ElfModel.ElfSymbol(
                    sectionKind(sections.get(raw.tableSectionIndex())),
                    raw.tableSectionIndex(),
                    raw.index(),
                    raw.name(),
                    raw.value(),
                    raw.size(),
                    raw.info() >> 4,
                    raw.info() & 0xf,
                    raw.other() & 3,
                    raw.sectionIndex(),
                    cleanVersion > 1 ? cleanVersion : null,
                    versionName == null ? null : versionName.name(),
                    hidden,
                    aliases));
        }
    }

    private ElfModel.SymbolTableKind sectionKind(ElfModel.ElfSection section) {
        return section.type() == SHT_DYNSYM ? ElfModel.SymbolTableKind.DYNAMIC : ElfModel.SymbolTableKind.STATIC;
    }

    private int findVersionSection(List<ElfModel.ElfSection> sections, int symbolSectionIndex) {
        for (ElfModel.ElfSection section : sections) {
            if (section.type() == SHT_GNU_VERSYM && section.link() == symbolSectionIndex) {
                return section.index();
            }
        }
        return -1;
    }

    private int[] readVersionArray(ElfModel.ElfSection section, int symbolCount) {
        require(section.size() >= 2L * symbolCount, "version index array is too short");
        int[] result = new int[symbolCount];
        for (int index = 0; index < symbolCount; index++) {
            result[index] = u16((int) (section.offset() + 2L * index));
        }
        return result;
    }

    private List<ElfModel.ElfRelocation> parseRelocations(ElfModel.ElfSection section,
            List<ElfModel.ElfSection> sections, Map<Integer, List<RawSymbol>> rawByTable,
            List<ElfModel.ElfSymbol> parsedSymbols) {
        require(section.link() >= 0 && section.link() < sections.size(), "relocation table has invalid symbol link");
        ElfModel.ElfSection symbolTable = sections.get(section.link());
        require(symbolTable.type() == SHT_SYMTAB || symbolTable.type() == SHT_DYNSYM,
                "relocation table does not link a symbol table");
        int entrySize;
        if (section.type() == SHT_RELA) {
            entrySize = elfClass == 2 ? 24 : 12;
        } else {
            entrySize = elfClass == 2 ? 16 : 8;
        }
        require(section.entrySize() == 0 || section.entrySize() == entrySize, "invalid relocation entry size");
        require(section.size() % entrySize == 0, "relocation table has trailing partial entry");
        int count = Math.toIntExact(section.size() / entrySize);
        List<ElfModel.ElfRelocation> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = Math.toIntExact(section.offset() + (long) index * entrySize);
            require(offset + entrySize <= data.length, "relocation entry exceeds file");
            long relocOffset;
            long info;
            int symbolIndex;
            long type;
            Long addend = null;
            if (elfClass == 2) {
                relocOffset = u64(offset);
                info = u64(offset + 8);
                symbolIndex = (int) (info >> 32);
                type = info & 0xffffffffL;
                if (section.type() == SHT_RELA) {
                    addend = (long) buffer.getLong(offset + 16);
                }
            } else {
                relocOffset = u32(offset);
                info = u32(offset + 4);
                symbolIndex = (int) (info >> 8);
                type = info & 0xff;
                if (section.type() == SHT_RELA) {
                    addend = (long) buffer.getInt(offset + 8);
                }
            }
            ElfModel.ElfSymbol symbol = findSymbol(parsedSymbols, section.link(), symbolIndex);
            result.add(new ElfModel.ElfRelocation(section.index(), section.name(), relocOffset, type,
                    symbolIndex, addend, section.type() == SHT_RELA ? ElfModel.RelocationKind.RELA
                    : ElfModel.RelocationKind.REL, section.link(),
                    symbol == null ? null : symbol.name(),
                    symbol == null ? null : symbol.versionName()));
        }
        return result;
    }

    private ElfModel.ElfSymbol findSymbol(List<ElfModel.ElfSymbol> symbols, int tableSectionIndex, int symbolIndex) {
        for (ElfModel.ElfSymbol symbol : symbols) {
            if (symbol.tableSectionIndex() == tableSectionIndex && symbol.index() == symbolIndex) {
                return symbol;
            }
        }
        return null;
    }

    private Map<Integer, VersionName> parseVersionNames(List<ElfModel.ElfSection> sections) {
        Map<Integer, VersionName> result = new HashMap<>();
        for (ElfModel.ElfSection section : sections) {
            if (section.type() == SHT_GNU_VERDEF) {
                parseVerdefNames(section, sections, result);
            } else if (section.type() == SHT_GNU_VERNEED) {
                parseVerneedNames(section, sections, result);
            }
        }
        return result;
    }

    private void parseVerdefNames(ElfModel.ElfSection section, List<ElfModel.ElfSection> sections,
            Map<Integer, VersionName> target) {
        require(section.link() > 0 && section.link() < sections.size(), "verdef has invalid string link");
        long stringOffset = sections.get(section.link()).offset();
        long stringEnd = stringOffset + sections.get(section.link()).size();
        long cursor = section.offset();
        long end = section.offset() + section.size();
        for (int seen = 0; cursor != 0 && cursor < end && seen < 10000; seen++) {
            require(cursor + 20 <= end && cursor + 20 <= data.length, "truncated verdef entry");
            int index = u16((int) cursor + 6) & 0x7fff;
            int auxOffset = u32((int) cursor + 12);
            int nextOffset = u32((int) cursor + 16);
            String primary = null;
            List<String> aliases = new ArrayList<>();
            if (auxOffset > 0) {
                long aux = cursor + Integer.toUnsignedLong(auxOffset);
                for (int auxSeen = 0; aux != 0 && aux < end && auxSeen < 10000; auxSeen++) {
                    require(aux + 8 <= end && aux + 8 <= data.length, "truncated verdaux entry");
                    String name = readString(stringOffset + u32((int) aux), stringEnd);
                    if (primary == null) {
                        primary = name;
                    } else {
                        aliases.add(name);
                    }
                    int nextAux = u32((int) aux + 4);
                    aux = nextAux == 0 ? 0 : aux + Integer.toUnsignedLong(nextAux);
                }
            }
            if (index > 1 && primary != null) {
                target.put(index, new VersionName(primary, aliases));
            }
            cursor = nextOffset == 0 ? 0 : section.offset() + Integer.toUnsignedLong(nextOffset);
        }
    }

    private void parseVerneedNames(ElfModel.ElfSection section, List<ElfModel.ElfSection> sections,
            Map<Integer, VersionName> target) {
        require(section.link() > 0 && section.link() < sections.size(), "verneed has invalid string link");
        long stringOffset = sections.get(section.link()).offset();
        long stringEnd = stringOffset + sections.get(section.link()).size();
        long entry = section.offset();
        long end = section.offset() + section.size();
        for (int seen = 0; entry != 0 && entry < end && seen < 10000; seen++) {
            require(entry + 16 <= end && entry + 16 <= data.length, "truncated verneed entry");
            int auxOffset = u32((int) entry + 8);
            int nextEntry = u32((int) entry + 12);
            long aux = auxOffset == 0 ? 0 : entry + Integer.toUnsignedLong(auxOffset);
            for (int auxSeen = 0; aux != 0 && aux < end && auxSeen < 10000; auxSeen++) {
                require(aux + 16 <= end && aux + 16 <= data.length, "truncated vernaux entry");
                int index = u16((int) aux + 6) & 0x7fff;
                String name = readString(stringOffset + u32((int) aux), stringEnd);
                int nextAux = u32((int) aux + 12);
                if (index > 1 && !name.isBlank()) {
                    target.put(index, new VersionName(name, List.of()));
                }
                aux = nextAux == 0 ? 0 : aux + Integer.toUnsignedLong(nextAux);
            }
            entry = nextEntry == 0 ? 0 : section.offset() + Integer.toUnsignedLong(nextEntry);
        }
    }

    private List<ElfModel.VersionDefinition> buildDefinitions(List<ElfModel.ElfSection> sections,
            Map<Integer, VersionName> names) {
        return sections.stream()
                .filter(section -> section.type() == SHT_GNU_VERDEF)
                .flatMap(section -> names.values().stream())
                .map(name -> new ElfModel.VersionDefinition(-1, name.name(), name.aliases(), 0))
                .toList();
    }

    private List<ElfModel.VersionNeed> buildNeeds(List<ElfModel.ElfSection> sections,
            Map<Integer, VersionName> names) {
        List<ElfModel.VersionNeed> result = new ArrayList<>();
        for (ElfModel.ElfSection section : sections) {
            if (section.type() != SHT_GNU_VERNEED) {
                continue;
            }
            require(section.link() > 0 && section.link() < sections.size(), "verneed has invalid link");
            long strings = sections.get(section.link()).offset();
            long stringsEnd = strings + sections.get(section.link()).size();
            long entry = section.offset();
            long end = section.offset() + section.size();
            for (int seen = 0; entry != 0 && entry < end && seen < 10000; seen++) {
                require(entry + 16 <= end, "truncated verneed entry");
                String file = readString(strings + u32((int) entry), stringsEnd);
                int auxOffset = u32((int) entry + 8);
                int nextEntry = u32((int) entry + 12);
                List<ElfModel.VersionNeedEntry> entries = new ArrayList<>();
                long aux = auxOffset == 0 ? 0 : entry + Integer.toUnsignedLong(auxOffset);
                for (int auxSeen = 0; aux != 0 && aux < end && auxSeen < 10000; auxSeen++) {
                    require(aux + 16 <= end, "truncated vernaux entry");
                    int rawIndex = u16((int) aux + 6);
                    int index = rawIndex & 0x7fff;
                    entries.add(new ElfModel.VersionNeedEntry(index,
                            readString(strings + u32((int) aux), stringsEnd), (rawIndex & 0x8000) != 0));
                    int nextAux = u32((int) aux + 12);
                    aux = nextAux == 0 ? 0 : aux + Integer.toUnsignedLong(nextAux);
                }
                result.add(new ElfModel.VersionNeed(file, entries));
                entry = nextEntry == 0 ? 0 : section.offset() + Integer.toUnsignedLong(nextEntry);
            }
        }
        return result;
    }

    private List<ElfModel.SectionGroup> parseGroups(List<ElfModel.ElfSection> sections,
            Map<Integer, List<RawSymbol>> rawSymbols) {
        List<ElfModel.SectionGroup> result = new ArrayList<>();
        for (ElfModel.ElfSection section : sections) {
            if (section.type() != SHT_GROUP) {
                continue;
            }
            require(section.size() >= 4, "truncated section group");
            long flags = u32((int) section.offset());
            int wordSize = elfClass == 2 ? 8 : 4;
            require((section.size() - 4) % wordSize == 0, "invalid section group length");
            List<Integer> members = new ArrayList<>();
            List<String> memberNames = new ArrayList<>();
            for (long offset = section.offset() + 4; offset < section.offset() + section.size(); offset += wordSize) {
                int memberIndex = elfClass == 2 ? (int) u64((int) offset) : u32((int) offset);
                members.add(memberIndex);
                memberNames.add(memberIndex >= 0 && memberIndex < sections.size()
                        ? sections.get(memberIndex).name() : "#" + memberIndex);
            }
            String signature = "";
            if (section.link() >= 0 && section.link() < sections.size()) {
                List<RawSymbol> symbols = rawSymbols.get(section.link());
                if (symbols != null && section.info() >= 0 && section.info() < symbols.size()) {
                    signature = symbols.get(section.info()).name();
                }
            }
            result.add(new ElfModel.SectionGroup(section.index(), section.name(), flags, signature,
                    members, memberNames));
        }
        return result;
    }
