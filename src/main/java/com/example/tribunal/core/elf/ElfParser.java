package com.example.tribunal.core.elf;

import com.example.tribunal.core.elf.ElfModels.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElfParser {
    public static class ParseException extends IOException {
        public ParseException(String message) { super(message); }
        public ParseException(String message, Throwable cause) { super(message, cause); }
    }

    private static final int SHT_STRTAB = 3, SHT_SYMTAB = 2, SHT_DYNSYM = 11, SHT_GROUP = 17;
    private static final int SHT_REL = 9, SHT_RELA = 4, SHT_DYNAMIC = 6;
    private static final int SHF_COMPRESSED = 0x800;
    private static final int DT_NULL = 0, DT_NEEDED = 1, DT_SONAME = 14, DT_VERDEF = 0x6ffffffc;
    private static final int DT_VERDEFNUM = 0x6ffffffd, DT_VERNEED = 0x6ffffffe, DT_VERNEEDNUM = 0x6fffffff;
    private static final int VER_NDX_LOCAL = 0, VER_NDX_GLOBAL = 1;
    private static final int VER_FLG_BASE = 1, VER_FLG_WEAK = 2;
    private static final int GRP_COMDAT = 1;
    private static final short SHN_UNDEF = 0, SHN_ABS = (short) 0xfff1, SHN_COMMON = (short) 0xfff2, SHN_XINDEX = (short) 0xffff;

    private final byte[] data;
    private final ByteBuffer buf;
    private final boolean elf64;
    private final ByteOrder order;
    private final int machine;
    private final List<Section> sections = new ArrayList<>();
    private final List<ElfSymbol> rawSymbols = new ArrayList<>();
    private final List<Relocation> relocations = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final List<String> needed = new ArrayList<>();
    private String soname = "";
    private VersionInfo versions = new VersionInfo(List.of(), List.of(), List.of());
    private final Map<Integer, Integer> linkedStringTable = new HashMap<>();

    public ElfParser(byte[] data) throws ParseException {
        this.data = data;
        if (data == null || data.length < 64 || data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ParseException("not an ELF file");
        }
        if (data[4] != 1 && data[4] != 2) throw new ParseException("unsupported ELF class");
        if (data[5] != 1 && data[5] != 2) throw new ParseException("unsupported ELF endianness");
        elf64 = data[4] == 2;
        if (!elf64 && data.length < 52) throw new ParseException("truncated ELF header");
        order = data[5] == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        buf = ByteBuffer.wrap(data).order(order);
        machine = u16(18);
    }

    public static ElfObject parse(String name, byte[] bytes) throws ParseException {
        return new ElfParser(bytes).read(name, bytes);
    }

    private ElfObject read(String name, byte[] bytes) throws ParseException {
        int type = u16(16);
        long sectionOffset = elf64 ? u64(40) : u32(32);
        int sectionHeaderSize = u16(elf64 ? 58 : 46);
        int sectionCount = u16(elf64 ? 60 : 48);
        int sectionStringIndex = u16(elf64 ? 62 : 50);
        if (sectionCount == 0 || sectionOffset == 0) throw new ParseException("ELF section table is absent");
        if (sectionHeaderSize < (elf64 ? 64 : 40)) throw new ParseException("invalid section header size");
        if (Integer.toUnsignedLong((int) sectionOffset) + Integer.toUnsignedLong(sectionCount) * Integer.toUnsignedLong(sectionHeaderSize) > data.length) {
            throw new ParseException("section headers exceed file length");
        }
        int[][] raw = readSectionHeaders(sectionOffset, Math.max(sectionCount, 1), sectionHeaderSize);
        if (sectionCount == 0) {
            sectionCount = (int) u64or32(raw[0], 7);
            raw = readSectionHeaders(sectionOffset, sectionCount, sectionHeaderSize);
        }
        if (sectionStringIndex == 0xffff) sectionStringIndex = raw[0][8];
        if (sectionStringIndex < 0 || sectionStringIndex >= sectionCount) throw new ParseException("invalid section name index");
        byte[] names = sectionBytes(raw[sectionStringIndex]);
        for (int i = 0; i < sectionCount; i++) {
            int[] s = raw[i];
            sections.add(new Section(i, cstring(names, s[0]), s[1], u64or32(s, 2), u64or32(s, 3),
                    u64or32(s, 4), u64or32(s, 5), s[7], s[8], u64or32(s, 9), u64or32(s, 10)));
        }
        for (Section section : sections) {
            if (section.type() == SHT_DYNSYM || (section.type() == SHT_SYMTAB && u16(16) != 3)) readSymbols(section);
            if (section.type() == SHT_GROUP) readGroup(section);
            if (section.type() == SHT_REL || section.type() == SHT_RELA) readRelocations(section);
            if (section.type() == SHT_DYNAMIC) readDynamic(section);
        }
        applyVersions();
        return new ElfObject(name, bytes, elf64 ? ElfClass.ELF64 : ElfClass.ELF32,
                switch (type) { case 1 -> ElfType.RELOCATABLE; case 2 -> ElfType.EXECUTABLE;
                    case 3 -> ElfType.SHARED; case 4 -> ElfType.CORE; default -> ElfType.OTHER; },
                order == ByteOrder.LITTLE_ENDIAN, machine, List.copyOf(sections), List.copyOf(rawSymbols),
                List.copyOf(relocations), List.copyOf(groups), List.copyOf(needed), soname, versions);
    }

    private int[][] readSectionHeaders(long offset, int count, int size) throws ParseException {
        int[][] raw = new int[count][11];
        for (int i = 0; i < count; i++) {
            long p = offset + (long) i * size;
            if (elf64) {
                raw[i][0] = (int) u32(p);
                raw[i][1] = (int) u32(p + 4);
                raw[i][2] = (int) u64(p + 8);
                raw[i][3] = (int) u64(p + 16);
                raw[i][4] = (int) u64(p + 24);
                raw[i][5] = (int) u64(p + 32);
                raw[i][6] = (int) u64(p + 40);
                raw[i][7] = (int) u32(p + 40);
                raw[i][8] = (int) u32(p + 44);
                raw[i][9] = (int) u64(p + 48);
                raw[i][10] = (int) u64(p + 56);
            } else {
                raw[i][0] = (int) u32(p);
                raw[i][1] = (int) u32(p + 4);
                raw[i][2] = (int) u32(p + 8);
                raw[i][3] = (int) u32(p + 12);
                raw[i][4] = (int) u32(p + 16);
                raw[i][5] = (int) u32(p + 20);
                raw[i][6] = (int) u32(p + 24);
                raw[i][7] = (int) u32(p + 28);
                raw[i][8] = (int) u32(p + 32);
                raw[i][9] = (int) u32(p + 36);
                raw[i][10] = (int) u32(p + 40);
            }
        }
        return raw;
    }

    private byte[] sectionBytes(Section section) throws ParseException {
        if (section.size() == 0) return new byte[0];
        long end = section.offset() + section.size();
        if (section.offset() < 0 || end < section.offset() || end > data.length) {
            throw new ParseException("section " + section.name() + " exceeds file");
        }
        byte[] out = new byte[(int) section.size()];
        System.arraycopy(data, (int) section.offset(), out, 0, out.length);
        return out;
    }

    private byte[] sectionBytes(int[] raw) throws ParseException {
        long offset = u64or32(raw, 6), size = u64or32(raw, 7);
        if (size == 0) return new byte[0];
        if (offset < 0 || offset + size < offset || offset + size > data.length) {
            throw new ParseException("section exceeds file length");
        }
        byte[] out = new byte[(int) size];
        System.arraycopy(data, (int) offset, out, 0, out.length);
        return out;
    }

    private long u64or32(int[] section, int field) {
        return Integer.toUnsignedLong(section[field]);
    }

    private String cstring(byte[] table, long offset) throws ParseException {
        if (table.length == 0) return "";
        if (offset < 0 || offset > table.length) throw new ParseException("string offset outside table");
        int start = (int) offset;
        int end = start;
        while (end < table.length && table[end] != 0) end++;
        if (end >= table.length) throw new ParseException("unterminated string");
        return new String(table, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }

    private int u16(long p) throws ParseException {
        check(p, 2);
        return Short.toUnsignedInt(buf.getShort((int) p));
    }

    private long u32(long p) throws ParseException {
        check(p, 4);
        return Integer.toUnsignedLong(buf.getInt((int) p));
    }

    private long u64(long p) throws ParseException {
        check(p, 8);
        return buf.getLong((int) p);
    }

    private void check(long p, int length) throws ParseException {
        if (p < 0 || p + length < p || p + length > data.length) throw new ParseException("truncated ELF structure");
    }

    private void readSymbols(Section symtab) throws ParseException {
        if (symtab.link() < 0 || symtab.link() >= sections.size()) throw new ParseException("symbol string link invalid");
        byte[] strings = sectionBytes(sections.get(symtab.link()));
        int entrySize = (int) (symtab.entrySize() > 0 ? symtab.entrySize() : elf64 ? 24 : 16);
        int expected = elf64 ? 24 : 16;
        if (entrySize < expected) throw new ParseException("symbol entry too small");
        long count = symtab.size() / entrySize;
        if (count > 8_000_000) throw new ParseException("too many symbols");
        int startIndex = rawSymbols.size();
        for (long i = 0; i < count; i++) {
            long p = symtab.offset() + i * entrySize;
            int nameOff;
            long value, size;
            byte info, other;
            short shndx;
            if (elf64) {
                nameOff = (int) u32(p);
                info = data[(int) p + 4];
                other = data[(int) p + 5];
                shndx = (short) u16(p + 6);
                value = u64(p + 8);
                size = u64(p + 16);
            } else {
                nameOff = (int) u32(p);
                value = u32(p + 4);
                size = u32(p + 8);
                info = data[(int) p + 12];
                other = data[(int) p + 13];
                shndx = (short) u16(p + 14);
            }
            if (shndx == SHN_XINDEX) {
                long sectionEntry = symtab.size() / entrySize;
                Section xindex = sections.stream().filter(s -> s.type() == 0x18 && s.link() == symtab.index()).findFirst()
                        .orElseThrow(() -> new ParseException("missing extended section index"));
                if (i * 4 + 4 > xindex.size()) throw new ParseException("extended section index truncated");
                shndx = (short) u32(xindex.offset() + i * 4);
            }
            int bindValue = (info & 0xff) >> 4;
            int typeValue = info & 15;
            int visibilityValue = other & 7;
            Binding binding = switch (bindValue) { case 0 -> Binding.LOCAL; case 1 -> Binding.GLOBAL;
                case 2 -> Binding.WEAK; default -> Binding.OTHER; };
            SymbolKind kind = shndx == SHN_COMMON ? SymbolKind.COMMON : switch (typeValue) {
                case 0 -> SymbolKind.NOTYPE; case 1 -> SymbolKind.OBJECT; case 2 -> SymbolKind.FUNC;
                case 3 -> SymbolKind.SECTION; case 4 -> SymbolKind.FILE; case 6 -> SymbolKind.TLS;
                case 10 -> SymbolKind.IFUNC; default -> SymbolKind.OTHER; };
            Visibility visibility = switch (visibilityValue) { case 0 -> Visibility.DEFAULT;
                case 1 -> Visibility.INTERNAL; case 2 -> Visibility.HIDDEN; case 3 -> Visibility.PROTECTED;
                default -> Visibility.OTHER; };
            int sectionIndex = Short.toUnsignedInt(shndx);
            String sectionName = sectionIndex > 0 && sectionIndex < sections.size() ? sections.get(sectionIndex).name()
                    : switch (shndx) { case SHN_UNDEF -> "UND"; case SHN_ABS -> "ABS"; case SHN_COMMON -> "COMMON";
                        default -> "SPECIAL"; };
            rawSymbols.add(new ElfSymbol(startIndex + (int) i, cstring(strings, nameOff), value, size, binding, kind,
                    visibility, sectionIndex, sectionName, (short) typeValue, "", true, false));
        }
        linkedStringTable.put(symtab.index(), symtab.link());
    }

    private void readRelocations(Section rel) throws ParseException {
        int symbolTable = rel.link();
        if (symbolTable < 0 || symbolTable >= sections.size()) throw new ParseException("relocation symbol link invalid");
        long symOffset = sections.get(symbolTable).offset();
        long symEntry = sections.get(symbolTable).entrySize() != 0 ? sections.get(symbolTable).entrySize() : elf64 ? 24 : 16;
        int entrySize = rel.type() == SHT_RELA ? (elf64 ? 24 : 12) : (elf64 ? 16 : 8);
        if (rel.entrySize() > 0 && rel.entrySize() < entrySize) throw new ParseException("relocation entry too small");
        long count = rel.size() / entrySize;
        for (long i = 0; i < count; i++) {
            long p = rel.offset() + i * entrySize;
            long offset = elf64 ? u64(p) : u32(p);
            long info = elf64 ? u64(p + 8) : u32(p + 4);
            long addend = rel.type() == SHT_RELA ? (elf64 ? u64(p + 16) : (int) u32(p + 8)) : 0;
            int type = elf64 ? (int) info : (int) (info & 255);
            int symbolIndex = elf64 ? (int) (info >>> 32) : (int) (info >>> 8);
            long symbolCount = symEntry == 0 ? 0 : (sections.get(symbolTable).size() / symEntry);
            if (symbolIndex < 0 || symbolIndex >= symbolCount) throw new ParseException("relocation symbol index outside table");
            long sp = symOffset + (long) symbolIndex * symEntry;
            int nameOff = (int) (elf64 ? u32(sp) : u32(sp));
            int strTab = sections.get(symbolTable).link();
            String symbolName = cstring(sectionBytes(sections.get(strTab)), nameOff);
            relocations.add(new Relocation(relocations.size(), rel.info(), rel.name(), offset, type, symbolIndex,
                    addend, symbolName, isCopyType(type), isPcrelType(type)));
        }
    }

    private boolean isCopyType(int type) {
        return switch (machine) {
            case 62 -> type == 5;
            case 3, 40, 20 -> type == 5;
            case 183 -> type == 1024;
            case 243 -> type == 22;
            default -> false;
        };
    }

    private boolean isPcrelType(int type) {
        return switch (machine) {
            case 62 -> type == 2 || type == 4 || type == 7 || type == 8 || type == 9;
            case 3, 40, 20 -> type == 2 || type == 6 || type == 7 || type == 21;
            default -> false;
        };
    }

    private void readGroup(Section group) throws ParseException {
        if (group.link() < 0 || group.link() >= sections.size()) throw new ParseException("group symbol link invalid");
        if (group.size() < 4) throw new ParseException("group section truncated");
        int flags = (int) u32(group.offset());
        long count = (group.size() - 4) / 4;
        long symbolEntry = sections.get(group.link()).entrySize() != 0 ? sections.get(group.link()).entrySize() : elf64 ? 24 : 16;
        int nameOffset = (int) u32(sections.get(group.link()).offset() + (long) group.info() * symbolEntry);
        String signature = cstring(sectionBytes(sections.get(sections.get(group.link()).link())), nameOffset);
        List<Integer> members = new ArrayList<>();
        for (long i = 0; i < count; i++) members.add((int) u32(group.offset() + 4 + i * 4));
        groups.add(new Group(group.index(), signature, (flags & GRP_COMDAT) != 0, List.copyOf(members)));
    }

    private void readDynamic(Section dynamic) throws ParseException {
        int entrySize = elf64 ? 16 : 8;
        Map<Long, Long> values = new HashMap<>();
        for (long p = dynamic.offset(); p + entrySize <= dynamic.offset() + dynamic.size(); p += entrySize) {
            long tag = elf64 ? u64(p) : u32(p);
            long value = elf64 ? u64(p + 8) : u32(p + 4);
            if (tag == DT_NULL) break;
            if (tag == DT_NEEDED || tag == DT_SONAME) {
                Section linked = sections.get(dynamic.link());
                String text = cstring(sectionBytes(linked), value);
                if (tag == DT_NEEDED) needed.add(text);
                else soname = text;
            }
            values.put(tag, value);
        }
        if (values.containsKey((long) DT_VERDEF) || values.containsKey((long) DT_VERNEED)
                || values.containsKey((long) DT_VERDEFNUM) || values.containsKey((long) DT_VERNEEDNUM)) {
            versions = readVersions(dynamic.link(), values);
        }
    }

    private VersionInfo readVersions(int dynamicStringLink, Map<Long, Long> values) throws ParseException {
        byte[] strings = sectionBytes(sections.get(dynamicStringLink));
        List<VersionDefinition> definitions = new ArrayList<>();
        List<VersionRequirement> requirements = new ArrayList<>();
        long defCount = values.getOrDefault((long) DT_VERDEFNUM, 0L);
        long needCount = values.getOrDefault((long) DT_VERNEEDNUM, 0L);
        long defOffset = values.getOrDefault((long) DT_VERDEF, 0L);
        for (long i = 0; i < defCount; i++) {
            long p = defOffset;
            if (u16(p) != 1) throw new ParseException("unsupported Verdef version");
            int flags = u16(p + 2);
            int index = u16(p + 4);
            int auxCount = u16(p + 6);
            long auxOffset = u32(p + 8);
            long nextOffset = u32(p + 12);
            long ap = p + auxOffset;
            List<String> aliases = new ArrayList<>();
            for (int a = 0; a < auxCount; a++) {
                String alias = cstring(strings, u32(ap + 4));
                aliases.add(alias);
                long nextAux = u32(ap + 8);
                if (nextAux == 0 && a + 1 < auxCount) throw new ParseException("missing version alias");
                ap += nextAux;
            }
            if (aliases.isEmpty()) throw new ParseException("version definition has no name");
            definitions.add(new VersionDefinition(aliases.get(0), index, flags, List.copyOf(aliases)));
            if (nextOffset == 0 && i + 1 < defCount) throw new ParseException("missing next version definition");
            defOffset = p + nextOffset;
        }
        long needOffset = values.getOrDefault((long) DT_VERNEED, 0L);
        for (long i = 0; i < needCount; i++) {
            long p = needOffset;
            if (u16(p) != 1) throw new ParseException("unsupported Verneed version");
            int auxCount = u16(p + 2);
            long fileOffset = u32(p + 4);
            long auxOffset = u32(p + 8);
            long nextOffset = u32(p + 12);
            String fileName = cstring(strings, fileOffset);
            List<RequestedVersion> requested = new ArrayList<>();
            long ap = p + auxOffset;
            for (int a = 0; a < auxCount; a++) {
                int hash = (int) u32(ap);
                int flags = u16(ap + 4);
                int versionIndex = u16(ap + 6);
                String versionName = cstring(strings, u32(ap + 8));
                long nextAux = u32(ap + 12);
                requested.add(new RequestedVersion(versionName, versionIndex, flags == VER_FLG_WEAK ? List.of(versionName) : List.of(versionName)));
                if (nextAux == 0 && a + 1 < auxCount) throw new ParseException("missing next requested version");
                ap += nextAux;
            }
            requirements.add(new VersionRequirement(fileName, List.copyOf(requested)));
            if (nextOffset == 0 && i + 1 < needCount) throw new ParseException("missing next version requirement");
            needOffset = p + nextOffset;
        }
        List<Short> symbolVersions = new ArrayList<>();
        Section gnuVersion = sections.stream().filter(section -> section.type() == 0x6fffffff).findFirst().orElse(null);
        if (gnuVersion != null) {
            for (long p = gnuVersion.offset(); p + 2 <= gnuVersion.offset() + gnuVersion.size(); p += 2) {
                symbolVersions.add((short) u16(p));
            }
        }
        return new VersionInfo(List.copyOf(definitions), List.copyOf(requirements), List.copyOf(symbolVersions));
    }

    private void applyVersions() throws ParseException {
        if (versions.symbolVersions().isEmpty()) return;
        List<ElfSymbol> updated = new ArrayList<>();
        int[] current = {0};
        for (ElfSymbol symbol : rawSymbols) {
            short rawVersion = symbol.index() < versions.symbolVersions().size() ? versions.symbolVersions().get(symbol.index()) : 1;
            int versionIndex = Short.toUnsignedInt(rawVersion);
            String versionName = "";
            boolean defined = false;
            boolean isDefault = true;
            if (versionIndex > 1) {
                var definition = versions.definitions().stream().filter(v -> v.hash() == versionIndex).findFirst();
                if (definition.isPresent()) {
                    versionName = definition.get().name();
                    defined = true;
                isDefault = (definition.get().flags() & VER_FLG_WEAK) == 0;
                } else {
                    var requested = versions.requirements().stream()
                            .flatMap(r -> r.versions().stream().filter(v -> v.hash() == versionIndex))
                            .findFirst();
                    if (requested.isPresent()) {
                        versionName = requested.get().name();
                        isDefault = true;
                    } else {
                        throw new ParseException("symbol version index " + versionIndex + " has no definition or need");
                    }
                }
            }
            updated.add(new ElfSymbol(symbol.index(), symbol.name(), symbol.value(), symbol.size(), symbol.binding(),
                    symbol.kind(), symbol.visibility(), symbol.sectionIndex(), symbol.sectionName(), symbol.sectionType(),
                    versionName, isDefault, defined));
        }
        rawSymbols.clear();
        rawSymbols.addAll(updated);
    }
}
