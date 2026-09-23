package com.tribunal.elf;

import com.tribunal.elf.ElfModel.Class;
import com.tribunal.elf.ElfModel.ElfFile;
import com.tribunal.elf.ElfModel.Endian;
import com.tribunal.elf.ElfModel.Relocation;
import com.tribunal.elf.ElfModel.Section;
import com.tribunal.elf.ElfModel.SectionGroup;
import com.tribunal.elf.ElfModel.Symbol;
import com.tribunal.elf.ElfModel.VersionDef;
import com.tribunal.elf.ElfModel.VersionNeed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Java ELF parser for {@link Elf#ET_REL}, {@link Elf#ET_EXEC} and
 * {@link Elf#ET_DYN} objects. Supports ELFCLASS32/64, both endiannesses,
 * extended section numbering (SHN_XINDEX), GNU symbol versioning,
 * SHT_GROUP section groups and REL/RELA relocations.
 *
 * <p>All integer reads and table walks are bounds checked; a truncated or
 * corrupt object produces {@link ElfFormatException} instead of an
 * unexpected runtime error.</p>
 */
public final class ElfParser {

    public ElfFile parse(byte[] data) throws ElfFormatException {
        return parse(data, "<memory>");
    }

    public ElfFile parse(byte[] data, String sourceLabel) throws ElfFormatException {
        Context ctx = new Context();
        ctx.data = data;
        List<String> warnings = new ArrayList<>();
        if (data == null || data.length < Elf.EI_NIDENT) {
            throw new ElfFormatException("file shorter than ELF identification (16 bytes)");
        }
        if ((data[0] & 0xff) != Elf.ELFMAG0 || data[1] != Elf.ELFMAG1
                || data[2] != Elf.ELFMAG2 || data[3] != Elf.ELFMAG3) {
            throw new ElfFormatException("bad ELF magic number from " + sourceLabel);
        }
        int cls = data[Elf.EI_CLASS] & 0xff;
        if (cls != Elf.ELFCLASS32 && cls != Elf.ELFCLASS64) {
            throw new ElfFormatException("unsupported EI_CLASS " + cls);
        }
        int enc = data[Elf.EI_DATA] & 0xff;
        if (enc != Elf.ELFDATA2LSB && enc != Elf.ELFDATA2MSB) {
            throw new ElfFormatException("unsupported EI_DATA " + enc);
        }
        boolean elf64 = cls == Elf.ELFCLASS64;
        boolean bigEndian = enc == Elf.ELFDATA2MSB;
        ctx.r = new ElfReader(data, bigEndian, elf64);

        short type = (short) ctx.r.u16(16);
        short machine = (short) ctx.r.u16(18);
        long flags;
        long entry;
        long shoff;
        int shentsize;
        int shnum;
        int shstrndx;
        if (elf64) {
            int eVersion = (int) ctx.r.u32(20);
            if (eVersion != 1) {
                throw new ElfFormatException("unsupported e_version " + eVersion);
            }
            entry = ctx.r.addr(24);
            // phoff at 32
            shoff = ctx.r.off(40);
            shentsize = ctx.r.u16(58);
            shnum = ctx.r.u16(60);
            shstrndx = ctx.r.u16(62);
            flags = ctx.r.u32(48);
        } else {
            int eVersion = (int) ctx.r.u32(20);
            if (eVersion != 1) {
                throw new ElfFormatException("unsupported e_version " + eVersion);
            }
            entry = ctx.r.addr(24);
            // phoff at 28
            shoff = ctx.r.off(32);
            flags = ctx.r.u32(36);
            shentsize = ctx.r.u16(40);
            shnum = ctx.r.u16(44);
            shstrndx = ctx.r.u16(46);
        }

        List<Section> sections =
                parseSectionHeaders(ctx, elf64, shoff, shentsize, shnum, shstrndx, warnings);
        parseVersionSections(ctx, sections);
        Map<Integer, List<Symbol>> symbolTables = parseSymbolTables(ctx, sections, warnings);
        List<Symbol> symbols = chooseMainSymbols(sections, symbolTables);
        List<SectionGroup> groups = parseGroups(ctx, sections, symbolTables, warnings);
        List<Relocation> relocations =
                parseRelocations(ctx, sections, symbolTables, warnings);

        List<String> needed = new ArrayList<>();
        String soname = null;
        for (Section section : sections) {
            if (section.type() != Elf.SHT_DYNAMIC) {
                continue;
            }
            Section strSec = sections.get(Math.max(section.link(), 0));
            long strOff = strSec.offset();
            long strSize = strSec.type() == Elf.SHT_NOBITS ? 0 : strSec.size();
            int ents = elf64 ? 16 : 8;
            if (section.entsize() > 0) {
                ents = (int) section.entsize();
            }
            if (ents <= 0 || section.size() % ents != 0) {
                warnings.add("SHT_DYNAMIC size not a multiple of entry size (section "
                        + section.index() + ")");
                continue;
            }
            for (long pos = section.offset(); pos < section.offset() + section.size();
                 pos += ents) {
                long tag;
                long val;
                if (elf64) {
                    tag = ctx.r.u64(pos);
                    val = ctx.r.u64(pos + 8);
                } else {
                    tag = ctx.r.u32(pos);
                    val = ctx.r.u32(pos + 4);
                }
                if (tag == Elf.DT_NULL) {
                    break;
                }
                if (tag == Elf.DT_NEEDED || tag == Elf.DT_SONAME) {
                    String name = ctx.r.strtabString(strOff, strSize, (int) val);
                    if (tag == Elf.DT_NEEDED) {
                        needed.add(name);
                    } else if (soname == null) {
                        soname = name;
                    }
                }
            }
        }

        return new ElfFile(data, sourceLabel,
                elf64 ? Class.ELF64 : Class.ELF32,
                bigEndian ? Endian.BIG : Endian.LITTLE, type, machine, entry, flags,
                List.copyOf(sections), List.copyOf(symbols), List.copyOf(relocations),
                List.copyOf(groups), List.copyOf(ctx.versionDefs),
                List.copyOf(ctx.versionNeeds), Map.of(), List.copyOf(needed),
                soname, List.copyOf(warnings));
    }

    private static final class Context {
        byte[] data;
        ElfReader r;
        final Map<Integer, String> versionIndexNames = new HashMap<>();
        final List<VersionDef> versionDefs = new ArrayList<>();
        final List<VersionNeed> versionNeeds = new ArrayList<>();
    }

    private List<Section> parseSectionHeaders(Context ctx, boolean elf64, long shoff,
                                              int shentsize, int shnumIn, int shstrndxIn,
                                              List<String> warnings)
            throws ElfFormatException {
        int shnum = shnumIn;
        int shstrndx = shstrndxIn;
        int minEnt = elf64 ? 64 : 40;
        if (shoff == 0) {
            if (shnum != 0 || shstrndx != 0) {
                throw new ElfFormatException("no section header table but shnum/shstrndx set");
            }
            return List.of();
        }
        if (shentsize < minEnt) {
            throw new ElfFormatException("e_shentsize " + shentsize + " smaller than " + minEnt);
        }
        if (shnum == 0) {
            // Extended section numbering: real count lives in section 0's sh_size.
            ctx.r.require(shoff, shentsize);
            long at = shoff;
            shnum = (int) (elf64 ? ctx.r.u64(at + 32) : ctx.r.u32(at + 20));
            if (shstrndx == Elf.SHN_XINDEX) {
                shstrndx = (int) (elf64 ? ctx.r.u64(at + 24) : ctx.r.u32(at + 24));
            }
            if (shnum <= 0) {
                throw new ElfFormatException("extended section numbering claims 0 sections");
            }
        }
        long end = shoff + (long) shnum * shentsize;
        if (end < 0 || end > ctx.data.length) {
            throw new ElfFormatException("section header table runs past end of file");
        }
        List<Section> sections = new ArrayList<>(shnum);
        for (int i = 0; i < shnum; i++) {
            long at = shoff + (long) i * shentsize;
            int nameOff = (int) ctx.r.u32(at);
            int sType = (int) ctx.r.u32(at + 4);
            long sFlags;
            long sAddr;
            long sOffset;
            long sSize;
            int sLink;
            int sInfo;
            long sAlign;
            long sEnt;
            if (elf64) {
                sFlags = ctx.r.u64(at + 8);
                sAddr = ctx.r.addr(at + 16);
                sOffset = ctx.r.off(at + 24);
                sSize = ctx.r.u64(at + 32);
                sLink = (int) ctx.r.u32(at + 40);
                sInfo = (int) ctx.r.u32(at + 44);
                sAlign = ctx.r.u64(at + 48);
                sEnt = ctx.r.u64(at + 56);
            } else {
                sFlags = ctx.r.u32(at + 8);
                sAddr = ctx.r.addr(at + 12);
                sOffset = ctx.r.off(at + 16);
                sSize = ctx.r.u32(at + 20);
                sLink = (int) ctx.r.u32(at + 24);
                sInfo = (int) ctx.r.u32(at + 28);
                sAlign = ctx.r.u32(at + 32);
                sEnt = ctx.r.u32(at + 36);
            }
            if (i == 0 && shnumIn == 0) {
                // The synthetic section zero carries the real values sh_size/sh_link.
                sSize = 0;
                sLink = 0;
            }
            sections.add(new Section(i, nameOff, "", sType, sFlags, sAddr, sOffset, sSize,
                    sLink, sInfo, sAlign, sEnt));
        }
        if (shstrndx != 0) {
            if (shstrndx < 0 || shstrndx >= sections.size()) {
                throw new ElfFormatException("e_shstrndx " + shstrndx + " out of range");
            }
            Section shstr = sections.get(shstrndx);
            if (shstr.type() != Elf.SHT_NOBITS) {
                for (int i = 0; i < sections.size(); i++) {
                    Section s = sections.get(i);
                    if (i == shstrndx || s.nameOffset() == 0) {
                        continue;
                    }
                    try {
                        String nm = ctx.r.strtabString(shstr.offset(), shstr.size(),
                                s.nameOffset());
                        sections.set(i, new Section(i, s.nameOffset(), nm, s.type(), s.flags(),
                                s.addr(), s.offset(), s.size(), s.link(), s.info(),
                                s.addralign(), s.entsize()));
                    } catch (ElfFormatException ex) {
                        warnings.add("section " + i + " has invalid sh_name: " + ex.getMessage());
                    }
                }
            }
        }
        validateSectionBounds(ctx, sections, warnings);
        return sections;
    }

    private void validateSectionBounds(Context ctx, List<Section> sections,
                                       List<String> warnings) throws ElfFormatException {
        for (Section s : sections) {
            if (s.index() == 0 || s.type() == Elf.SHT_NOBITS) {
                continue;
            }
            if (s.offset() < 0 || s.size() < 0
                    || s.offset() + s.size() < 0
                    || s.offset() + s.size() > ctx.data.length) {
                throw new ElfFormatException(
                        "section " + s.index() + " (" + s.name() + ") offset/size out of file");
            }
        }
    }

    /**
     * Walk SHT_GNU_verdef / SHT_GNU_verneed sections and build a map from
     * versym index to the human readable version name.
     */
    private void parseVersionSections(Context ctx, List<Section> sections)
            throws ElfFormatException {
        for (Section section : sections) {
            if (section.type() == Elf.SHT_GNU_verdef) {
                parseVerdef(ctx, sections, section);
            } else if (section.type() == Elf.SHT_GNU_verneed) {
                parseVerneed(ctx, sections, section);
            }
        }
    }

    private void parseVerdef(Context ctx, List<Section> sections, Section section)
            throws ElfFormatException {
        Section strSec = section.link() >= 0 && section.link() < sections.size()
                ? sections.get(section.link()) : null;
        if (strSec == null || strSec.type() == Elf.SHT_NOBITS) {
            throw new ElfFormatException("verdef section " + section.index()
                    + " has invalid linked string table");
        }
        long base = section.offset();
        long limit = base + section.size();
        long off = 0;
        int nodes = 0;
        int guard = 0;
        while (off != 0 || nodes == 0) {
            if (++guard > 65536 || base + off + 20 > limit) {
                throw new ElfFormatException("truncated verdef chain in section "
                        + section.index());
            }
            long at = base + off;
            long nextOff = ctx.r.u32(at);
            int flags = (int) ctx.r.u16(at + 4);
            int ndx = (int) ctx.r.u16(at + 6);
            int cnt = (int) ctx.r.u16(at + 8);
            long auxOff = ctx.r.u32(at + 12);
            String name = "";
            List<String> parents = new ArrayList<>();
            long aoff = auxOff;
            for (int a = 0; a < Math.max(cnt, 1) && aoff != 0; a++) {
                long aa = at + aoff;
                if (aa + 8 > limit) {
                    throw new ElfFormatException("truncated verdaux in section "
                            + section.index());
                }
                long aNext = ctx.r.u32(aa);
                int nameIdx = (int) ctx.r.u32(aa + 4);
                String value = ctx.r.strtabString(strSec.offset(), strSec.size(), nameIdx);
                if (a == 0) {
                    name = value;
                } else {
                    parents.add(value);
                }
                aoff = aNext;
            }
            VersionDef def = new VersionDef(ndx, name, flags, ndx, List.copyOf(parents));
            ctx.versionDefs.add(def);
            ctx.versionIndexNames.put(ndx, name);
            nodes++;
            if (nextOff == 0) {
                break;
            }
            off = nextOff;
        }
    }

    private void parseVerneed(Context ctx, List<Section> sections, Section section)
            throws ElfFormatException {
        Section strSec = section.link() >= 0 && section.link() < sections.size()
                ? sections.get(section.link()) : null;
        if (strSec == null || strSec.type() == Elf.SHT_NOBITS) {
            throw new ElfFormatException("verneed section " + section.index()
                    + " has invalid linked string table");
        }
        long base = section.offset();
        long limit = base + section.size();
        long off = 0;
        int guard = 0;
        while (true) {
            if (++guard > 65536 || base + off + 16 > limit) {
                throw new ElfFormatException("truncated verneed chain in section "
                        + section.index());
            }
            long at = base + off;
            long nextOff = ctx.r.u32(at);
            int fileIdx = (int) ctx.r.u32(at + 4);
            int auxCnt = (int) ctx.r.u16(at + 8);
            long auxOff = ctx.r.u32(at + 12);
            String fileName = ctx.r.strtabString(strSec.offset(), strSec.size(), fileIdx);
            long aoff = auxOff;
            for (int a = 0; a < Math.max(auxCnt, 1) && aoff != 0; a++) {
                long aa = at + aoff;
                if (aa + 16 > limit) {
                    throw new ElfFormatException("truncated vernaux in section "
                            + section.index());
                }
                long aNext = ctx.r.u32(aa);
                int nameIdx = (int) ctx.r.u32(aa + 4);
                int ndx = (int) ctx.r.u16(aa + 12);
                int flags = (int) ctx.r.u16(aa + 14);
                String versionName = ctx.r.strtabString(strSec.offset(), strSec.size(),
                        nameIdx);
                ctx.versionNeeds.add(new VersionNeed(fileName, ndx, versionName,
                        (flags & Elf.VER_FLG_WEAK) != 0, List.of()));
                ctx.versionIndexNames.put(ndx, versionName);
                aoff = aNext;
            }
            if (nextOff == 0) {
                break;
            }
            off = nextOff;
        }
    }

    private Map<Integer, List<Symbol>> parseSymbolTables(Context ctx, List<Section> sections,
                                                         List<String> warnings)
            throws ElfFormatException {
        Map<Integer, List<Symbol>> tables = new HashMap<>();
        for (Section symSec : sections) {
            if (symSec.type() != Elf.SHT_SYMTAB && symSec.type() != Elf.SHT_DYNSYM) {
                continue;
            }
            List<Symbol> table = parseOneSymbolTable(ctx, sections, symSec, warnings);
            tables.put(symSec.index(), table);
        }
        return tables;
    }

    private List<Symbol> chooseMainSymbols(List<Section> sections,
                                           Map<Integer, List<Symbol>> tables) {
        for (Section s : sections) {
            if (s.type() == Elf.SHT_SYMTAB && tables.containsKey(s.index())) {
                return tables.get(s.index());
            }
        }
        for (Section s : sections) {
            if (s.type() == Elf.SHT_DYNSYM && tables.containsKey(s.index())) {
                return tables.get(s.index());
            }
        }
        return List.of();
    }

    private List<Symbol> parseOneSymbolTable(Context ctx, List<Section> sections,
                                             Section symSec, List<String> warnings)
            throws ElfFormatException {
        List<Symbol> all = new ArrayList<>();
            if (symSec.link() <= 0 || symSec.link() >= sections.size()) {
                throw new ElfFormatException("symbol section " + symSec.index()
                        + " has invalid linked string table");
            }
            Section strSec = sections.get(symSec.link());
            if (strSec.type() == Elf.SHT_NOBITS) {
                throw new ElfFormatException("symbol section " + symSec.index()
                        + " string table is NOBITS");
            }
            boolean elf64 = ctx.r.elf64;
            int ent = (int) (symSec.entsize() > 0 ? symSec.entsize()
                    : (elf64 ? 24 : 16));
            if (ent < (elf64 ? 24 : 16)) {
                throw new ElfFormatException("symbol entry size " + ent + " too small");
            }
            if (symSec.size() % ent != 0) {
                throw new ElfFormatException("symbol table section " + symSec.index()
                        + " size not a multiple of entry size");
            }
            int count = (int) (symSec.size() / ent);

            // Optional SHT_SYMTAB_SHNDX for extended section indices.
            int[] xindex = null;
            for (Section x : sections) {
                if (x.type() == Elf.SHT_SYMTAB_SHNDX && x.link() == symSec.index()) {
                    if (x.size() < (long) count * 4) {
                        throw new ElfFormatException("SYMTAB_SHNDX section " + x.index()
                                + " too small for " + count + " symbols");
                    }
                    xindex = new int[count];
                    for (int i = 0; i < count; i++) {
                        xindex[i] = (int) ctx.r.u32(x.offset() + (long) i * 4);
                    }
                    break;
                }
            }

            // Optional GNU versym table associated with this symbol table.
            long[] versym = null;
            for (Section v : sections) {
                if (v.type() == Elf.SHT_GNU_versym && v.link() == symSec.index()) {
                    if (v.size() < (long) count * 2) {
                        throw new ElfFormatException("versym section " + v.index()
                                + " too small for " + count + " symbols");
                    }
                    versym = new long[count];
                    for (int i = 0; i < count; i++) {
                        versym[i] = ctx.r.u16(v.offset() + (long) i * 2) & 0xffffL;
                    }
                    break;
                }
            }

            int firstLocal = symSec.info();
            for (int i = 0; i < count; i++) {
                long at = symSec.offset() + (long) i * ent;
                int nameIdx;
                int info;
                int other;
                int shndx;
                long value;
                long size;
                if (elf64) {
                    nameIdx = (int) ctx.r.u32(at);
                    info = ctx.r.u8(at + 4) & 0xff;
                    other = ctx.r.u8(at + 5) & 0xff;
                    shndx = ctx.r.u16(at + 6);
                    value = ctx.r.addr(at + 8);
                    size = ctx.r.u64(at + 16);
                } else {
                    nameIdx = (int) ctx.r.u32(at);
                    value = ctx.r.addr(at + 4);
                    size = ctx.r.u32(at + 8);
                    info = ctx.r.u8(at + 12) & 0xff;
                    other = ctx.r.u8(at + 13) & 0xff;
                    shndx = ctx.r.u16(at + 14);
                }
                if (shndx == Elf.SHN_XINDEX) {
                    if (xindex != null) {
                        shndx = xindex[i];
                    } else {
                        warnings.add("symbol " + i + " uses SHN_XINDEX but no "
                                + "SYMTAB_SHNDX present; treated as UNDEF");
                        shndx = Elf.SHN_UNDEF;
                    }
                }
                if (shndx != 0 && !Elf.isReservedShndx(shndx)
                        && (shndx < 0 || shndx >= sections.size())) {
                    warnings.add("symbol " + i + " references invalid section " + shndx
                            + "; treated as UNDEF");
                    shndx = Elf.SHN_UNDEF;
                }
                String name = ctx.r.strtabString(strSec.offset(), strSec.size(), nameIdx);
                int binding = info >> 4;
                int type = info & 0xf;
                int visibility = other & 0x3;
                long verRaw = versym != null ? versym[i] : -1L;
                int verIndex = -1;
                String verName = null;
                boolean hidden = false;
                boolean defaultVer = false;
                if (verRaw >= 0 && i != 0) {
                    int raw = (int) verRaw;
                    verIndex = raw & Elf.VERSYM_VERSION_MASK;
                    hidden = (raw & Elf.VER_NDX_HIDDEN) != 0;
                    if (verIndex > 1) {
                        verName = ctx.versionIndexNames.get(verIndex);
                        // A version node is the "default" version when the symbol
                        // references it without the hidden (non-default) bit.
                        defaultVer = !hidden;
                    } else {
                        verIndex = raw & 0xffff;
                        defaultVer = verIndex == Elf.VER_NDX_GLOBAL;
                    }
                }
                all.add(new Symbol(i, name, binding, type, visibility, shndx, value, size,
                        verIndex, verName, hidden, defaultVer));
            }
        return List.copyOf(all);
    }

    private List<SectionGroup> parseGroups(Context ctx, List<Section> sections,
                                           Map<Integer, List<Symbol>> symbolTables,
                                           List<String> warnings)
            throws ElfFormatException {
        List<SectionGroup> groups = new ArrayList<>();
        for (Section g : sections) {
            if (g.type() != Elf.SHT_GROUP) {
                continue;
            }
            if (g.link() <= 0 || g.link() >= sections.size()) {
                throw new ElfFormatException("group section " + g.index()
                        + " has invalid linked symbol table");
            }
            if (g.size() < 4) {
                throw new ElfFormatException("group section " + g.index() + " truncated");
            }
            int flags = (int) ctx.r.u32(g.offset());
            int count = (int) (g.size() / 4);
            List<Integer> members = new ArrayList<>();
            for (int i = 1; i < count; i++) {
                int member = (int) ctx.r.u32(g.offset() + (long) i * 4);
                if (member < 0 || member >= sections.size()) {
                    throw new ElfFormatException("group " + g.index()
                            + " references invalid member section " + member);
                }
                members.add(member);
            }
            List<Symbol> linked = symbolTables.get(g.link());
            int sigSymIndex = g.info();
            if (linked == null) {
                throw new ElfFormatException("group section " + g.index()
                        + " links missing symbol table section " + g.link());
            }
            String signature;
            if (sigSymIndex >= 0 && sigSymIndex < linked.size()) {
                signature = linked.get(sigSymIndex).name();
            } else {
                warnings.add("group section " + g.index()
                        + " signature symbol index out of range");
                signature = "";
            }
            groups.add(new SectionGroup(g.index(), flags, signature, List.copyOf(members)));
        }
        return groups;
    }

    private List<Relocation> parseRelocations(Context ctx, List<Section> sections,
                                              Map<Integer, List<Symbol>> symbolTables,
                                              List<String> warnings)
            throws ElfFormatException {
        boolean elf64 = ctx.r.elf64;
        List<Relocation> rels = new ArrayList<>();
        for (Integer key : List.of(Elf.SHT_REL, Elf.SHT_RELA)) {
            for (Section r : sections) {
                if (r.type() != key) {
                    continue;
                }
                boolean rela = key == Elf.SHT_RELA;
                int ent = (int) (r.entsize() > 0 ? r.entsize()
                        : (elf64 ? (rela ? 24 : 16) : (rela ? 12 : 8)));
                int minEnt = elf64 ? (rela ? 24 : 16) : (rela ? 12 : 8);
                if (ent < minEnt) {
                    throw new ElfFormatException("relocation section " + r.index()
                            + " entry size " + ent + " too small");
                }
                if (r.size() % ent != 0) {
                    throw new ElfFormatException("relocation section " + r.index()
                            + " size not a multiple of entry size");
                }
                int targetSection = r.info();
                if (targetSection < 0 || targetSection >= sections.size()) {
                    throw new ElfFormatException("relocation section " + r.index()
                            + " targets invalid section " + targetSection);
                }
                List<Symbol> linkedSyms = symbolTables.get(r.link());
                if (linkedSyms == null) {
                    throw new ElfFormatException("relocation section " + r.index()
                            + " links missing symbol table section " + r.link());
                }
                int count = (int) (r.size() / ent);
                for (int i = 0; i < count; i++) {
                    long at = r.offset() + (long) i * ent;
                    long offset;
                    long info;
                    long addend = 0;
                    if (elf64) {
                        offset = ctx.r.addr(at);
                        info = rela ? ctx.r.u64(at + 8) : ctx.r.u64(at + 8);
                        if (rela) {
                            addend = ctx.r.u64(at + 16);
                        }
                    } else {
                        offset = ctx.r.addr(at);
                        info = ctx.r.u32(at + 4);
                        if (rela) {
                            addend = ctx.r.u32(at + 8);
                        }
                    }
                    int symIndex = elf64 ? (int) (info >> 32) : (int) (info >> 8);
                    int type = elf64 ? (int) (info & 0xffffffffL) : (int) (info & 0xff);
                    if (symIndex < 0 || symIndex >= linkedSyms.size()) {
                        warnings.add("relocation in section " + r.index()
                                + " references invalid symbol " + symIndex);
                        continue;
                    }
                    String sName = linkedSyms.get(symIndex).name();
                    rels.add(new Relocation(targetSection, offset, addend, info, symIndex,
                            type, rela, sName));
                }
            }
        }
        return rels;
    }
}
