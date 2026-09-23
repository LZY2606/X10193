package com.example.symbolcourt.elf;

import com.example.symbolcourt.elf.ElfModels.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class ElfParser {
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_RELA = 4;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_REL = 9;
    private static final int SHT_GROUP = 17;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final int DT_NEEDED = 1;
    private static final int SHN_UNDEF = 0;
    private static final int SHN_COMMON = 0xfff2;

    public List<ObjectUnit> parsePaths(List<String> paths) {
        List<ObjectUnit> units = new ArrayList<>();
        int order = 0;
        for (String raw : paths) {
            Path path = Path.of(raw);
            String display = path.getFileName() == null ? raw : path.getFileName().toString();
            try {
                byte[] bytes = Files.readAllBytes(path);
                units.addAll(parseBytes(display, raw, bytes, order++));
            } catch (Exception ex) {
                units.add(corrupt(display, raw, order++, "读取失败: " + ex.getMessage()));
            }
        }
        return units;
    }

    public List<ObjectUnit> parseBytes(String name, String path, byte[] bytes, int order) {
        if (isArchive(bytes)) return parseArchive(name, path, bytes, order);
        return List.of(parseElf(name, path, bytes, order, null, null, false));
    }

    public ObjectUnit corrupt(String name, String path, int order, String message) {
        return new ObjectUnit("u" + order, name, path, null, ObjectKind.CORRUPT, order, "", null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(message), true, false, true);
    }

    private boolean isArchive(byte[] b) {
        return b.length >= 8 && new String(b, 0, 8).equals("!<arch>\n");
    }

    private List<ObjectUnit> parseArchive(String archiveName, String path, byte[] bytes, int archiveOrder) {
        List<ObjectUnit> result = new ArrayList<>();
        int pos = 8;
        int memberNo = 0;
        try {
            while (pos + 60 <= bytes.length) {
                String rawHeader = ascii(bytes, pos, 60);
                String rawName = rawHeader.substring(0, 16).trim();
                long size = Long.parseLong(rawHeader.substring(48, 58).trim());
                int dataStart = pos + 60;
                int dataEnd = Math.toIntExact(dataStart + size);
                if (dataEnd > bytes.length) throw new ElfParseException("archive member exceeds archive");
                String memberName = archiveMemberName(rawName, bytes, dataStart, dataEnd);
                if (!rawName.equals("/") && !rawName.startsWith("/SYM") && !rawName.equals("//")) {
                    byte[] member = slice(bytes, dataStart, dataEnd);
                    String memberPath = path + "!(" + memberName + ")";
                    if (member.length >= 4 && member[0] == 0x7f && member[1] == 'E' && member[2] == 'L' && member[3] == 'F') {
                        result.add(parseElf(memberName, memberPath, member, archiveOrder, archiveName, null, false));
                    } else {
                        result.add(new ObjectUnit(archiveName + "#" + memberNo, memberName, memberPath, archiveName,
                                ObjectKind.CORRUPT, archiveOrder, sha256(member), null, List.of(), List.of(), List.of(),
                                List.of(), List.of(), List.of(), List.of("archive member 不是 ELF"), true, true, false));
                    }
                }
                pos = dataEnd + (size % 2 == 0 ? 0 : 1);
                memberNo++;
            }
        } catch (Exception ex) {
            result.add(new ObjectUnit(archiveName + "#corrupt", "corrupt-member", path + "!corrupt", archiveName,
                    ObjectKind.CORRUPT, archiveOrder, "", null, List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of("损坏 archive: " + ex.getMessage()), true, true, false));
        }
        return result;
    }

    private String archiveMemberName(String rawName, byte[] bytes, int dataStart, int dataEnd) {
        if (rawName.startsWith("/") && Character.isDigit(rawName.charAt(1))) {
            try {
                int offset = Integer.parseInt(rawName.substring(1).trim());
                int end = offset;
                while (end < bytes.length && bytes[end] != '/' && bytes[end] != '\n' && bytes[end] != 0x60) end++;
                if (end > offset) return ascii(bytes, offset, end);
            } catch (Exception ignored) { }
        }
        if (rawName.endsWith("/")) return rawName.substring(0, rawName.length() - 1);
        return rawName;
    }

    private ObjectUnit parseElf(String name, String path, byte[] b, int order, String archiveName,
                                Void unused, boolean forceExtracted) {
        List<String> errors = new ArrayList<>();
        try {
            Reader r = new Reader(b);
            if (b.length < 64 || b[0] != 0x7f || b[1] != 'E' || b[2] != 'L' || b[3] != 'F') {
                throw new ElfParseException("invalid ELF magic");
            }
            int elfClass = r.u8(4);
            int encoding = r.u8(5);
            boolean le = encoding == 1;
            r.littleEndian = le;
            if (elfClass != 1 && elfClass != 2) throw new ElfParseException("unsupported EI_CLASS " + elfClass);
            int type = elfClass == 2 ? r.u16leBe(16, le) : r.u16leBe(16, le);
            int machine = r.u16leBe(18, le);
            ObjectKind kind = switch (type) { case 1 -> ObjectKind.REL; case 2 -> ObjectKind.EXECUTABLE; case 3 -> ObjectKind.SHARED; default -> ObjectKind.REL; };

            int sectionHeaderOffsetOffset = elfClass == 2 ? 40 : 32;
            int sectionHeaderEntrySizeOffset = elfClass == 2 ? 58 : 46;
            int sectionCountOffset = elfClass == 2 ? 60 : 48;
            int sectionStringIndexOffset = elfClass == 2 ? 62 : 50;
            long shoff = r.longAt(sectionHeaderOffsetOffset, elfClass, le);
            int shentsize = r.u16leBe(sectionHeaderEntrySizeOffset, le);
            int shnum = r.u16leBe(sectionCountOffset, le);
            int shstrndx = r.u16leBe(sectionStringIndexOffset, le);
            if (shoff <= 0 || shnum <= 0) throw new ElfParseException("missing section headers");
            if (shoff + (long) shnum * shentsize > b.length) throw new ElfParseException("section headers out of bounds");

            List<Section> sections = readSections(r, shoff, shentsize, shnum, elfClass, le);
            String[] sectionNames = readStringTable(b, sections, shstrndx);
            List<VersionDefinition> definitions = new ArrayList<>();
            List<VersionRequirement> requirements = new ArrayList<>();
            for (int i = 0; i < sections.size(); i++) {
                Section section = sections.get(i);
                if (section.type == SHT_GNU_VERDEF) definitions.addAll(readVerdef(b, section, errors));
                if (section.type == SHT_GNU_VERNEED) requirements.addAll(readVerneed(b, sections, section, errors));
            }
            List<ElfSymbol> symbols = new ArrayList<>();
            symbols.addAll(readSymbolTable(b, r, sections, sectionNames, definitions, requirements, SHT_SYMTAB, false, elfClass, le, errors));
            symbols.addAll(readSymbolTable(b, r, sections, sectionNames, definitions, requirements, SHT_DYNSYM, true, elfClass, le, errors));
            List<String> needed = readNeeded(b, sections, elfClass, le, errors);
            List<String> groups = readGroups(b, sections, sectionNames, symbols, errors);
            boolean dynSymPresent = sections.stream().anyMatch(section -> section.type == SHT_DYNSYM);
            List<ElfRelocation> relocations = readRelocations(b, r, sections, sectionNames, symbols, elfClass, le, errors, machine, dynSymPresent);
            String id = archiveName == null ? "u" + order : archiveName + "#" + name;
            return new ObjectUnit(id, name, path, archiveName, kind, order, sha256(b),
                    new ElfIdentity(elfClass, encoding, machine, le), needed, symbols, relocations,
                    definitions, requirements, groups, List.copyOf(errors), false, archiveName != null,
                    archiveName == null || forceExtracted);
        } catch (Exception ex) {
            return new ObjectUnit(archiveName == null ? "u" + order : archiveName + "#" + name, name, path,
                    archiveName, ObjectKind.CORRUPT, order, safeSha(b), null, List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of("损坏对象已隔离: " + ex.getMessage()), true,
                    archiveName != null, archiveName == null);
        }
    }

    private List<Section> readSections(Reader r, long off, int entsize, int count, int elfClass, boolean le) {
        List<Section> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int p = Math.toIntExact(off + (long) i * entsize);
            int nameOff = r.u16leBe(p, le);
            int type = r.u32(p + 4, le);
            long flags = r.longAt(p + 8, elfClass, le);
            long addr = r.longAt(p + (elfClass == 2 ? 16 : 12), elfClass, le);
            int offset = r.u32(p + (elfClass == 2 ? 24 : 16), le);
            long size = r.longAt(p + (elfClass == 2 ? 32 : 20), elfClass, le);
            int link = r.u32(p + (elfClass == 2 ? 40 : 24), le);
            int info = r.u32(p + (elfClass == 2 ? 44 : 28), le);
            int ents = r.u32(p + (elfClass == 2 ? 56 : 36), le);
            list.add(new Section(nameOff, type, flags, addr, offset, size, link, info, ents));
        }
        return list;
    }

    private String[] readStringTable(byte[] b, List<Section> sections, int index) {
        if (index <= 0 || index >= sections.size()) return new String[0];
        Section s = sections.get(index);
        if (s.type != SHT_STRTAB || s.offset + s.size > b.length) return new String[0];
        String[] values = new String[(int) Math.min(s.size, Integer.MAX_VALUE)];
        int start = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.size; i++) {
            int ch = Byte.toUnsignedInt(b[s.offset + i]);
            if (ch == 0) {
                values[start] = current.toString();
                current.setLength(0);
                start = i + 1;
            } else {
                if (current.isEmpty()) start = i;
                current.append((char) ch);
            }
        }
        return values;
    }

    private String getString(byte[] b, Section table, int offset) {
        if (table == null || offset < 0 || offset >= table.size) return "";
        int start = table.offset + offset;
        int end = start;
        while (end < b.length && b[end] != 0) end++;
        return ascii(b, start, end);
    }

    private List<ElfSymbol> readSymbolTable(byte[] b, Reader r, List<Section> sections, String[] sectionNames,
                                            List<VersionDefinition> defs, List<VersionRequirement> reqs,
                                            int tableType, boolean dynamic, int elfClass, boolean le, List<String> errors) {
        List<ElfSymbol> out = new ArrayList<>();
        for (int si = 1; si < sections.size(); si++) {
            Section sym = sections.get(si);
            if (sym.type != tableType) continue;
            Section str = sym.link > 0 && sym.link < sections.size() ? sections.get(sym.link) : null;
            Section versec = findSection(sections, SHT_GNU_VERSYM).orElse(null);
            int entry = sym.entsize != 0 ? sym.entsize : elfClass == 2 ? 24 : 16;
            long count = entry == 0 ? 0 : sym.size / entry;
            for (int index = 0; index < count; index++) {
                int p = sym.offset + index * entry;
                try {
                    int nameOff = r.u32(p, le);
                    int info = b[p + (elfClass == 2 ? 4 : 12)] & 255;
                    int other = b[p + (elfClass == 2 ? 5 : 13)] & 255;
                    int shndx = r.u16leBe(p + (elfClass == 2 ? 6 : 14), le);
                    long value = r.longAt(p + (elfClass == 2 ? 8 : 4), elfClass, le);
                    long size = r.longAt(p + (elfClass == 2 ? 16 : 8), elfClass, le);
                    String name = getString(b, str, nameOff);
                    if (index == 0 && name.isEmpty()) continue;
                    Integer versionIndex = readVersionIndex(b, r, versec, index, le);
                    String versionName = resolveVersionName(versionIndex, defs, reqs);
                    boolean nonDefault = resolveNonDefault(versionIndex, defs, reqs);
                    String sectionName = shndx > 0 && shndx < sectionNames.length ? sectionNames[shndx]
                            : shndx == SHN_COMMON ? "SHN_COMMON" : shndx == SHN_UNDEF ? "UND" : "ABS";
                    String group = groupSignatureFor(sections, sectionNames, shndx);
                    out.add(new ElfSymbol(index, name, info >> 4, info & 15, other & 3, shndx, sectionName,
                            value, size, dynamic, versionIndex, versionName, nonDefault, group));
                } catch (Exception ex) {
                    errors.add("symbol index " + index + " 越界已跳过");
                }
            }
        }
        return out;
    }

    private Integer readVersionIndex(byte[] b, Reader r, Section versec, int symbolIndex, boolean le) {
        if (versec == null || symbolIndex * 2L + 2 > versec.size) return null;
        return r.u16leBe(versec.offset + symbolIndex * 2, le);
    }

    private String resolveVersionName(Integer index, List<VersionDefinition> defs, List<VersionRequirement> reqs) {
        if (index == null || index == 0 || index == 1) return index != null && index == 1 ? "(local)" : null;
        if ((index & 0x7fff) == 0) return null;
        int bare = index & 0x7fff;
        for (VersionDefinition d : defs) if (d.index == bare) return d.name;
        for (VersionRequirement q : reqs) if (q.index == bare) return q.versionName;
        return null;
    }

    private boolean resolveNonDefault(Integer index, List<VersionDefinition> defs, List<VersionRequirement> reqs) {
        if (index == null || (index & 0x8000) != 0) return true;
        int bare = index & 0x7fff;
        for (VersionDefinition d : defs) if (d.index == bare) return d.nonDefault;
        return false;
    }

    private String groupSignatureFor(List<Section> sections, String[] names, int symbolSectionIndex) {
        if (symbolSectionIndex <= 0 || symbolSectionIndex >= sections.size()) return null;
        Section member = sections.get(symbolSectionIndex);
        for (int i = 0; i < sections.size(); i++) {
            Section g = sections.get(i);
            if (g.type == SHT_GROUP && g.info == member.info && i < names.length) return names[i];
        }
        return null;
    }

    private List<String> readNeeded(byte[] b, List<Section> sections, int elfClass, boolean le, List<String> errors) {
        List<String> values = new ArrayList<>();
        for (Section dynamic : sections) {
            if (dynamic.type != SHT_DYNAMIC) continue;
            Section str = dynamic.link > 0 && dynamic.link < sections.size() ? sections.get(dynamic.link) : null;
            int entry = elfClass == 2 ? 16 : 8;
            for (long off = 0; off + entry <= dynamic.size; off += entry) {
                int p = dynamic.offset + (int) off;
                long tag = new Reader(b).longAt(p, elfClass, le);
                if (tag == 0) break;
                long value = new Reader(b).longAt(p + (elfClass == 2 ? 8 : 4), elfClass, le);
                if (tag == DT_NEEDED) values.add(getString(b, str, (int) value));
            }
        }
        return values;
    }

    private List<String> readGroups(byte[] b, List<Section> sections, String[] sectionNames,
                                    List<ElfSymbol> symbols, List<String> errors) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            if (s.type == SHT_GROUP && i < sectionNames.length && !sectionNames[i].isBlank()) {
                values.add(sectionNames[i]);
            }
        }
        return values;
    }

    private List<ElfRelocation> readRelocations(byte[] b, Reader r, List<Section> sections, String[] sectionNames,
                                                List<ElfSymbol> allSymbols, int elfClass, boolean le,
                                                List<String> errors, int machine, boolean objectHasDynSym) {
        List<ElfRelocation> out = new ArrayList<>();
        int copyType = copyRelocationType(machine);
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            if (section.type != SHT_REL && section.type != SHT_RELA) continue;
            boolean rela = section.type == SHT_RELA;
            int symSectionIndex = section.link;
            List<ElfSymbol> linked = symbolsForTable(sections, allSymbols, symSectionIndex);
            int targetSection = section.info;
            String targetName = targetSection >= 0 && targetSection < sectionNames.length ? sectionNames[targetSection] : "";
            int entry = rela ? (elfClass == 2 ? 24 : 12) : (elfClass == 2 ? 16 : 8);
            for (long n = 0; n + entry <= section.size; n += entry) {
                int p = section.offset + (int) n;
                long offset = r.longAt(p, elfClass, le);
                long info = r.longAt(p + (elfClass == 2 ? 8 : 4), elfClass, le);
                int type = elfClass == 2 ? (int) (info & 0xffffffff) : (int) (info & 0xff);
                int sym = elfClass == 2 ? (int) (info >>> 32) : (int) (info >>> 8);
                Long addend = rela ? r.longAt(p + (elfClass == 2 ? 16 : 8), elfClass, le) : null;
                ElfSymbol symbol = sym < linked.size() ? linked.get(sym) : null;
                String name = symbol == null ? "#" + sym : symbol.name;
                String version = symbol == null ? null : symbol.versionName;
                out.add(new ElfRelocation(offset, type, sym, name, targetName, addend, type == copyType, version,
                        section.link >= 0 && section.link < sections.size() && sections.get(section.link).type == SHT_DYNSYM));
            }
        }
        return out;
    }

    private List<ElfSymbol> symbolsForTable(List<Section> sections, List<ElfSymbol> symbols, int symSectionIndex) {
        if (symSectionIndex < 0 || symSectionIndex >= sections.size()) return List.of();
        boolean dynamic = sections.get(symSectionIndex).type == SHT_DYNSYM;
        List<ElfSymbol> list = new ArrayList<>();
        int maxIndex = symbols.stream().filter(s -> s.dynamic == dynamic).mapToInt(ElfSymbol::symbolIndex).max().orElse(-1);
        for (int i = 0; i <= maxIndex; i++) list.add(null);
        for (ElfSymbol s : symbols) if (s.dynamic == dynamic && s.symbolIndex < list.size()) list.set(s.symbolIndex, s);
        return list;
    }

    private int copyRelocationType(int machine) {
        return switch (machine) {
            case 3, 40, 183 -> 5;
            case 62 -> 1024;
            case 8 -> 20;
            case 20 -> 14;
            case 21 -> 21;
            default -> -1;
        };
    }

    private List<VersionDefinition> readVerdef(byte[] b, Section section, List<String> errors) {
        List<VersionDefinition> out = new ArrayList<>();
        Reader r = new Reader(b);
        for (int p = section.offset; p > 0 && p + 20 <= section.offset + section.size; ) {
            try {
                int version = r.u16leBe(p, true);
                int flags = r.u16leBe(p + 2, true);
                int ndx = r.u16leBe(p + 4, true);
                int cnt = r.u16leBe(p + 6, true);
                int hash = r.u32(p + 8, true);
                int aux = r.u32(p + 12, true);
                int next = r.u32(p + 16, true);
                List<VersionAlias> aliases = new ArrayList<>();
                String firstName = null;
                int ap = p + aux;
                for (int a = 0; a < cnt && ap > 0 && ap + 8 <= section.offset + section.size; a++) {
                    int nameOffset = r.u32(ap, true);
                    int nextAux = r.u32(ap + 4, true);
                    String alias = verName(b, section, nameOffset);
                    if (a == 0) firstName = alias; else aliases.add(new VersionAlias(alias, (flags & 1) != 0));
                    ap = nextAux == 0 ? 0 : ap + nextAux;
                }
                out.add(new VersionDefinition(ndx, firstName == null ? "VER_" + ndx : firstName, (flags & 1) != 0, aliases));
                if (next == 0) break;
                p += next;
            } catch (Exception ex) {
                errors.add("version definition 越界已跳过");
                break;
            }
        }
        return out;
    }

    private List<VersionRequirement> readVerneed(byte[] b, List<Section> sections, Section section, List<String> errors) {
        List<VersionRequirement> out = new ArrayList<>();
        Reader r = new Reader(b);
        Section strings = section.link > 0 && section.link < sections.size() ? sections.get(section.link) : null;
        for (int p = section.offset; p > 0 && p + 16 <= section.offset + section.size; ) {
            try {
                int fileOffset = r.u32(p + 4, true);
                int auxCount = r.u16leBe(p + 8, true);
                int aux = r.u32(p + 12, true);
                int next = r.u32(p + 16 <= section.offset + section.size ? p + 16 : p);
                String file = getString(b, strings, fileOffset);
                int ap = p + aux;
                for (int i = 0; i < auxCount && ap + 16 <= section.offset + section.size; i++) {
                    int flags = r.u32(ap, true);
                    int versionIndex = r.u16leBe(ap + 4, true);
                    int nameOffset = r.u32(ap + 8, true);
                    int nextAux = r.u32(ap + 12, true);
                    out.add(new VersionRequirement(file, versionIndex & 0x7fff, getString(b, strings, nameOffset)));
                    if (nextAux == 0) break;
                    ap += nextAux;
                }
                if (next == 0) break;
                p += next;
            } catch (Exception ex) {
                errors.add("version requirement 越界已跳过");
                break;
            }
        }
        return out;
    }

    private String verName(byte[] b, Section ver, int relativeToDynamicStrings) {
        return getString(b, ver, relativeToDynamicStrings);
    }

    private java.util.Optional<Section> findSection(List<Section> sections, int type) {
        return sections.stream().filter(s -> s.type == type).findFirst();
    }

    private static byte[] slice(byte[] source, int start, int end) {
        byte[] out = new byte[end - start];
        System.arraycopy(source, start, out, 0, out.length);
        return out;
    }

    private String ascii(byte[] b, int start, int end) {
        return new String(b, start, Math.max(0, end - start), java.nio.charset.StandardCharsets.US_ASCII);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String safeSha(byte[] bytes) {
        try { return sha256(bytes); } catch (Exception ex) { return ""; }
    }

    private static class Section {
        final int nameOffset;
        final int type;
        final long flags;
        final long address;
        final int offset;
        final long size;
        final int link;
        final int info;
        final int entsize;
        Section(int nameOffset, int type, long flags, long address, int offset, long size, int link, int info, int entsize) {
            this.nameOffset = nameOffset; this.type = type; this.flags = flags; this.address = address;
            this.offset = offset; this.size = size; this.link = link; this.info = info; this.entsize = entsize;
        }
    }

    private static class Reader {
        private final byte[] b;
        private boolean littleEndian = true;
        Reader(byte[] b) { this.b = b; }
        int u8(int p) { return b[p] & 255; }
        int u16leBe(int p, boolean le) {
            int x = b[p] & 255, y = b[p + 1] & 255;
            return le ? x | y << 8 : y | x << 8;
        }
        int u32(int p, boolean le) {
            int v = 0;
            for (int i = 0; i < 4; i++) v |= (b[p + i] & 255) << (8 * (le ? i : 3 - i));
            return v;
        }
        long longAt(int p, int elfClass, boolean le) {
            if (elfClass == 2) {
                long v = 0;
                for (int i = 0; i < 8; i++) v |= ((long)(b[p + i] & 255)) << (8 * (le ? i : 7 - i));
                return v;
            }
            return Integer.toUnsignedLong(u32(p, le));
        }
    }

    private static class ElfParseException extends RuntimeException {
        ElfParseException(String message) { super(message); }
    }
}
