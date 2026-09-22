package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Self-contained ELF parser (no system linker/library involvement).
 * Supports ELF32/ELF64, both endians; parses section headers, static and
 * dynamic symbol tables, GNU symbol versioning, section groups, dynamic
 * dependencies and relocations.
 *
 * <p>Every read goes through {@link ByteReader}, which enforces length and
 * index boundaries; any inconsistency raises {@link ElfParseException}.
 */
public final class ElfParser {

    private static final int EHDR_IDENT_SIZE = 16;
    private static final int ELF64_EHDR_SIZE = 64;
    private static final int ELF32_EHDR_SIZE = 52;
    private static final int ELF64_SHDR_SIZE = 64;
    private static final int ELF32_SHDR_SIZE = 40;
    private static final int ELF64_SYM_SIZE = 24;
    private static final int ELF32_SYM_SIZE = 16;
    private static final int ELF64_REL_SIZE = 16;
    private static final int ELF64_RELA_SIZE = 24;
    private static final int ELF32_REL_SIZE = 8;
    private static final int ELF32_RELA_SIZE = 12;

    public ElfFile parse(byte[] data) {
        return parse(data, "input");
    }

    public ElfFile parse(byte[] data, String sourceName) {
        if (data == null || data.length < EHDR_IDENT_SIZE) {
            throw new ElfParseException(sourceName + ": file too small for ELF header");
        }
        for (int i = 0; i < 4; i++) {
            if ((data[i] & 0xff) != (ElfConstants.ELF_MAGIC[i] & 0xff)) {
                throw new ElfParseException(sourceName + ": bad ELF magic");
            }
        }
        int elfClass = data[ElfConstants.EI_CLASS] & 0xff;
        if (elfClass != ElfConstants.ELFCLASS32 && elfClass != ElfConstants.ELFCLASS64) {
            throw new ElfParseException(sourceName + ": unsupported ELF class " + elfClass);
        }
        int endian = data[ElfConstants.EI_DATA] & 0xff;
        if (endian != ElfConstants.ELFDATA2LSB && endian != ElfConstants.ELFDATA2MSB) {
            throw new ElfParseException(sourceName + ": unsupported ELF data encoding " + endian);
        }
        boolean elf64 = elfClass == ElfConstants.ELFCLASS64;
        ByteReader r = new ByteReader(data, endian == ElfConstants.ELFDATA2LSB);
        int minHdr = elf64 ? ELF64_EHDR_SIZE : ELF32_EHDR_SIZE;
        if (data.length < minHdr) {
            throw new ElfParseException(sourceName + ": truncated ELF header");
        }

        int type = r.u16(16);
        int machine = r.u16(18);
        long shoff = elf64 ? r.u64(40) : r.u32(32);
        int shentsize = elf64 ? r.u16(58) : r.u16(46);
        int shnum = elf64 ? r.u16(60) : r.u16(48);
        int shstrndx = elf64 ? r.u16(62) : r.u16(50);

        int expectedShent = elf64 ? ELF64_SHDR_SIZE : ELF32_SHDR_SIZE;
        if (shentsize < expectedShent) {
            throw new ElfParseException(sourceName + ": unsupported e_shentsize " + shentsize);
        }

        ElfFile elf = new ElfFile(elf64, r.littleEndian(), machine, type, null);

        if (shoff == 0 || shnum == 0) {
            parseDynamicViaHeaderlessSections(r, elf, elf64, sourceName);
            return elf;
        }

        // Extended numbering: if shnum == 0 the count lives in section 0 sh_size.
        if (shnum == 0) {
            long s0off = shoff;
            long sizeField = elf64 ? r.u64(s0off + 32) : r.u32(s0off + 20);
            shnum = (int) sizeField;
        }
        long tableBytes = (long) shnum * shentsize;
        r.require(shoff, tableBytes);
        if (shstrndx == ElfConstants.SHN_XINDEX) {
            // extended shstrndx lives in section 0 sh_link
            long s0off = shoff;
            shstrndx = elf64 ? (int) r.u32(s0off + 40) : (int) r.u32(s0off + 24);
        }
        if (shstrndx < 0 || shstrndx >= shnum) {
            throw new ElfParseException(sourceName + ": invalid shstrndx " + shstrndx);
        }

        List<RawSection> raw = new ArrayList<>(shnum);
        for (int i = 0; i < shnum; i++) {
            raw.add(readRawSection(r, elf64, shoff + (long) i * shentsize));
        }
        String[] sectionNames = readStringTable(r, raw.get(shstrndx));

        for (int i = 0; i < shnum; i++) {
            RawSection rs = raw.get(i);
            String name = safeString(sectionNames, rs.nameOff);
            elf.getSections().add(new ElfSection(name, i, rs.type, rs.flags, rs.addr,
                    rs.offset, rs.size, rs.link, rs.info, rs.addralign, rs.entsize));
            if (rs.type != ElfConstants.SHT_NOBITS && rs.size > 0) {
                r.require(rs.offset, rs.size);
            }
        }

        parseContents(r, elf, raw, sectionNames, machine, elf64, sourceName);
        return elf;
    }

    private void parseDynamicViaHeaderlessSections(ByteReader r, ElfFile elf, boolean elf64, String src) {
        // Stripped section headers: we cannot meaningfully resolve symbols.
        elf.addWarning("no-sections", src + ": no section header table; symbol analysis unavailable");
    }

    private static final class RawSection {
        int nameOff;
        int type;
        long flags;
        long addr;
        long offset;
        long size;
        int link;
        int info;
        long addralign;
        long entsize;
    }

    private RawSection readRawSection(ByteReader r, boolean elf64, long off) {
        RawSection rs = new RawSection();
        if (elf64) {
            rs.nameOff = (int) r.u32(off);
            rs.type = (int) r.u32(off + 4);
            rs.flags = r.u64(off + 8);
            rs.addr = r.u64(off + 16);
            rs.offset = r.u64(off + 24);
            rs.size = r.u64(off + 32);
            rs.link = (int) r.u32(off + 40);
            rs.info = (int) r.u32(off + 44);
            rs.addralign = r.u64(off + 48);
            rs.entsize = r.u64(off + 56);
        } else {
            rs.nameOff = (int) r.u32(off);
            rs.type = (int) r.u32(off + 4);
            rs.flags = r.u32(off + 8);
            rs.addr = r.u32(off + 12);
            rs.offset = r.u32(off + 16);
            rs.size = r.u32(off + 20);
            rs.link = (int) r.u32(off + 24);
            rs.info = (int) r.u32(off + 28);
            rs.addralign = r.u32(off + 32);
            rs.entsize = r.u32(off + 36);
        }
        return rs;
    }

    private String[] readStringTable(ByteReader r, RawSection strtab) {
        if (strtab == null || strtab.type == ElfConstants.SHT_NOBITS) {
            return new String[0];
        }
        r.require(strtab.offset, strtab.size);
        long size = strtab.size;
        String[] out = new String[(int) size];
        int start = (int) strtab.offset;
        int i = 0;
        while (i < size) {
            int begin = i;
            while (i < size && r.u8(start + i) != 0) {
                i++;
            }
            if (i > begin) {
                byte[] b = r.slice(start + begin, i - begin);
                out[begin] = new String(b, java.nio.charset.StandardCharsets.UTF_8);
            }
            i++;
        }
        return out;
    }

    private String safeString(String[] table, long off) {
        if (off < 0 || off >= table.length || table[(int) off] == null) {
            return "";
        }
        return table[(int) off];
    }

    private void parseContents(ByteReader r, ElfFile elf, List<RawSection> raw, String[] shstr,
                               int machine, boolean elf64, String sourceName) {
        parseVersionSections(r, elf, raw, sourceName);
        parseGroups(r, elf, raw, elf64, sourceName);
        parseSymbolTables(r, elf, raw, sourceName);
        parseRelocations(r, elf, raw, machine, sourceName);
        parseDynamic(r, elf, raw, sourceName);
    }

    // ------------------------------------------------------------------
    // Version definitions / requirements
    // ------------------------------------------------------------------

    private void parseVersionSections(ByteReader r, ElfFile elf, List<RawSection> raw, String src) {
        for (RawSection rs : raw) {
            if (rs.type == ElfConstants.SHT_GNU_VERDEF) {
                parseVerdef(r, elf, raw, rs, src);
            } else if (rs.type == ElfConstants.SHT_GNU_VERNEED) {
                parseVerneed(r, elf, raw, rs, src);
            }
        }
    }

    private String[] linkedStringTable(ByteReader r, List<RawSection> raw, RawSection rs, String src) {
        if (rs.link <= 0 || rs.link >= raw.size()) {
            throw new ElfParseException(src + ": invalid sh_link " + rs.link);
        }
        return readStringTable(r, raw.get(rs.link));
    }

    private void parseVerdef(ByteReader r, ElfFile elf, List<RawSection> raw, RawSection rs, String src) {
        String[] str = linkedStringTable(r, raw, rs, src);
        r.require(rs.offset, rs.size);
        long cursor = rs.offset;
        long end = rs.offset + rs.size;
        while (cursor < end) {
            long base = cursor;
            int vdVersion = (int) r.u16(base);
            int vdFlags = (int) r.u16(base + 2);
            int vdNdx = (int) r.u16(base + 4);
            int vdCnt = (int) r.u16(base + 6);
            int vdHash = (int) r.u32(base + 8);
            int vdAux = (int) r.u32(base + 12);
            int vdNext = (int) r.u32(base + 16);
            if (vdVersion != 1) {
                throw new ElfParseException(src + ": bad verdef version " + vdVersion);
            }
            if (vdCnt <= 0) {
                throw new ElfParseException(src + ": verdef with no aux entries");
            }
            if (vdAux <= 0) {
                throw new ElfParseException(src + ": verdef vd_aux out of range");
            }
            long auxBase = base + vdAux;
            String firstName = null;
            List<String> aliases = new ArrayList<>();
            long auxCursor = auxBase;
            for (int i = 0; i < vdCnt; i++) {
                int vdaName = (int) r.u32(auxCursor + 4);
                int vdaNext = (int) r.u32(auxCursor + 8);
                String nm = safeString(str, vdaName);
                if (i == 0) {
                    firstName = nm;
                } else {
                    aliases.add(nm);
                }
                if (vdaNext == 0) {
                    if (i != vdCnt - 1) {
                        throw new ElfParseException(src + ": verdef aux chain ended early");
                    }
                    break;
                }
                auxCursor += vdaNext;
                if (auxCursor >= end) {
                    throw new ElfParseException(src + ": verdef aux chain runs past section");
                }
            }
            elf.getVersionDefinitions().add(
                    new VersionDefinition(vdNdx, vdHash, vdFlags, firstName, aliases));
            if (vdNext == 0) {
                break;
            }
            cursor += vdNext;
            if (cursor > end) {
                throw new ElfParseException(src + ": verdef chain runs past section");
            }
        }
    }

    private void parseVerneed(ByteReader r, ElfFile elf, List<RawSection> raw, RawSection rs, String src) {
        String[] str = linkedStringTable(r, raw, rs, src);
        r.require(rs.offset, rs.size);
        long cursor = rs.offset;
        long end = rs.offset + rs.size;
        while (cursor < end) {
            long base = cursor;
            int vnVersion = (int) r.u16(base);
            int vnCnt = (int) r.u16(base + 2);
            int vnFile = (int) r.u32(base + 4);
            int vnAux = (int) r.u32(base + 8);
            int vnNext = (int) r.u32(base + 12);
            if (vnVersion != 1) {
                throw new ElfParseException(src + ": bad verneed version " + vnVersion);
            }
            if (vnCnt <= 0 || vnAux <= 0) {
                throw new ElfParseException(src + ": malformed verneed header");
            }
            String fileName = safeString(str, vnFile);
            List<VersionRequirement.Node> nodes = new ArrayList<>();
            long auxCursor = base + vnAux;
            for (int i = 0; i < vnCnt; i++) {
                int vnaHash = (int) r.u32(auxCursor);
                int vnaFlags = (int) r.u16(auxCursor + 4);
                int vnaOther = (int) r.u16(auxCursor + 6);
                int vnaName = (int) r.u32(auxCursor + 8);
                int vnaNext = (int) r.u32(auxCursor + 12);
                if (vnaOther <= 1) {
                    throw new ElfParseException(src + ": vernaux invalid version index " + vnaOther);
                }
                nodes.add(new VersionRequirement.Node(vnaOther, vnaHash, vnaFlags,
                        safeString(str, vnaName)));
                if (vnaNext == 0) {
                    if (i != vnCnt - 1) {
                        throw new ElfParseException(src + ": vernaux chain ended early");
                    }
                    break;
                }
                auxCursor += vnaNext;
                if (auxCursor >= end) {
                    throw new ElfParseException(src + ": vernaux chain runs past section");
                }
            }
            elf.getVersionRequirements().add(new VersionRequirement(fileName, nodes));
            if (vnNext == 0) {
                break;
            }
            cursor += vnNext;
            if (cursor > end) {
                throw new ElfParseException(src + ": verneed chain runs past section");
            }
        }
    }

    // ------------------------------------------------------------------
    // Section groups (COMDAT)
    // ------------------------------------------------------------------

    private void parseGroups(ByteReader r, ElfFile elf, List<RawSection> raw, boolean elf64, String src) {
        for (RawSection rs : raw) {
            if (rs.type != ElfConstants.SHT_GROUP) {
                continue;
            }
            r.require(rs.offset, rs.size);
            if (rs.size < 4 || rs.size % 4 != 0) {
                throw new ElfParseException(src + ": malformed SHT_GROUP size " + rs.size);
            }
            // sh_link -> associated symbol table section; sh_info -> signature symbol index.
            if (rs.link <= 0 || rs.link >= raw.size()) {
                throw new ElfParseException(src + ": group sh_link out of range");
            }
            RawSection symtab = raw.get(rs.link);
            if (symtab.type != ElfConstants.SHT_SYMTAB && symtab.type != ElfConstants.SHT_DYNSYM) {
                throw new ElfParseException(src + ": group sh_link does not reference a symbol table");
            }
            if (symtab.link <= 0 || symtab.link >= raw.size()) {
                throw new ElfParseException(src + ": symbol table sh_link out of range");
            }
            String[] symStr = readStringTable(r, raw.get(symtab.link));
            int symEnt = elf64 ? ELF64_SYM_SIZE : ELF32_SYM_SIZE;
            long symOff = symtab.offset + (long) rs.info * symEnt;
            if (rs.info < 0 || symOff + symEnt > symtab.offset + symtab.size) {
                throw new ElfParseException(src + ": group signature symbol index out of range");
            }
            long nameOff = elf64 ? r.u32(symOff) : r.u32(symOff);
            String signature = safeString(symStr, nameOff);

            int flags = (int) r.u32(rs.offset);
            List<Integer> members = new ArrayList<>();
            long count = (rs.size / 4) - 1;
            for (long i = 0; i < count; i++) {
                int secIdx = (int) r.u32(rs.offset + 4 + i * 4);
                if (secIdx < 0 || secIdx >= raw.size()) {
                    throw new ElfParseException(src + ": group member section index out of range");
                }
                members.add(secIdx);
            }
            elf.getGroups().add(new SectionGroup(signature,
                    (flags & ElfConstants.GRP_COMDAT) != 0, members));
        }
    }


    // ------------------------------------------------------------------
    // Symbol tables
    // ------------------------------------------------------------------

    private void parseSymbolTables(ByteReader r, ElfFile elf, List<RawSection> raw, String src) {
        // Index maps: version index -> definition name; requirement nodes by index.
        Map<Integer, String> defNames = new HashMap<>();
        Map<Integer, Boolean> defWeak = new HashMap<>();
        for (VersionDefinition vd : elf.getVersionDefinitions()) {
            defNames.put(vd.getIndex(), vd.getName());
            defWeak.put(vd.getIndex(), (vd.getFlags() & ElfConstants.VER_FLG_WEAK) != 0);
        }
        Map<Integer, String> needNames = new HashMap<>();
        Map<Integer, Boolean> needWeak = new HashMap<>();
        for (VersionRequirement vr : elf.getVersionRequirements()) {
            for (VersionRequirement.Node n : vr.getNodes()) {
                needNames.put(n.getIndex(), n.getName());
                needWeak.put(n.getIndex(), n.isWeak());
            }
        }

        for (int sidx = 0; sidx < raw.size(); sidx++) {
            RawSection rs = raw.get(sidx);
            if (rs.type != ElfConstants.SHT_SYMTAB && rs.type != ElfConstants.SHT_DYNSYM) {
                continue;
            }
            boolean dynamic = rs.type == ElfConstants.SHT_DYNSYM;
            if (rs.link <= 0 || rs.link >= raw.size()) {
                throw new ElfParseException(src + ": symbol table " + sidx + " has bad sh_link");
            }
            String[] strtab = readStringTable(r, raw.get(rs.link));
            int[] xindex = readExtendedIndices(r, raw, sidx, elf.isElf64(), src);
            int ent = elf.isElf64() ? ELF64_SYM_SIZE : ELF32_SYM_SIZE;
            if (rs.entsize > 0 && rs.entsize != ent) {
                throw new ElfParseException(src + ": unexpected symbol entry size " + rs.entsize);
            }
            r.require(rs.offset, rs.size);
            if (rs.size % ent != 0) {
                throw new ElfParseException(src + ": symbol table size not a multiple of entry size");
            }
            int count = (int) (rs.size / ent);

            int[] versym = null;
            RawSection versymSec = findVersym(raw, sidx);
            if (versymSec != null) {
                versym = readVersym(r, versymSec, count, src);
            }

            Set<String> seenDynNames = new HashSet<>();
            List<ElfSymbol> out = dynamic ? elf.getDynamicSymbols() : elf.getStaticSymbols();

            for (int i = 0; i < count; i++) {
                long o = rs.offset + (long) i * ent;
                long nameOff;
                long value;
                long size;
                int info;
                int other;
                int shndx;
                if (elf.isElf64()) {
                    nameOff = r.u32(o);
                    info = r.u8(o + 4);
                    other = r.u8(o + 5);
                    shndx = r.u16(o + 6);
                    value = r.u64(o + 8);
                    size = r.u64(o + 16);
                } else {
                    nameOff = r.u32(o);
                    value = r.u32(o + 4);
                    size = r.u32(o + 8);
                    info = r.u8(o + 12);
                    other = r.u8(o + 13);
                    shndx = r.u16(o + 14);
                }
                if (shndx == ElfConstants.SHN_XINDEX) {
                    if (xindex == null) {
                        throw new ElfParseException(src + ": SHN_XINDEX but no symtab_shndx");
                    }
                    shndx = xindex[i];
                }
                String name = safeString(strtab, nameOff);
                String sectionName = sectionNameFor(elf, shndx);

                int versionIndex = 0;
                String versionName = null;
                ElfSymbol.VersionSource versionSource = ElfSymbol.VersionSource.NONE;
                boolean hiddenBit = false;
                if (versym != null) {
                    int rawVersym = versym[i];
                    hiddenBit = (rawVersym & ElfConstants.VERSYM_HIDDEN) != 0;
                    versionIndex = rawVersym & ElfConstants.VERSYM_VERSION;
                    if (versionIndex == ElfConstants.VER_NDX_LOCAL) {
                        versionSource = ElfSymbol.VersionSource.SPECIAL;
                    } else if (versionIndex == ElfConstants.VER_NDX_GLOBAL) {
                        versionSource = ElfSymbol.VersionSource.NONE;
                    } else if (defNames.containsKey(versionIndex)) {
                        versionName = defNames.get(versionIndex);
                        versionSource = ElfSymbol.VersionSource.VERDEF;
                    } else if (needNames.containsKey(versionIndex)) {
                        versionName = needNames.get(versionIndex);
                        versionSource = ElfSymbol.VersionSource.VERNEED;
                    } else {
                        versionName = null;
                        versionSource = ElfSymbol.VersionSource.UNRESOLVED;
                    }
                }

                // Default version: first defined dynamic symbol bearing a given
                // (name,versionIndex) is the default "@@"; later ones are "@".
                boolean defaultVersion = false;
                if (dynamic && versionName != null && shndx != ElfConstants.SHN_UNDEF) {
                    String key = name + "" + versionIndex;
                    if (seenDynNames.add(key)) {
                        defaultVersion = true;
                    }
                }
                boolean hiddenVersionNode =
                        hiddenBit || (dynamic && defWeak.getOrDefault(versionIndex, false));

                out.add(new ElfSymbol(name, value, size, ElfConstants.bind(info),
                        ElfConstants.type(info), ElfConstants.visibility(other), shndx,
                        sectionName, dynamic, versionIndex, versionName, versionSource,
                        hiddenBit, defaultVersion, hiddenVersionNode));
            }
        }
    }

    private String sectionNameFor(ElfFile elf, int shndx) {
        if (shndx == ElfConstants.SHN_UNDEF) {
            return "*UND*";
        }
        if (shndx == ElfConstants.SHN_ABS) {
            return "*ABS*";
        }
        if (shndx == ElfConstants.SHN_COMMON) {
            return "*COM*";
        }
        ElfSection s = elf.sectionByIndex(shndx);
        return s == null ? null : s.getName();
    }

    private RawSection findVersym(List<RawSection> raw, int symtabIndex) {
        for (RawSection rs : raw) {
            if (rs.type == ElfConstants.SHT_GNU_VERSYM && rs.link == symtabIndex) {
                return rs;
            }
        }
        return null;
    }

    private int[] readVersym(ByteReader r, RawSection rs, int symbolCount, String src) {
        r.require(rs.offset, rs.size);
        long entries = rs.size / 2;
        if (entries < symbolCount) {
            throw new ElfParseException(src + ": .gnu.version has fewer entries than symbol table");
        }
        int[] out = new int[symbolCount];
        for (int i = 0; i < symbolCount; i++) {
            out[i] = r.u16(rs.offset + (long) i * 2);
        }
        return out;
    }

    private int[] readExtendedIndices(ByteReader r, List<RawSection> raw, int symtabIdx,
                                      boolean elf64, String src) {
        for (RawSection rs : raw) {
            if (rs.type != ElfConstants.SHT_SYMTAB_SHNDX || rs.link != symtabIdx) {
                continue;
            }
            r.require(rs.offset, rs.size);
            int symEnt = elf64 ? ELF64_SYM_SIZE : ELF32_SYM_SIZE;
            long symEntries = raw.get(symtabIdx).size / symEnt;
            long entries = rs.size / 4;
            if (entries != symEntries) {
                throw new ElfParseException(src + ": symtab_shndx entry count mismatch");
            }
            int[] out = new int[(int) entries];
            for (int i = 0; i < entries; i++) {
                out[i] = (int) r.u32(rs.offset + (long) i * 4);
            }
            return out;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Relocations
    // ------------------------------------------------------------------

    private void parseRelocations(ByteReader r, ElfFile elf, List<RawSection> raw, int machine, String src) {
        Integer copyType = ElfConstants.COPY_RELOC_TYPES.get(machine);
        for (RawSection rs : raw) {
            if (rs.type != ElfConstants.SHT_REL && rs.type != ElfConstants.SHT_RELA) {
                continue;
            }
            if (rs.link <= 0 || rs.link >= raw.size()) {
                throw new ElfParseException(src + ": relocation sh_link out of range");
            }
            if (rs.info <= 0 || rs.info >= raw.size()) {
                throw new ElfParseException(src + ": relocation sh_info out of range");
            }
            RawSection symSec = raw.get(rs.link);
            List<ElfSymbol> symbols = symSec.type == ElfConstants.SHT_DYNSYM
                    ? elf.getDynamicSymbols() : elf.getStaticSymbols();
            boolean rela = rs.type == ElfConstants.SHT_RELA;
            int relEnt = elf.isElf64()
                    ? (rela ? ELF64_RELA_SIZE : ELF64_REL_SIZE)
                    : (rela ? ELF32_RELA_SIZE : ELF32_REL_SIZE);
            if (rs.entsize > 0 && rs.entsize != relEnt) {
                throw new ElfParseException(src + ": unexpected relocation entry size");
            }
            r.require(rs.offset, rs.size);
            if (rs.size % relEnt != 0) {
                throw new ElfParseException(src + ": relocation section size not a multiple of entry size");
            }
            long count = rs.size / relEnt;
            String targetSection = safeSectionName(elf, rs.info);
            for (long i = 0; i < count; i++) {
                long off = rs.offset + i * relEnt;
                long rOffset;
                long rInfo;
                long addend = 0;
                if (elf.isElf64()) {
                    rOffset = r.u64(off);
                    rInfo = r.u64(off + 8);
                    if (rela) {
                        addend = r.u64(off + 16);
                    }
                } else {
                    rOffset = r.u32(off);
                    rInfo = r.u32(off + 4);
                    if (rela) {
                        addend = r.u32(off + 8);
                    }
                }
                int symIdx = elf.isElf64() ? (int) (rInfo >>> 32) : (int) (rInfo >>> 8);
                int relType = elf.isElf64() ? (int) (rInfo & 0xffffffffL) : (int) (rInfo & 0xffL);
                if (symIdx < 0 || symIdx >= symbols.size()) {
                    throw new ElfParseException(src + ": relocation symbol index " + symIdx
                            + " out of range [0," + symbols.size() + ")");
                }
                String symName = symbols.get(symIdx).getName();
                boolean isCopy = copyType != null && copyType == relType;
                elf.getRelocations().add(new ElfRelocation(rOffset, symIdx, relType, addend, rela,
                        targetSection, isCopy, symName));
            }
        }
    }

    private String safeSectionName(ElfFile elf, int idx) {
        ElfSection s = elf.sectionByIndex(idx);
        return s == null ? null : s.getName();
    }

    // ------------------------------------------------------------------
    // .dynamic: NEEDED / SONAME
    // ------------------------------------------------------------------

    private void parseDynamic(ByteReader r, ElfFile elf, List<RawSection> raw, String src) {
        RawSection dyn = null;
        for (RawSection rs : raw) {
            if (rs.type == ElfConstants.SHT_DYNAMIC) {
                dyn = rs;
                break;
            }
        }
        if (dyn == null) {
            return;
        }
        int ent = elf.isElf64() ? 16 : 8;
        r.require(dyn.offset, dyn.size);
        if (dyn.size % ent != 0) {
            throw new ElfParseException(src + ": .dynamic size not a multiple of entry size");
        }

        String[] dynstr = locateDynstr(r, elf, raw, dyn, src);

        List<Long> neededOffsets = new ArrayList<>();
        long sonameOff = -1;
        long count = dyn.size / ent;
        for (long i = 0; i < count; i++) {
            long off = dyn.offset + i * ent;
            long tag = elf.isElf64() ? r.u64(off) : r.u32(off);
            long val = elf.isElf64() ? r.u64(off + 8) : r.u32(off + 4);
            if (tag == ElfConstants.DT_NULL) {
                break;
            } else if (tag == ElfConstants.DT_NEEDED) {
                neededOffsets.add(val);
            } else if (tag == ElfConstants.DT_SONAME) {
                sonameOff = val;
            }
        }
        for (Long off : neededOffsets) {
            elf.getNeeded().add(safeString(dynstr, off));
        }
        if (sonameOff >= 0) {
            elf.setSoname(safeString(dynstr, sonameOff));
        }
    }

    private String[] locateDynstr(ByteReader r, ElfFile elf, List<RawSection> raw,
                                  RawSection dyn, String src) {
        int ent = elf.isElf64() ? 16 : 8;
        long count = dyn.size / ent;
        long strtabAddr = -1;
        for (long i = 0; i < count; i++) {
            long off = dyn.offset + i * ent;
            long tag = elf.isElf64() ? r.u64(off) : r.u32(off);
            long val = elf.isElf64() ? r.u64(off + 8) : r.u32(off + 4);
            if (tag == ElfConstants.DT_STRTAB) {
                strtabAddr = val;
                break;
            }
            if (tag == ElfConstants.DT_NULL) {
                break;
            }
        }
        if (strtabAddr >= 0) {
            for (RawSection rs : raw) {
                if (rs.type == ElfConstants.SHT_STRTAB && rs.addr != 0
                        && strtabAddr >= rs.addr
                        && strtabAddr < rs.addr + Math.max(rs.size, 1)) {
                    return readStringTableOffset(r, rs, strtabAddr - rs.addr);
                }
            }
        }
        // Fallback: conventional .dynstr, or sh_link of the first dynsym.
        for (RawSection rs : raw) {
            if (rs.type == ElfConstants.SHT_DYNSYM && rs.link > 0 && rs.link < raw.size()) {
                return readStringTable(r, raw.get(rs.link));
            }
        }
        throw new ElfParseException(src + ": cannot locate dynamic string table");
    }

    private String[] readStringTableOffset(ByteReader r, RawSection rs, long relativeOffset) {
        // DT_STRTAB normally points exactly at the table; read the whole table and shift indexes.
        String[] full = readStringTable(r, rs);
        if (relativeOffset == 0) {
            return full;
        }
        // Reparent indexes so safeString(off - relativeOffset) works through a padded array.
        String[] shifted = new String[(int) (relativeOffset + full.length)];
        System.arraycopy(full, 0, shifted, (int) relativeOffset, full.length);
        return shifted;
    }
}
