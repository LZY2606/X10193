package court.symbol.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BinaryParser {
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_RELA = 4;
    private static final int SHT_REL = 9;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GROUP = 17;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int DT_NEEDED = 1;
    private static final int DT_SONAME = 14;
    private static final int DT_STRTAB = 5;

    public ParsedBinary parse(byte[] bytes, String fileName) {
        if (bytes.length >= 8 && bytes[0] == '!' && bytes[1] == '<' && bytes[2] == 'a' && bytes[3] == 'r') {
            return parseArchive(bytes, fileName);
}
        return parseElf(bytes, fileName, "");
    }

    public ParsedBinary parseElf(byte[] bytes, String fileName, String memberName) {
        ParsedBinary binary = new ParsedBinary();
        binary.data = bytes;
        binary.fileName = fileName;
        binary.archiveMemberName = memberName;
        if (bytes.length < 64 || bytes[0] != 0x7f || bytes[1] != 'E' || bytes[2] != 'L' || bytes[3] != 'F') {
            throw new ElfReadException("不是 ELF 或 ar 成员");
        }
        int elfClass = bytes[4] & 0xff;
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfReadException("不支持的 ELF class");
        }
        binary.elf64 = elfClass == 2;
        int dataEncoding = bytes[5] & 0xff;
        if (dataEncoding != 1 && dataEncoding != 2) {
            throw new ElfReadException("不支持的 ELF 端序");
        }
        binary.littleEndian = dataEncoding == 1;
        ByteReader reader = new ByteReader(bytes, binary.littleEndian);
        binary.elfType = reader.u16(16);
        binary.machine = reader.u16(18);
        binary.format = switch (binary.elfType) {
            case 1 -> ParsedBinary.Format.ELF_OBJECT;
            case 2 -> ParsedBinary.Format.ELF_EXECUTABLE;
            case 3 -> ParsedBinary.Format.ELF_SHARED;
            default -> throw new ElfReadException("不支持的 ELF 类型 " + binary.elfType);
        };
        parseSections(reader, binary);
        parseDynamic(reader, binary);
        parseSymbols(reader, binary);
        parseRelocations(reader, binary);
        parseGroups(reader, binary);
        parseVersionDefinitions(reader, binary);
        parseVersionNeeds(reader, binary);
        parseVersionSymbols(reader, binary);
        decorateSymbols(binary);
        return binary;
    }

    private void parseSections(ByteReader reader, ParsedBinary binary) {
        int headerSize = binary.elf64 ? 64 : 52;
        if (binary.data.length < headerSize) {
            throw new ElfReadException("ELF 头长度不足");
        }
        long sectionOffset = binary.elf64 ? reader.u64(40) : reader.u32(32);
        int sectionEntrySize = reader.u16(binary.elf64 ? 58 : 46);
        int sectionCount = reader.u16(binary.elf64 ? 60 : 48);
        int stringSection = reader.u16(binary.elf64 ? 62 : 50);
        int expected = binary.elf64 ? 64 : 40;
        if (sectionEntrySize < expected) {
            throw new ElfReadException("Section header entry 过小");
        }
        if (sectionCount == 0) {
            return;
        }
        if (sectionOffset < headerSize || sectionCount > 1048576) {
            throw new ElfReadException("Section header 数量或偏移非法");
        }
        long total = Math.multiplyExact((long) sectionCount, sectionEntrySize);
        if (total > binary.data.length - sectionOffset) {
            throw new ElfReadException("Section header 越界");
        }
        for (int i = 0; i < sectionCount; i++) {
            long offset = sectionOffset + (long) i * sectionEntrySize;
            int nameOffset = (int) reader.u32(offset);
            int type = (int) reader.u32(offset + 4);
            long flags = binary.elf64 ? reader.u64(offset + 8) : reader.u32(offset + 8);
            long address = binary.elf64 ? reader.u64(offset + 16) : reader.u32(offset + 12);
            long sectionDataOffset = binary.elf64 ? reader.u64(offset + 24) : reader.u32(offset + 16);
            long size = binary.elf64 ? reader.u64(offset + 32) : reader.u32(offset + 20);
            int link = (int) reader.u32(offset + (binary.elf64 ? 40 : 24));
            int info = (int) reader.u32(offset + (binary.elf64 ? 44 : 28));
            long entrySize = binary.elf64 ? reader.u64(offset + 56) : reader.u32(offset + 36);
            binary.sections.add(new ElfSection(i, nameOffset, "", type, flags, address, sectionDataOffset, size,
                    link, info, entrySize));
        }
        if (stringSection == 0 || stringSection >= binary.sections.size()) {
            throw new ElfReadException("Section 字符串表索引非法");
        }
        ElfSection stringTable = binary.sections.get(stringSection);
        for (ElfSection section : binary.sections) {
            section.name = section.index == 0 ? "" : readString(reader, stringTable, section.nameOffset);
        }
    }

    private String readString(ByteReader reader, ElfSection stringTable, long offset) {
        if (offset < 0 || offset >= stringTable.size) {
            throw new ElfReadException("字符串偏移越界");
        }
        return reader.cString(stringTable.offset + offset, stringTable.size - offset);
    }

    private void parseDynamic(ByteReader reader, ParsedBinary binary) {
        for (ElfSection section : sectionsOfType(binary, SHT_DYNAMIC)) {
            requireSection(binary, section);
            if (section.link <= 0 || section.link >= binary.sections.size()) {
                throw new ElfReadException("动态字符串表索引非法");
            }
            ElfSection strings = binary.sections.get(section.link);
            int entrySize = binary.elf64 ? 16 : 8;
            if (section.entrySize != 0 && section.entrySize != entrySize || section.size % entrySize != 0) {
                throw new ElfReadException("dynamic entry 长度非法");
            }
            for (long pos = 0; pos < section.size; pos += entrySize) {
                long tag = binary.elf64 ? reader.u64(section.offset + pos) : reader.u32(section.offset + pos);
                long value = binary.elf64 ? reader.u64(section.offset + pos + 8)
                        : reader.u32(section.offset + pos + 4);
                if (tag == 0) {
                    break;
                }
                if (tag == DT_NEEDED) {
                    binary.needed.add(readString(reader, strings, value));
                } else if (tag == DT_SONAME) {
                    binary.soname = readString(reader, strings, value);
                }
            }
        }
    }

    private void parseSymbols(ByteReader reader, ParsedBinary binary) {
        for (ElfSection section : binary.sections) {
            if (section.type != SHT_SYMTAB && section.type != SHT_DYNSYM) {
                continue;
            }
            requireSection(binary, section);
            if (section.link <= 0 || section.link >= binary.sections.size()) {
                throw new ElfReadException("符号字符串表索引非法");
            }
            ElfSection strings = binary.sections.get(section.link);
            int entrySize = binary.elf64 ? 24 : 16;
            if (section.entrySize != 0 && section.entrySize != entrySize || section.size % entrySize != 0) {
                throw new ElfReadException("symbol entry 长度非法");
            }
            List<ElfSymbol> target = section.type == SHT_DYNSYM ? binary.dynamicSymbols : binary.staticSymbols;
            int count = (int) (section.size / entrySize);
            for (int index = 0; index < count; index++) {
                long pos = section.offset + (long) index * entrySize;
                int nameOffset;
                long value;
                long size;
                int info;
                int other;
                int sectionIndex;
                if (binary.elf64) {
                    nameOffset = (int) reader.u32(pos);
                    info = reader.u8(pos + 4);
                    other = reader.u8(pos + 5);
                    sectionIndex = reader.u16(pos + 6);
                    value = reader.u64(pos + 8);
                    size = reader.u64(pos + 16);
                } else {
                    nameOffset = (int) reader.u32(pos);
                    value = reader.u32(pos + 4);
                    size = reader.u32(pos + 8);
                    info = reader.u8(pos + 12);
                    other = reader.u8(pos + 13);
                    sectionIndex = reader.u16(pos + 14);
                }
                ElfSymbol symbol = new ElfSymbol(index, readString(reader, strings, nameOffset & 0xffffffffL),
                        value, size, info, other, sectionIndex, section.type == SHT_DYNSYM);
                symbol.tableSectionIndex = section.index;
                target.add(symbol);
                binary.symbols.add(symbol);
            }
        }
    }

    private List<ElfSection> sectionsOfType(ParsedBinary binary, int type) {
        return binary.sections.stream().filter(section -> section.type == type).toList();
    }

    private void requireSection(ParsedBinary binary, ElfSection section) {
        if (section.offset < 0 || section.size < 0 || section.offset > binary.data.length
                || section.size > binary.data.length - section.offset) {
            throw new ElfReadException("section 数据越界: " + section.name);
        }
    }

    private void parseRelocations(ByteReader reader, ParsedBinary binary) {
        for (ElfSection section : binary.sections) {
            if (section.type != SHT_REL && section.type != SHT_RELA) {
                continue;
            }
            requireSection(binary, section);
            int entrySize = binary.elf64 ? (section.type == SHT_RELA ? 24 : 16)
                    : (section.type == SHT_RELA ? 12 : 8);
            if (section.entrySize != 0 && section.entrySize != entrySize || section.size % entrySize != 0) {
                throw new ElfReadException("relocation entry 长度非法");
            }
            if (section.info <= 0 || section.info >= binary.sections.size()) {
                throw new ElfReadException("relocation 目标 section 索引非法");
            }
            ElfSection target = binary.sections.get(section.info);
            if (section.link < 0 || section.link >= binary.sections.size()) {
                throw new ElfReadException("relocation 符号表索引非法");
            }
            boolean dynamic = section.link < binary.sections.size()
                    && binary.sections.get(section.link).type == SHT_DYNSYM;
            int count = (int) (section.size / entrySize);
            for (int i = 0; i < count; i++) {
                long pos = section.offset + (long) i * entrySize;
                long offset = binary.elf64 ? reader.u64(pos) : reader.u32(pos);
                long info;
                long addend = 0;
                if (binary.elf64) {
                    info = reader.u64(pos + 8);
                    if (section.type == SHT_RELA) {
                        addend = reader.u64(pos + 16);
                    }
                } else {
                    info = reader.u32(section.type == SHT_RELA ? pos + 4 : pos + 4);
                    if (section.type == SHT_RELA) {
                        addend = Integer.toUnsignedLong((int) reader.u32(pos + 8));
                    }
                }
                int symbolIndex = (int) (binary.elf64 ? info >>> 32 : info >>> 8);
                int type = (int) (binary.elf64 ? info : info & 0xff);
                ElfRelocation relocation = new ElfRelocation(target.index, target.name, offset, symbolIndex,
                        type, addend, section.type == SHT_RELA, dynamic);
                List<ElfSymbol> table = dynamic ? binary.dynamicSymbols : binary.staticSymbols;
                if (symbolIndex < 0 || symbolIndex >= table.size()) {
                    throw new ElfReadException("relocation 符号索引越界");
                }
                relocation.symbolName = table.get(symbolIndex).name;
                binary.relocations.add(relocation);
            }
        }
    }

    private void parseGroups(ByteReader reader, ParsedBinary binary) {
        for (ElfSection section : sectionsOfType(binary, SHT_GROUP)) {
            requireSection(binary, section);
            if (section.size < 4 || section.size % 4 != 0) {
                throw new ElfReadException("group entry 长度非法");
            }
            if (section.link < 0 || section.link >= binary.sections.size()) {
                throw new ElfReadException("group 符号表索引非法");
            }
            ElfSection symbolTable = binary.sections.get(section.link);
            List<ElfSymbol> table = symbolTable.type == SHT_DYNSYM ? binary.dynamicSymbols : binary.staticSymbols;
            if (section.info < 0 || section.info >= table.size()) {
                throw new ElfReadException("group 签名符号索引越界");
            }
            int flags = (int) reader.u32(section.offset);
            List<Integer> members = new ArrayList<>();
            for (long pos = 4; pos < section.size; pos += 4) {
                int member = (int) reader.u32(section.offset + pos);
                if (member < 0 || member >= binary.sections.size()) {
                    throw new ElfReadException("group 成员 section 索引越界");
                }
                members.add(member);
            }
            String signature = table.get(section.info).name;
            SectionGroup group = new SectionGroup(signature, flags == 2, section.info, members);
            binary.groups.add(group);
            for (int sectionIndex : members) {
                binary.sections.get(sectionIndex).groupSignature = signature;
                for (ElfSymbol symbol : table) {
                    if (symbol.sectionIndex == sectionIndex && !symbol.name.isEmpty()
                            && !symbol.isLocal() && symbol.isDefined()) {
                        symbol.groupSignature = signature;
                    }
                }
            }
        }
    }

    private void parseVersionDefinitions(ByteReader reader, ParsedBinary binary) {
        for (ElfSection section : sectionsOfType(binary, SHT_GNU_VERDEF)) {
            requireSection(binary, section);
            if (section.link <= 0 || section.link >= binary.sections.size()) {
                throw new ElfReadException("verdef 字符串表索引非法");
            }
            ElfSection strings = binary.sections.get(section.link);
            long pos = 0;
            while (pos < section.size) {
                if (pos + 20 > section.size) {
                    throw new ElfReadException("verdef 长度越界");
                }
                int version = reader.u16(section.offset + pos);
                int flags = reader.u16(section.offset + pos + 2);
                int index = reader.u16(section.offset + pos + 4);
                int count = reader.u16(section.offset + pos + 6);
                long aux = reader.u32(section.offset + pos + 12);
                long next = reader.u32(section.offset + pos + 8);
                if (version != 1 || count < 1 || aux == 0 || pos + aux + 8 > section.size) {
                    throw new ElfReadException("verdef 格式非法");
                }
                long auxPos = pos + aux;
                long nameOffset = reader.u32(section.offset + auxPos);
                String name = readString(reader, strings, nameOffset);
                binary.versionDefinitions.add(new VersionDefinition(index, name, flags != 2, flags));
                if (next == 0) {
                    break;
                }
                pos += next;
            }
        }
    }

    private void parseVersionNeeds(ByteReader reader, ParsedBinary binary) {
        for (ElfSection section : sectionsOfType(binary, SHT_GNU_VERNEED)) {
            requireSection(binary, section);
            if (section.link <= 0 || section.link >= binary.sections.size()) {
                throw new ElfReadException("verneed 字符串表索引非法");
            }
            ElfSection strings = binary.sections.get(section.link);
            long needPos = 0;
            while (needPos < section.size) {
                if (needPos + 16 > section.size) {
                    throw new ElfReadException("verneed 长度越界");
                }
                int version = reader.u16(section.offset + needPos);
                int count = reader.u16(section.offset + needPos + 2);
                long fileOffset = reader.u32(section.offset + needPos + 4);
                long aux = reader.u32(section.offset + needPos + 8);
                long next = reader.u32(section.offset + needPos + 12);
                if (version != 1 || count < 1 || aux == 0 || needPos + 16 + 16 > section.size) {
                    throw new ElfReadException("verneed 格式非法");
                }
                String library = readString(reader, strings, fileOffset);
                List<VersionNeed.Requirement> requirements = new ArrayList<>();
                long auxPos = needPos + aux;
                for (int i = 0; i < count; i++) {
                    if (auxPos + 16 > section.size) {
                        throw new ElfReadException("vernaux 长度越界");
                    }
                    int flags = reader.u16(section.offset + auxPos + 4);
                    int index = reader.u16(section.offset + auxPos + 8);
                    long nameOffset = reader.u32(section.offset + auxPos + 12);
                    long auxNext = reader.u32(section.offset + auxPos + 12);
                    requirements.add(new VersionNeed.Requirement(index,
                            readString(reader, strings, nameOffset), flags != 2, flags));
                    if (auxNext == 0) {
                        break;
                    }
                    auxPos += auxNext;
                }
                binary.versionNeeds.add(new VersionNeed(library, requirements));
                if (next == 0) {
                    break;
                }
                needPos += next;
            }
        }
    }

    private void parseVersionSymbols(ByteReader reader, ParsedBinary binary) {
        for (ElfSection section : sectionsOfType(binary, SHT_GNU_VERSYM)) {
            requireSection(binary, section);
            if (section.size / 2 < binary.dynamicSymbols.size()) {
                throw new ElfReadException("versym 条目少于动态符号");
            }
            Map<Integer, VersionDefinition> definitions = new HashMap<>();
            Map<Integer, VersionNeed.Requirement> requirements = new HashMap<>();
            Map<Integer, String> requirementLibraries = new HashMap<>();
            for (VersionDefinition definition : binary.versionDefinitions) {
                definitions.put(definition.index(), definition);
            }
            for (VersionNeed need : binary.versionNeeds) {
                for (VersionNeed.Requirement requirement : need.requirements()) {
                    requirements.put(requirement.index(), requirement);
                    requirementLibraries.put(requirement.index(), need.library());
                }
            }
            for (int i = 0; i < binary.dynamicSymbols.size(); i++) {
                ElfSymbol symbol = binary.dynamicSymbols.get(i);
                int raw = reader.u16(section.offset + i * 2L);
                int visibleIndex = raw & 0x7fff;
                symbol.versionIndex = raw;
                symbol.versionDefault = (raw & 0x8000) == 0;
                VersionDefinition definition = definitions.get(visibleIndex);
                if (definition != null) {
                    symbol.versionName = definition.name();
                    symbol.versionDefault = definition.defaultVersion() && symbol.versionDefault;
                } else {
                    VersionNeed.Requirement requirement = requirements.get(visibleIndex);
                    if (requirement != null) {
                        symbol.versionName = requirement.name();
                        symbol.versionLibrary = requirementLibraries.get(visibleIndex);
                        symbol.versionRequirement = true;
                        symbol.versionDefault = requirement.defaultVersion() && symbol.versionDefault;
                    }
                }
            }
        }
    }

    private void decorateSymbols(ParsedBinary binary) {
        for (ElfSymbol symbol : binary.symbols) {
            symbol.sectionName = switch (symbol.sectionIndex) {
                case 0 -> "UND";
                case 0xfff1 -> "ABS";
                case 0xfff2 -> "COMMON";
                default -> symbol.sectionIndex > 0 && symbol.sectionIndex < binary.sections.size()
                        ? binary.sections.get(symbol.sectionIndex).name : "UNKNOWN";
            };
        }
    }

    private ParsedBinary parseArchive(byte[] bytes, String fileName) {
        ParsedBinary archive = new ParsedBinary();
        archive.data = bytes;
        archive.fileName = fileName;
        archive.format = ParsedBinary.Format.ARCHIVE;
        int pos = 8;
        String longNames = "";
        while (pos < bytes.length) {
            if (pos + 60 > bytes.length) {
                throw new ElfReadException("ar 成员头长度不足");
            }
            String rawName = new String(bytes, pos, 16, StandardCharsets.US_ASCII).trim();
            long size = parseDecimal(bytes, pos + 48, 10, "ar 成员大小");
            long contentOffset = pos + 60L;
            if (size < 0 || contentOffset > bytes.length || size > bytes.length - contentOffset) {
                throw new ElfReadException("ar 成员数据越界");
            }
            String memberName = rawName;
            long dataOffset = contentOffset;
            if (rawName.startsWith("#1/")) {
                int nameLength = (int) parseDecimal(rawName.substring(3), "BSD ar 成员名长度");
                if (nameLength > size) {
                    throw new ElfReadException("BSD ar 成员名越界");
                }
                memberName = new String(bytes, (int) contentOffset, nameLength, StandardCharsets.US_ASCII).trim();
                contentOffset += nameLength;
                size -= nameLength;
            } else if (rawName.equals("//")) {
                longNames = new String(bytes, (int) contentOffset, (int) size, StandardCharsets.US_ASCII);
            } else if (rawName.startsWith("/") && !rawName.equals("/")) {
                long nameOffset = parseDecimal(rawName.substring(1), "GNU ar 长名偏移");
                if (nameOffset < 0 || nameOffset >= longNames.length()) {
                    throw new ElfReadException("GNU ar 长名偏移越界");
                }
                int end = longNames.indexOf('\n', (int) nameOffset);
                if (end < 0) {
                    end = longNames.length();
                }
                memberName = longNames.substring((int) nameOffset, end).replace("/", "").trim();
            } else {
                memberName = rawName.replace("/", "").trim();
            }
            byte[] memberData = new ByteReader(bytes, true).slice(contentOffset, size);
            if (!rawName.equals("//") && !rawName.equals("/") && !rawName.startsWith("/SYM")) {
                try {
                    ParsedBinary member = parseElf(memberData, fileName, memberName);
                    member.dataOffset = dataOffset;
                    archive.archiveMembers.add(member);
                } catch (RuntimeException ex) {
                    archive.memberErrors.add(memberName + ": " + ex.getMessage());
                }
            }
            long next = contentOffset + size + (size & 1L);
            if (next > Integer.MAX_VALUE) {
                throw new ElfReadException("ar 文件过大");
            }
            pos = (int) next;
        }
        return archive;
    }

    private long parseDecimal(byte[] bytes, int offset, int length, String description) {
        return parseDecimal(new String(bytes, offset, length, StandardCharsets.US_ASCII).trim(), description);
    }

    private long parseDecimal(String value, String description) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ElfReadException(description + " 非法: " + value);
        }
    }
}
