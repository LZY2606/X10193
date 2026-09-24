package tribunal.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ElfParser {
    private static final int SHT_NULL = 0;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GROUP = 17;
    private static final int SHT_REL = 9;
    private static final int SHT_RELA = 4;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final int DT_NEEDED = 1;
    private static final int DT_SONAME = 14;
    private static final int DT_STRTAB = 5;
    private static final int SHF_GROUP = 0x200;

    public ParsedObject parse(String objectKey, String displayName, byte[] data) {
        return parse(objectKey, displayName, null, null, data);
    }

    public ParsedObject parse(String objectKey, String displayName, String archiveName, String memberName,
                              byte[] data) {
        try {
            return doParse(objectKey, displayName, archiveName, memberName, data);
        } catch (RuntimeException ex) {
            return ParsedObject.invalid(objectKey, displayName, archiveName, memberName,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private ParsedObject doParse(String objectKey, String displayName, String archiveName, String memberName,
                                 byte[] data) {
        if (data == null || data.length < 16 || data[0] != 0x7f || data[1] != 'E' || data[2] != 'L'
                || data[3] != 'F') {
            throw new ElfParseException("不是 ELF 魔数");
        }
        int elfClass = data[4] & 0xff;
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfParseException("未知 ELF class: " + elfClass);
        }
        int endian = data[5] & 0xff;
        if (endian != 1 && endian != 2) {
            throw new ElfParseException("未知 ELF data encoding: " + endian);
        }
        boolean elf64 = elfClass == 2;
        ByteReader reader = new ByteReader(data, endian == 1);
        int elfType = reader.u16(16);
        int machine = reader.u16(18);
        long sectionHeaderOffset = reader.word(elf64 ? 40 : 32);
        int sectionHeaderEntrySize = reader.u16(elf64 ? 58 : 46);
        int sectionCount = reader.u16(elf64 ? 60 : 48);
        int sectionStringIndex = reader.u16(elf64 ? 62 : 50);
        if (sectionCount <= 0 || sectionHeaderEntrySize <= 0) {
            throw new ElfParseException("缺少 section header table");
        }
        long expected = sectionHeaderOffset + (long) sectionCount * sectionHeaderEntrySize;
        reader.require(sectionHeaderOffset, (long) sectionCount * sectionHeaderEntrySize);
        if (sectionStringIndex >= sectionCount && sectionStringIndex != 0) {
            throw new ElfParseException("section string index 越界: " + sectionStringIndex);
        }

        List<Section> sections = readSections(reader, elf64, sectionHeaderOffset, sectionHeaderEntrySize,
                sectionCount);
        Map<Integer, String> sectionNames = readStringTableAsMap(reader, sections, sectionStringIndex);
        List<SectionGroup> groups = readGroups(reader, sections, sectionNames);
        Map<Integer, String> sectionToGroup = mapSectionsToGroups(groups);
        Map<Integer, VersionName> versions = readVersions(reader, sections, sectionNames);
        String soname = null;
        List<String> needed = new ArrayList<>();
        readDynamic(reader, elf64, sections, sectionNames, versions, needed, new StringHolder());
        StringHolder sonameHolder = new StringHolder();
        readDynamic(reader, elf64, sections, sectionNames, versions, new ArrayList<>(), sonameHolder);
        soname = sonameHolder.value;
        List<ElfSymbol> symbols = readSymbols(reader, elf64, sections, sectionNames, versions, sectionToGroup);
        List<Relocation> relocations = readRelocations(reader, elf64, sections, sectionNames, symbols);
        readDynamicAgain(reader, elf64, sections, sectionNames, versions, needed, sonameHolder);
        if (sonameHolder.value != null) soname = sonameHolder.value;

        return new ParsedObject(objectKey, displayName, archiveName, memberName, true, true, null,
                elfType, machine, elf64, endian == 1, sections, symbols, new ArrayList<>(versions.values()),
                groups, relocations, soname, needed);
    }

    private List<Section> readSections(ByteReader reader, boolean elf64, long start, int entrySize, int count) {
        List<Section> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long off = start + (long) i * entrySize;
            long minimum = elf64 ? 64 : 40;
            reader.require(off, minimum);
            int nameOffset;
            int type;
            long flags;
            long address;
            long offset;
            long size;
            int link;
            int info;
            long entrySizeValue;
            if (elf64) {
                nameOffset = reader.u32AsInt(off);
                type = reader.u32AsInt(off + 4);
                flags = reader.u64(off + 8);
                address = reader.u64(off + 16);
                offset = reader.u64(off + 24);
                size = reader.u64(off + 32);
                link = reader.u32AsInt(off + 40);
                info = reader.u32AsInt(off + 44);
                entrySizeValue = reader.u64(off + 56);
            } else {
                nameOffset = reader.u32AsInt(off);
                type = reader.u32AsInt(off + 4);
                flags = reader.u32(off + 8);
                address = reader.u32(off + 12);
                offset = reader.u32(off + 16);
                size = reader.u32(off + 20);
                link = reader.u32AsInt(off + 24);
                info = reader.u32AsInt(off + 28);
                entrySizeValue = reader.u32(off + 36);
            }
            if (type != SHT_NULL) reader.require(offset, size);
            out.add(new Section(i, "", type, flags, address, offset, size, link, info, entrySizeValue));
        }
        return out;
    }

    private Map<Integer, String> readStringTableAsMap(ByteReader reader, List<Section> sections, int index) {
        Map<Integer, String> names = new HashMap<>();
        if (index <= 0 || index >= sections.size()) return names;
        Section section = sections.get(index);
        for (int i = 0; i < sections.size(); i++) names.put(i, readString(reader, section, sections.get(i).nameOffset()));
        return names;
    }

    private String readString(ByteReader reader, Section stringTable, long offset) {
        if (stringTable == null || offset < 0) return "";
        long start = stringTable.offset();
        long size = stringTable.size();
        long absolute = start + offset;
        if (offset >= size) return "";
        return reader.cString(absolute);
    }

    private List<SectionGroup> readGroups(ByteReader reader, List<Section> sections,
                                          Map<Integer, String> sectionNames) {
        List<SectionGroup> out = new ArrayList<>();
        for (Section section : sections) {
            if (section.type() != SHT_GROUP || section.link() < 0 || section.link() >= sections.size()) continue;
            long entrySize = section.entrySize();
            if (entrySize <= 0) entrySize = 4;
            long count = section.size() / entrySize;
            if (count < 1) continue;
            int flag = (int) reader.word(section.offset(), entrySize == 8);
            List<Integer> members = new ArrayList<>();
            for (long j = 1; j < count; j++) {
                int memberSection = (int) reader.word(section.offset() + j * entrySize, entrySize == 8);
                if (memberSection >= 0 && memberSection < sections.size()) members.add(memberSection);
            }
            Section symbolSection = sections.get(section.link());
            String signature = readSignature(reader, sections, symbolSection, section.info());
            out.add(new SectionGroup(signature, flag == 2, members, section.index()));
        }
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            sections.set(i, new Section(i, sectionNames.getOrDefault(i, ""), section.type(), section.flags(),
                    section.address(), section.offset(), section.size(), section.link(), section.info(),
                    section.entrySize()));
        }
        return out;
    }

    private String readSignature(ByteReader reader, List<Section> sections, Section symbolSection, int info) {
        if (symbolSection == null || symbolSection.link() <= 0 || symbolSection.link() >= sections.size()) {
            return "group#" + info;
        }
        boolean elf64 = false;
        long entrySize = symbolSection.entrySize();
        long symbolOffset = symbolSection.offset() + (long) info * entrySize;
        if (!reader.fits(symbolOffset, Math.max(4, entrySize))) return "group#" + info;
        elf64 = entrySize == 24;
        long nameOffset = reader.word(symbolOffset, elf64);
        return readString(reader, sections.get(symbolSection.link()), nameOffset);
    }

    private Map<Integer, String> mapSectionsToGroups(List<SectionGroup> groups) {
        Map<Integer, String> out = new HashMap<>();
        for (SectionGroup group : groups) {
            for (Integer section : group.sectionIndices()) out.put(section, group.signature());
        }
        return out;
    }

    private Map<Integer, VersionName> readVersions(ByteReader reader, List<Section> sections,
                                                   Map<Integer, String> sectionNames) {
        Map<Integer, VersionName> versions = new LinkedHashMap<>();
        versions.put(0, new VersionName(0, "*local*", false, false, true, null));
        versions.put(1, new VersionName(1, "*global*", false, false, true, null));
        Section def = findSection(sections, SHT_GNU_VERDEF);
        if (def != null) readVerdef(reader, sections, def, versions);
        Section need = findSection(sections, SHT_GNU_VERNEED);
        if (need != null) readVerneed(reader, sections, need, versions);
        return versions;
    }

    private Section findSection(List<Section> sections, int type) {
        for (Section section : sections) if (section.type() == type) return section;
        return null;
    }

    private void readVerdef(ByteReader reader, List<Section> sections, Section verdef,
                            Map<Integer, VersionName> versions) {
        if (verdef.link() <= 0 || verdef.link() >= sections.size()) return;
        Section strings = sections.get(verdef.link());
        long p = verdef.offset();
        long end = p + verdef.size();
        while (p + 20 <= end) {
            long next = reader.u32(p);
            int info = reader.u16(p + 10);
            long aux = reader.u32(p + 16);
            long auxPosition = p + aux;
            String name = null;
            boolean base = false;
            if (aux != 0 && auxPosition + 8 <= end) {
                long nameOffset = reader.u32(auxPosition);
                name = readString(reader, strings, nameOffset);
                long auxNext = reader.u32(auxPosition + 4);
                base = auxNext == 0;
            }
            if (name != null) versions.put(info, new VersionName(info, name, !base, false, true, null));
            if (next == 0) break;
            p += next;
        }
    }

    private void readVerneed(ByteReader reader, List<Section> sections, Section verneed,
                             Map<Integer, VersionName> versions) {
        if (verneed.link() <= 0 || verneed.link() >= sections.size()) return;
        Section strings = sections.get(verneed.link());
        long p = verneed.offset();
        long end = p + verneed.size();
        while (p + 16 <= end) {
            long next = reader.u32(p);
            long fileOffset = reader.u32(p + 8);
            String library = readString(reader, strings, fileOffset);
            long aux = reader.u32(p + 12);
            long a = p + aux;
            while (a + 16 <= end) {
                long hash = reader.u32(a);
                long flags = reader.u16(a + 4);
                int other = reader.u16(a + 6);
                long nameOffset = reader.u32(a + 8);
                long auxNext = reader.u32(a + 12);
                String name = readString(reader, strings, nameOffset);
                versions.put(other, new VersionName(other, name, (flags & 1) == 0, (flags & 2) != 0,
                        false, library));
                if (auxNext == 0) break;
                a += auxNext;
            }
            if (next == 0) break;
            p += next;
        }
    }

    private void readDynamicAgain(ByteReader reader, boolean elf64, List<Section> sections,
                                  Map<Integer, String> sectionNames, Map<Integer, VersionName> ignored,
                                  List<String> needed, StringHolder sonameHolder) {
        readDynamic(reader, elf64, sections, sectionNames, ignored, needed, sonameHolder);
    }

    private void readDynamic(ByteReader reader, boolean elf64, List<Section> sections,
                             Map<Integer, String> sectionNames, Map<Integer, VersionName> ignored,
                             List<String> needed, StringHolder sonameHolder) {
        for (Section section : sections) {
            if (section.type() != SHT_DYNAMIC || section.link() <= 0
                    || section.link() >= sections.size()) continue;
            Section strings = sections.get(section.link());
            long entrySize = section.entrySize();
            if (entrySize <= 0) entrySize = elf64 ? 16 : 8;
            long stringTableAddress = Long.MIN_VALUE;
            List<long[]> entries = new ArrayList<>();
            for (long p = section.offset(); p + entrySize <= section.offset() + section.size(); p += entrySize) {
                long tag = reader.word(p, elf64);
                long value = reader.word(p + (elf64 ? 8 : 4), elf64);
                if (tag == 0) break;
                entries.add(new long[] {tag, value});
                if (tag == DT_STRTAB) stringTableAddress = value;
            }
            if (stringTableAddress == Long.MIN_VALUE) continue;
            Section resolvedStrings = resolveAddressSection(sections, stringTableAddress);
            if (resolvedStrings == null) resolvedStrings = strings;
            for (long[] entry : entries) {
                if (entry[0] == DT_NEEDED) {
                    String value = readString(reader, resolvedStrings, entry[1]);
                    if (!value.isBlank() && !needed.contains(value)) needed.add(value);
                }
                if (entry[0] == DT_SONAME) {
                    sonameHolder.value = readString(reader, resolvedStrings, entry[1]);
                }
            }
        }
    }

    private Section resolveAddressSection(List<Section> sections, long address) {
        for (Section section : sections) {
            if (address >= section.address() && address < section.address() + Math.max(1, section.size())) {
                return section;
            }
        }
        return null;
    }

    private List<ElfSymbol> readSymbols(ByteReader reader, boolean elf64, List<Section> sections,
                                        Map<Integer, String> sectionNames, Map<Integer, VersionName> versions,
                                        Map<Integer, String> sectionToGroup) {
        List<ElfSymbol> all = new ArrayList<>();
        for (Section symbolSection : sections) {
            if (symbolSection.type() != 2 && symbolSection.type() != SHT_DYNSYM) continue;
            if (symbolSection.link() <= 0 || symbolSection.link() >= sections.size()) continue;
            Section strings = sections.get(symbolSection.link());
            Section versym = findVersionSection(sections, symbolSection.index());
            long entrySize = symbolSection.entrySize();
            long expectedEntry = elf64 ? 24 : 16;
            if (entrySize <= 0) entrySize = expectedEntry;
            long count = symbolSection.size() / entrySize;
            for (long index = 0; index < count; index++) {
                long p = symbolSection.offset() + index * entrySize;
                long nameOffset = reader.word(p, elf64);
                int info;
                byte other;
                int sectionIndex;
                long value;
                long size;
                if (elf64) {
                    info = reader.u8(p + 4);
                    other = (byte) reader.u8(p + 5);
                    sectionIndex = reader.u16(p + 6);
                    value = reader.u64(p + 8);
                    size = reader.u64(p + 16);
                } else {
                    value = reader.u32(p + 4);
                    size = reader.u32(p + 8);
                    info = reader.u8(p + 12);
                    other = (byte) reader.u8(p + 13);
                    sectionIndex = reader.u16(p + 14);
                }
                String name = readString(reader, strings, nameOffset);
                if (name.isEmpty() && index == 0) name = "*null*";
                int bindValue = info >> 4;
                int typeValue = info & 0xf;
                int visibilityValue = other & 3;
                Binding binding = switch (bindValue) {
                    case 0 -> Binding.LOCAL;
                    case 1 -> Binding.GLOBAL;
                    case 2 -> Binding.WEAK;
                    case 10 -> Binding.GNU_UNIQUE;
                    default -> Binding.OTHER;
                };
                SymbolType type = switch (typeValue) {
                    case 0 -> SymbolType.NOTYPE;
                    case 1 -> SymbolType.OBJECT;
                    case 2 -> SymbolType.FUNC;
                    case 3 -> SymbolType.SECTION;
                    case 4 -> SymbolType.FILE;
                    case 5 -> SymbolType.COMMON;
                    case 6 -> SymbolType.TLS;
                    default -> SymbolType.OTHER;
                };
                Visibility visibility = switch (visibilityValue) {
                    case 0 -> Visibility.DEFAULT;
                    case 1 -> Visibility.INTERNAL;
                    case 2 -> Visibility.HIDDEN;
                    case 3 -> Visibility.PROTECTED;
                    default -> Visibility.OTHER;
                };
                SymbolKind kind;
                if (sectionIndex == 0) kind = SymbolKind.UNDEFINED;
                else if (sectionIndex == 0xfff2 || sectionIndex == 0xfff1) kind = SymbolKind.COMMON;
                else kind = SymbolKind.DEFINED;
                Integer versionIndex = readVersionIndex(reader, versym, index);
                VersionName versionName = versionIndex == null ? null : versions.get(versionIndex & 0x7fff);
                String sectionName = sectionIndex >= 0 && sectionIndex < sections.size()
                        ? sections.get(sectionIndex).name()
                        : specialSectionName(sectionIndex);
                String groupSignature = sectionIndex >= 0 && sectionIndex < sections.size()
                        ? sectionToGroup.get(sectionIndex) : null;
                all.add(new ElfSymbol((int) index, name, binding, type, visibility, sectionIndex, sectionName,
                        value, size, kind, versionIndex,
                        versionName == null ? null : versionName.name(),
                        versionName != null && versionName.nonDefault(),
                        versionName != null && versionName.weakVersion(), groupSignature, false));
            }
        }
        return mergeSymbolTables(all);
    }

    private Section findVersionSection(List<Section> sections, int symbolSectionIndex) {
        for (Section section : sections) {
            if (section.type() == SHT_GNU_VERSYM && section.link() == symbolSectionIndex) return section;
        }
        return null;
    }

    private Integer readVersionIndex(ByteReader reader, Section versym, long symbolIndex) {
        if (versym == null || symbolIndex >= versym.size() / 2) return null;
        int raw = reader.u16(versym.offset() + symbolIndex * 2);
        if (raw == 0) return null;
        return raw;
    }

    private String specialSectionName(int index) {
        return switch (index) {
            case 0xfff1 -> "ABS";
            case 0xfff2 -> "COMMON";
            case 0xff00 -> "UNDEF";
            default -> "SHN[" + index + "]";
        };
    }

    private List<ElfSymbol> mergeSymbolTables(List<ElfSymbol> symbols) {
        Map<String, ElfSymbol> byName = new LinkedHashMap<>();
        for (ElfSymbol symbol : symbols) {
            if (!symbol.isNamedLinkSymbol() || symbol.kind() == SymbolKind.UNDEFINED) continue;
            ElfSymbol existing = byName.get(symbol.name());
            if (existing == null || score(existing) < score(symbol)) byName.put(symbol.name(), symbol);
        }
        List<ElfSymbol> merged = new ArrayList<>(symbols);
        for (ElfSymbol preferred : byName.values()) {
            boolean replaced = false;
            for (int i = 0; i < merged.size(); i++) {
                ElfSymbol current = merged.get(i);
                if (current.name().equals(preferred.name()) && current.kind() == preferred.kind()
                        && current.versionName() == null && preferred.versionName() != null) {
                    merged.set(i, preferred);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) merged.add(preferred);
        }
        return merged;
    }

    private int score(ElfSymbol symbol) {
        int score = symbol.versionName() == null ? 0 : 2;
        if (symbol.kind() == SymbolKind.DEFINED) score++;
        return score;
    }
