package local.symbolcourt.elf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

import local.symbolcourt.elf.ElfModels.*;

@Component
public class ElfParser {
    private static final int SHT_STRTAB = 3, SHT_SYMTAB = 2, SHT_DYNSYM = 11;
    private static final int SHT_REL = 9, SHT_RELA = 4, SHT_GROUP = 17;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final int DT_NEEDED = 1, DT_SONAME = 14, DT_VERNEEDNUM = 0x6fffffff;
    private static final int DT_VERDEFNUM = 0x6ffffffc;
    private static final int R_X86_64_COPY = 5;
    private static final int R_AARCH64_COPY = 1024;
    private static final int VER_FLG_BASE = 1, VER_FLG_WEAK = 2, VER_NDX_HIDDEN = 0x8000;

    public LoadedFile parse(String name, byte[] bytes, String mediaType) {
        try {
            if (bytes.length >= 8 && bytes[0] == '!' && bytes[1] == '<' && bytes[2] == 'a' && bytes[3] == 'r' && bytes[4] == 'c' && bytes[5] == 'h' && bytes[6] == '>') {
                return parseArchive(name, bytes, mediaType);
            }
            ParsedBinary binary = parseElf(name, bytes);
            return new LoadedFile(name, bytes, mediaType, binary, List.of(), null);
        } catch (Exception ex) {
            ParsedBinary corrupt = new ParsedBinary(name, BinaryKind.CORRUPT, false, true, 0,
                    null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    safeMessage(ex));
            return new LoadedFile(name, bytes, mediaType, corrupt, List.of(), safeMessage(ex));
        }
    }

    private LoadedFile parseArchive(String name, byte[] bytes, String mediaType) throws IOException {
        List<ArchiveMember> members = new ArrayList<>();
        int pos = 8;
        long index = 0;
        Map<String, String> longNames = new HashMap<>();
        if (pos + 12 <= bytes.length && new String(bytes, pos, 12, StandardCharsets.US_ASCII).trim().equals("/")) {
            int size = Integer.parseInt(ascii(bytes, pos + 48, 10).trim());
            int start = pos + 60;
            require(bytes, start, size);
            int end = start + size;
            int lineStart = start;
            for (int p = start; p < end; p++) {
                if (bytes[p] == '\n') {
                    String line = new String(bytes, lineStart, p - lineStart, StandardCharsets.US_ASCII);
                    int slash = line.indexOf('/');
                    if (slash > 0) longNames.put(line.substring(slash + 1).trim(), line.substring(0, slash).trim());
                    lineStart = p + 1;
                }
            }
            pos += 60 + size + (size & 1);
        }
        while (pos + 60 <= bytes.length) {
            String rawName = ascii(bytes, pos, 16).trim();
            String memberName = rawName;
            if (rawName.startsWith("/") && rawName.length() > 1 && !rawName.startsWith("//")) {
                memberName = longNames.getOrDefault(rawName.substring(1), rawName);
            }
            if (memberName.endsWith("/")) memberName = memberName.substring(0, memberName.length() - 1);
            int size = Integer.parseInt(ascii(bytes, pos + 48, 10).trim());
            int dataStart = pos + 60;
            require(bytes, dataStart, size);
            byte[] data = new byte[size];
            System.arraycopy(bytes, dataStart, data, 0, size);
            ParsedBinary parsed = null;
            if (!rawName.equals("/") && !rawName.startsWith("/SYM64/") && !rawName.equals("/")) {
                try {
                    parsed = parseElf(memberName, data);
                } catch (Exception ex) {
                    parsed = new ParsedBinary(memberName, BinaryKind.CORRUPT, false, true, 0,
                            null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                            safeMessage(ex));
                }
                members.add(new ArchiveMember(memberName, index++, data, parsed));
            }
            pos = dataStart + size + (size & 1);
        }
        ParsedBinary archive = new ParsedBinary(name, BinaryKind.ARCHIVE, false, true, 0,
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        return new LoadedFile(name, bytes, mediaType, archive, members, null);
    }

    private ParsedBinary parseElf(String name, byte[] b) throws IOException {
        require(b, 0, 64);
        if (b[0] != 0x7f || b[1] != 'E' || b[2] != 'L' || b[3] != 'F') throw new IOException("not ELF");
        boolean elf64 = b[4] == 2;
        if (b[4] != 1 && b[4] != 2) throw new IOException("bad ELF class");
        boolean le = b[5] == 1;
        if (b[5] != 1 && b[5] != 2) throw new IOException("bad ELF data encoding");
        if (b[6] != 1) throw new IOException("bad ELF version");
        int type = u16(b, 16, le);
        int machine = u16(b, 18, le);
        long sectionHeaderOffset = elf64 ? u64(b, 40, le) : u32(b, 32, le);
        int sectionHeaderEntrySize = elf64 ? u16(b, 58, le) : u16(b, 46, le);
        int sectionCount = elf64 ? u16(b, 60, le) : u16(b, 48, le);
        int sectionStringIndex = elf64 ? u16(b, 62, le) : u16(b, 50, le);
        BinaryKind kind = switch (type) { case 1 -> BinaryKind.RELOCATABLE; case 2 -> BinaryKind.EXECUTABLE; case 3 -> BinaryKind.SHARED; case 4 -> BinaryKind.CORE; default -> BinaryKind.UNKNOWN; };
        if (sectionCount == 0) throw new IOException("missing section headers");
        if (Integer.compareUnsigned(sectionHeaderEntrySize, elf64 ? 64 : 40) < 0) throw new IOException("short section header");
        require(b, Math.toIntExact(sectionHeaderOffset), sectionCount * sectionHeaderEntrySize);

        int[][] raw = new int[sectionCount][10];
        long[][] raw64 = new long[sectionCount][];
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < sectionCount; i++) {
            int o = Math.toIntExact(sectionHeaderOffset + (long)i * sectionHeaderEntrySize);
            int shName = u32i(b, o, le);
            int shType = u32i(b, o + 4, le);
            long shFlags = elf64 ? u64(b, o + 8, le) : u32(b, o + 8, le);
            long shAddr = elf64 ? u64(b, o + 16, le) : u32(b, o + 12, le);
            long shOffset = elf64 ? u64(b, o + 24, le) : u32(b, o + 16, le);
            long shSize = elf64 ? u64(b, o + 32, le) : u32(b, o + 20, le);
            int shLink = u32i(b, o + (elf64 ? 40 : 24), le);
            int shInfo = u32i(b, o + (elf64 ? 44 : 28), le);
            long shAddralign = elf64 ? u64(b, o + 48, le) : u32(b, o + 32, le);
            long shEntsize = elf64 ? u64(b, o + 56, le) : u32(b, o + 36, le);
            raw[i] = new int[]{shName, shType, (int)shFlags, shLink, shInfo};
            raw64[i] = new long[]{shFlags, shAddr, shOffset, shSize, shAddralign, shEntsize};
            sections.add(new Section(i, "", shType, shFlags, shAddr, shOffset, shSize, shLink, shInfo,
                    shAddralign, shEntsize, -1, null));
        }
        if (sectionStringIndex == 0xffff || sectionStringIndex >= sectionCount) sectionStringIndex = 0;
        String[] sectionNames = readStringTable(b, sections.get(sectionStringIndex));
        List<Section> named = new ArrayList<>();
        Map<Integer, Integer> groupOwner = new HashMap<>();
        Map<Integer, String> groupSignatures = new HashMap<>();
        for (int i = 0; i < sectionCount; i++) {
            Section s = sections.get(i);
            if (s.type() != 8 && s.offset() > 0 && s.type() != 8) requireRange(b, s.offset(), s.size());
            named.add(new Section(i, at(sectionNames, raw[i][0]), s.type(),
                    s.flags(), s.address(), s.offset(), s.size(), s.link(), s.info(),
                    s.alignment(), s.entrySize(), -1, null));
        }
        for (int i = 0; i < sectionCount; i++) {
            Section s = named.get(i);
            if (s.type() == SHT_GROUP && s.link() < sectionCount) {
                String sig = symbolNameAt(b, named, s.link(), s.info());
                long count = s.entrySize() == 0 ? 0 : s.size() / s.entrySize();
                for (long j = 1; j < count; j++) {
                    int sec = u32i(b, Math.toIntExact(s.offset() + j * s.entrySize()), le);
                    if (sec > 0 && sec < sectionCount) groupSignatures.put(sec, sig);
                    if (sec > 0 && sec < sectionCount) groupOwner.put(sec, i);
                }
            }
        }
        List<Section> finalSections = new ArrayList<>();
        for (int i = 0; i < sectionCount; i++) {
            Section s = named.get(i);
            finalSections.add(new Section(i, s.name(), s.type(), s.flags(), s.address(),
                    s.offset(), s.size(), s.link(), s.info(), s.alignment(), s.entrySize(),
                    groupOwner.getOrDefault(i, -1),
                    groupSignatures.get(i)));
        }

        Section versionSymbols = findByType(finalSections, SHT_GNU_VERSYM).orElse(null);
        Map<Integer, VersionInfo> versionByIndex = readVersions(b, finalSections, versionSymbols, le, elf64);
        int[] symbolVersions = readVersionSymbols(b, versionSymbols, le);
        List<ElfSymbol> symbols = new ArrayList<>();
        List<Relocation> relocations = new ArrayList<>();
        for (Section section : finalSections) {
            if (section.type() == SHT_SYMTAB || section.type() == SHT_DYNSYM) {
                symbols.addAll(readSymbols(b, finalSections, section, versionByIndex, symbolVersions, le, elf64));
            }
        }
        for (Section section : finalSections) {
            if (section.type() == SHT_REL || section.type() == SHT_RELA) {
                relocations.addAll(readRelocations(b, finalSections, section, symbols, le, elf64, machine));
            }
        }
        DynamicInfo dyn = readDynamic(b, finalSections, le, elf64);
        List<VersionEdge> edges = new ArrayList<>();
        for (VersionInfo v : versionByIndex.values()) {
            if (v.required()) edges.add(new VersionEdge(v.dependencyFile(), v.name(), "need", "VER_NEEDED"));
            else if (v.defined()) edges.add(new VersionEdge(name, v.name(), "define", v.nonDefault() ? "non-default" : "default"));
        }
        return new ParsedBinary(name, kind, elf64, le, machine, dyn.soname, finalSections,
                symbols.stream().sorted((a,z)->Integer.compare(a.index(),z.index())).toList(),
                relocations, dyn.needed, versionByIndex.values().stream().toList(), edges, null);
    }

    private record DynamicInfo(List<String> needed, String soname) {}

    private DynamicInfo readDynamic(byte[] b, List<Section> sections, boolean le, boolean elf64) throws IOException {
        Optional<Section> dyn = sections.stream().filter(s -> s.name().equals(".dynamic")).findFirst();
        if (dyn.isEmpty()) return new DynamicInfo(List.of(), null);
        Section d = dyn.get();
        Section strings = d.link() >= 0 && d.link() < sections.size() ? sections.get(d.link()) : null;
        String[] strs = strings == null ? new String[0] : readStringTable(b, strings);
        List<String> needed = new ArrayList<>();
        String soname = null;
        int ent = elf64 ? 16 : 8;
        for (long o = d.offset(); o + ent <= d.offset() + d.size(); o += ent) {
            long tag = elf64 ? u64(b, Math.toIntExact(o), le) : u32(b, Math.toIntExact(o), le);
            long val = elf64 ? u64(b, Math.toIntExact(o + 8), le) : u32(b, Math.toIntExact(o + 4), le);
            if (tag == 0) break;
            if ((tag == DT_NEEDED || tag == DT_SONAME) && val < Integer.MAX_VALUE) {
                String value = at(strs, (int)val);
                if (tag == DT_NEEDED) needed.add(value); else soname = value;
            }
        }
        return new DynamicInfo(needed, soname);
    }

    private Optional<Section> findByType(List<Section> sections, int type) {
        return sections.stream().filter(s -> s.type() == type).findFirst();
    }

    private Map<Integer, VersionInfo> readVersions(byte[] b, List<Section> sections, Section versym,
                                                   boolean le, boolean elf64) throws IOException {
        Map<Integer, VersionInfo> result = new HashMap<>();
        result.put(0, new VersionInfo(0, null, false, false, false, null));
        result.put(1, new VersionInfo(1, "", true, false, false, null));
        Optional<Section> verdef = findByType(sections, SHT_GNU_VERDEF);
        Optional<Section> verneed = findByType(sections, SHT_GNU_VERNEED);
        if (verdef.isPresent()) {
            Section vd = verdef.get();
            Section strings = vd.link() >= 0 && vd.link() < sections.size() ? sections.get(vd.link()) : null;
            String[] strs = strings == null ? new String[0] : readStringTable(b, strings);
            int cursor = Math.toIntExact(vd.offset());
            while (cursor < vd.offset() + vd.size()) {
                int off = cursor;
                if (off + 20 > vd.offset() + vd.size()) break;
                int version = u16(b, off + 6, le);
                int flags = u16(b, off + 8, le);
                int nameOffset = u32i(b, off + 12, le);
                int aux = u32i(b, off + 16, le);
                int next = u32i(b, off + 4, le);
                String name = at(strs, nameOffset);
                result.put(version, new VersionInfo(version, name, true, false,
                        (flags & VER_FLG_BASE) != 0, null));
                if (aux != 0) {
                    int ax = off + aux;
                    requireRangeForSection(b, vd, ax, 8);
                    int vnaName = u32i(b, ax + 4, le);
                    String alias = at(strs, vnaName);
                    if (!alias.isBlank()) result.put(version, new VersionInfo(version, alias, true, false, false, null));
                }
                if (next == 0) break;
                if (next == 0) break;
                cursor += next;
            }
        }
        if (verneed.isPresent()) {
            Section vn = verneed.get();
            Section strings = vn.link() >= 0 && vn.link() < sections.size() ? sections.get(vn.link()) : null;
            String[] strs = strings == null ? new String[0] : readStringTable(b, strings);
            int off = Math.toIntExact(vn.offset());
            while (off + 16 <= vn.offset() + vn.size()) {
                int fileOffset = u32i(b, off + 8, le);
                int auxCount = u16(b, off + 10, le);
                int next = u32i(b, off + 4, le);
                int aux = u32i(b, off + 12, le);
                String file = at(strs, fileOffset);
                int ax = off + aux;
                for (int i = 0; i < auxCount && ax + 16 <= vn.offset() + vn.size(); i++) {
                    int flags = u16(b, ax + 2, le);
                    int other = u16(b, ax + 6, le);
                    int nameOffset = u32i(b, ax + 8, le);
                    int auxNext = u32i(b, ax + 12, le);
                    result.put(other, new VersionInfo(other, at(strs, nameOffset), false, true,
                            (flags & VER_FLG_WEAK) != 0, file));
                    if (auxNext == 0) break;
                    ax += auxNext;
                }
                if (next == 0) break;
                off += next;
            }
        }
        return result;
    }

    private int[] readVersionSymbols(byte[] b, Section section, boolean le) throws IOException {
        if (section == null || section.size() > Integer.MAX_VALUE - 2) return new int[0];
        int count = (int) (section.size() / 2);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = u16(b, Math.toIntExact(section.offset() + i * 2L), le);
        return result;
    }

    private List<ElfSymbol> readSymbols(byte[] b, List<Section> sections, Section symtab,
                                        Map<Integer, VersionInfo> versions, int[] symbolVersions,
                                        boolean le, boolean elf64) throws IOException {
        if (symtab.link() >= sections.size()) throw new IOException("symbol string table out of bounds");
        String[] strings = readStringTable(b, sections.get(symtab.link()));
        int ent = elf64 ? 24 : 16;
        int count = ent == 0 ? 0 : Math.toIntExact(symtab.size() / ent);
        List<ElfSymbol> symbols = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int off = Math.toIntExact(symtab.offset() + (long)i * ent);
            int nameOffset = u32i(b, off, le);
            int info, other, shndx;
            long value, size;
            if (elf64) {
                info = b[off + 4] & 255; other = b[off + 5] & 255; shndx = u16(b, off + 6, le);
                value = u64(b, off + 8, le); size = u64(b, off + 16, le);
            } else {
                value = u32(b, off + 4, le); size = u32(b, off + 8, le);
                info = b[off + 12] & 255; other = b[off + 13] & 255; shndx = u16(b, off + 14, le);
            }
            Binding binding = switch (info >>> 4) { case 0 -> Binding.LOCAL; case 1 -> Binding.GLOBAL; case 2 -> Binding.WEAK; default -> Binding.OTHER; };
            int rawType = info & 15;
            SymbolType type = switch (rawType) { case 0 -> SymbolType.NOTYPE; case 1 -> SymbolType.OBJECT; case 2 -> SymbolType.FUNC; case 3 -> SymbolType.SECTION; case 4 -> SymbolType.FILE; case 5 -> SymbolType.COMMON; case 6 -> SymbolType.TLS; default -> SymbolType.OTHER; };
            Visibility visibility = switch (other & 3) { case 0 -> Visibility.DEFAULT; case 1 -> Visibility.INTERNAL; case 2 -> Visibility.HIDDEN; case 3 -> Visibility.PROTECTED; default -> Visibility.OTHER; };
            boolean common = shndx == 0xfff2;
            boolean defined = shndx != 0 && shndx != 0xfff1;
            String sectionName = shndx > 0 && shndx < sections.size() ? sections.get(shndx).name() : "SHN_UNDEF";
            String comdat = shndx > 0 && shndx < sections.size() ? sections.get(shndx).groupSignature() : null;
            int rawVersion = i < symbolVersions.length ? symbolVersions[i] : 1;
            int versionIndex = rawVersion == 0 ? 1 : (rawVersion & ~VER_NDX_HIDDEN);
            VersionInfo version = versions.getOrDefault(versionIndex,
                    new VersionInfo(versionIndex, "v" + versionIndex, false, false, false, null));
            if ((rawVersion & VER_NDX_HIDDEN) != 0 && versionIndex > 1) version = new VersionInfo(versionIndex, version.name(), version.defined(), version.required(), true, version.dependencyFile());
            symbols.add(new ElfSymbol(i, at(strings, nameOffset), value, size, binding, type, visibility,
                    shndx, defined, common, sectionName, comdat, i == 0 ? null : version));
        }
        return symbols;
    }

    private List<Relocation> readRelocations(byte[] b, List<Section> sections, Section rel,
                                             List<ElfSymbol> symbols, boolean le, boolean elf64,
                                             int machine) throws IOException {
        if (rel.link() >= sections.size()) throw new IOException("relocation symbol table out of bounds");
        Section target = rel.info() >= 0 && rel.info() < sections.size() ? sections.get(rel.info()) : null;
        int ent = elf64 ? (rel.type() == SHT_RELA ? 24 : 16) : (rel.type() == SHT_RELA ? 12 : 8);
        int count = ent == 0 ? 0 : Math.toIntExact(rel.size() / ent);
        Map<Long, ElfSymbol> byIndex = new HashMap<>();
        symbols.stream().filter(s -> s.sectionIndex() < sections.size()).forEach(s -> byIndex.put((long)s.index(), s));
        List<Relocation> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int off = Math.toIntExact(rel.offset() + (long)i * ent);
            long rOffset, rInfo, addend = 0;
            if (elf64) {
                rOffset = u64(b, off, le); rInfo = u64(b, off + 8, le);
                if (rel.type() == SHT_RELA) addend = readSigned64(b, off + 16, le);
            } else {
                rOffset = u32(b, off, le); rInfo = u32(b, off + 4, le);
                if (rel.type() == SHT_RELA) addend = readSigned32(b, off + 8, le);
            }
            long symbolIndex = elf64 ? rInfo >>> 32 : rInfo >>> 8;
            int type = (int)(elf64 ? rInfo & 0xffffffff : rInfo & 255);
            ElfSymbol symbol = byIndex.get(symbolIndex);
            boolean copy = (machine == 62 && type == R_X86_64_COPY) || (machine == 183 && type == R_AARCH64_COPY);
            result.add(new Relocation(rel.info(), target == null ? "" : target.name(), rOffset, addend, type,
                    symbolIndex, symbol == null ? null : symbol.name(),
                    symbol == null || symbol.version() == null ? null : symbol.version().name(), copy));
        }
        return result;
    }

    private String symbolNameAt(byte[] b, List<Section> sections, int symtabIndex, int symbolIndex) throws IOException {
        if (symtabIndex >= sections.size() || symbolIndex == 0) return null;
        Section symtab = sections.get(symtabIndex);
        if (symtab.link() >= sections.size()) return null;
        String[] strings = readStringTable(b, sections.get(symtab.link()));
        int ent = 24;
        int off = Math.toIntExact(symtab.offset() + (long)symbolIndex * ent);
        requireRangeForSection(b, symtab, off, ent);
        return at(strings, u32i(b, off, true));
    }

    private String[] readStringTable(byte[] b, Section section) throws IOException {
        if (section == null) return new String[0];
        if (section.size() > Integer.MAX_VALUE - 1) throw new IOException("string table too large");
        int size = (int)section.size();
        requireRange(b, Math.toIntExact(section.offset()), size);
        String[] result = new String[size];
        int start = 0;
        for (int i = 0; i < size; i++) {
            if (b[Math.toIntExact(section.offset()) + i] == 0) {
                result[start] = new String(b, Math.toIntExact(section.offset()) + start, i - start, StandardCharsets.UTF_8);
                start = i + 1;
            }
        }
        return result;
    }

    private String at(String[] strings, int offset) {
        if (offset < 0 || offset >= strings.length || strings[offset] == null) return "";
        return strings[offset];
    }

    private void requireRangeForSection(byte[] b, Section section, int absoluteOffset, int length) throws IOException {
        if (absoluteOffset < section.offset() || absoluteOffset + (long)length > section.offset() + section.size()) throw new IOException("section index out of bounds");
        require(b, absoluteOffset, length);
    }

    private void requireRange(byte[] b, long offset, long size) throws IOException {
        require(b, Math.toIntExact(offset), Math.toIntExact(size));
    }

    private void require(byte[] b, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || (long)offset + length > b.length) throw new IOException("ELF index out of bounds");
    }

    private String ascii(byte[] b, int offset, int length) {
        return new String(b, offset, length, StandardCharsets.US_ASCII);
    }

    private String safeMessage(Exception ex) {
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    private int u16(byte[] b, int o, boolean le) { return le ? (b[o]&255) | ((b[o+1]&255)<<8) : ((b[o]&255)<<8) | (b[o+1]&255); }
    private int u32i(byte[] b, int o, boolean le) { return (int) u32(b, o, le); }
    private long u32(byte[] b, int o, boolean le) {
        long v = 0;
        for (int i=0;i<4;i++) v |= (b[o+i]&255L) << (8 * (le ? i : 3-i));
        return v;
    }
    private long u64(byte[] b, int o, boolean le) {
        long v = 0;
        for (int i=0;i<8;i++) v |= (b[o+i]&255L) << (8 * (le ? i : 7-i));
        return v;
    }
    private long readSigned32(byte[] b, int o, boolean le) { long v = u32(b,o,le); return (v & 0x80000000L)==0 ? v : v - 0x100000000L; }
    private long readSigned64(byte[] b, int o, boolean le) { return u64(b,o,le); }
}
