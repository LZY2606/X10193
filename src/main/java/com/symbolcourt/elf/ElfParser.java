package com.symbolcourt.elf;

import com.symbolcourt.model.Models.*;
import java.util.*;

/**
 * Parses just the ELF metadata the symbol court needs.
 * Every read goes through {@link BinaryReader} bounds checks so a corrupt image
 * raises {@link ElfReadException}; callers isolate the offending object only.
 */
public class ElfParser {

    public ParsedObject parse(String id, String name, byte[] data) {
        BinaryReader r = new BinaryReader(data);
        if (data.length < Elf.EI_NIDENT) throw new ElfReadException("file shorter than ELF ident");
        if (r.u8(0) != 0x7f || r.u8(1) != 'E' || r.u8(2) != 'L' || r.u8(3) != 'F') {
            throw new ElfReadException("bad ELF magic");
        }
        int cls = r.u8(Elf.EI_CLASS);
        if (cls != Elf.ELFCLASS64 && cls != Elf.ELFCLASS32) throw new ElfReadException("unknown ELF class " + cls);
        int enc = r.u8(Elf.EI_DATA);
        if (enc == Elf.ELFDATA2LSB) r.little = true;
        else if (enc == Elf.ELFDATA2MSB) r.little = false;
        else throw new ElfReadException("unknown ELF data encoding " + enc);

        ParsedObject o = new ParsedObject();
        o.id = id;
        o.name = name;
        o.origin = name;
        o.elfClass = cls == Elf.ELFCLASS64 ? 64 : 32;
        o.littleEndian = r.little;
        boolean is64 = cls == Elf.ELFCLASS64;

        int type = r.u16(16);
        o.kind = switch (type) {
            case Elf.ET_REL -> Kind.OBJECT;
            case Elf.ET_EXEC -> Kind.EXECUTABLE;
            case Elf.ET_DYN -> Kind.SHARED;
            default -> Kind.OBJECT;
        };
        long machine = r.u16(18) & 0xffffL;
        o.arch = machine == 62 ? "x86_64" : machine == 3 ? "x86" : "machine_" + machine;

        // section header table
        long shoff = is64 ? r.u64(0x28) : r.u32(0x20);
        int shentsize = is64 ? r.u16(0x3a) : r.u16(0x2e);
        int shnum = is64 ? r.u16(0x3c) : r.u16(0x30);
        int shstrndx = is64 ? r.u16(0x3e) : r.u16(0x32);

        if (shoff == 0 || shnum == 0) {
            throw new ElfReadException("no section headers: only section based images are supported");
        }
        if (shentsize <= 0 || !r.inRange(shoff, (long) shentsize * shnum)) {
            throw new ElfReadException("section header table out of range");
        }

        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            long base = shoff + (long) i * shentsize;
            Section s = new Section();
            if (is64) {
                s.nameOff = r.u32(base);
                s.type = (int) r.u32(base + 4);
                s.flags = r.u64(base + 8);
                s.addr = r.u64(base + 16);
                s.offset = r.u64(base + 24);
                s.size = r.u64(base + 32);
                s.link = (int) r.u32(base + 40);
                s.info = r.u32(base + 44);
                s.entsize = r.u64(base + 56);
            } else {
                s.nameOff = r.u32(base);
                s.type = (int) r.u32(base + 4);
                s.flags = r.u32(base + 8);
                s.addr = r.u32(base + 12);
                s.offset = r.u32(base + 16);
                s.size = r.u32(base + 20);
                s.link = (int) r.u32(base + 24);
                s.info = r.u32(base + 28);
                s.entsize = r.u32(base + 36);
            }
            sections.add(s);
        }
        // extended section count / string index
        if (sections.size() == 0) throw new ElfReadException("no sections");
        if (shnum == 0 || shstrndx == Elf.SHN_XINDEX) {
            Section first = sections.get(0);
            if (shnum == 0) shnum = (int) first.size;
            if (shstrndx == Elf.SHN_XINDEX) shstrndx = (int) first.link;
        }
        if (shstrndx < 0 || shstrndx >= sections.size()) {
            throw new ElfReadException("section name string index out of range: " + shstrndx);
        }
        Section shstr = sections.get(shstrndx);
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            s.index = i;
            s.name = r.cstr(s.nameOff, shstr.offset, shstr.size);
            o.sections.add(s.name);
        }

        // dynamic metadata (soname / needed)
        parseDynamic(r, o, sections);

        // symbol tables
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            if (s.type == Elf.SHT_SYMTAB || s.type == Elf.SHT_DYNSYM) {
                parseSymbols(r, o, sections, s, s.type == Elf.SHT_DYNSYM);
            }
        }
        // version tables
        parseVerdef(r, o, sections);
        parseVerneed(r, o, sections);
        applyVersions(o, r, sections);
        // groups and COMDAT signatures
        parseGroups(r, o, sections);
        tagComdatSymbols(o);
        // relocations
        parseRelocations(r, o, sections);
        return o;
    }

    static class Section {
        int index;
        long nameOff;
        int type;
        long flags;
        long addr, offset, size, entsize;
        int link;
        long info;
        String name = "";
    }

    private void parseDynamic(BinaryReader r, ParsedObject o, List<Section> sections) {
        for (Section s : sections) {
            if (s.type != Elf.SHT_DYNAMIC) continue;
            Section str = (s.link >= 0 && s.link < sections.size()) ? sections.get(s.link) : null;
            int ent = s.entsize == 0 ? (o.elfClass == 64 ? 16 : 8) : (int) s.entsize;
            if (ent <= 0 || s.size / ent > 1_000_000) continue;
            for (long off = s.offset; off + ent <= s.offset + s.size; off += ent) {
                long tag, val;
                if (o.elfClass == 64) { tag = r.u64(off); val = r.u64(off + 8); }
                else { tag = r.u32(off); val = r.u32(off + 4); }
                if (tag == Elf.DT_NULL) break;
                if (str != null) {
                    if (tag == Elf.DT_NEEDED) o.needed.add(r.cstr(val, str.offset, str.size));
                    else if (tag == Elf.DT_SONAME) o.soname = r.cstr(val, str.offset, str.size);
                }
            }
        }
    }

    private void parseSymbols(BinaryReader r, ParsedObject o, List<Section> sections, Section sym, boolean dynamic) {
        if (sym.link < 0 || sym.link >= sections.size()) return;
        Section str = sections.get(sym.link);
        int ent = sym.entsize == 0 ? (o.elfClass == 64 ? 24 : 16) : (int) sym.entsize;
        if (ent <= 0 || sym.size / ent > 5_000_000) {
            o.diagnostics.add("symbol table " + sym.name + " has implausible size");
            return;
        }
        // extended section indexes
        Section xindex = null;
        for (Section s : sections) {
            if (s.type == Elf.SHT_SYMTAB_SHNDX && s.link == sym.index) { xindex = s; break; }
        }
        int firstLocal = (int) sym.info;
        long count = sym.size / ent;
        for (long k = 0; k < count; k++) {
            long off = sym.offset + k * ent;
            ElfSymbol s = new ElfSymbol();
            int nameIdx;
            long value, size;
            int info, other, shndx;
            if (o.elfClass == 64) {
                nameIdx = (int) r.u32(off);
                info = r.u8(off + 4);
                other = r.u8(off + 5);
                shndx = r.u16(off + 6);
                value = r.u64(off + 8);
                size = r.u64(off + 16);
            } else {
                nameIdx = (int) r.u32(off);
                value = r.u32(off + 4);
                size = r.u32(off + 8);
                info = r.u8(off + 12);
                other = r.u8(off + 13);
                shndx = r.u16(off + 14);
            }
            s.name = r.cstr(nameIdx, str.offset, str.size);
            s.bind = Elf.stBind(info);
            s.type = Elf.stType(info);
            s.visibility = Elf.stVisibility(other);
            s.value = value;
            s.size = size;
            if (shndx == Elf.SHN_XINDEX && xindex != null) {
                long e = (o.elfClass == 64 ? r.u32(xindex.offset + k * 4) : r.u32(xindex.offset + k * 4));
                shndx = (int) e;
            }
            s.shndx = shndx;
            s.dynamic = dynamic;
            s.index = (int) k;
            s.undefined = shndx == Elf.SHN_UNDEF && !dynamicFalseDefined(s);
            s.common = shndx == Elf.SHN_COMMON || s.type == Elf.STT_COMMON;
            s.local = s.bind == Elf.STB_LOCAL;
            if (s.common && o.elfClass == 64) s.commonAlignment = value == 0 ? 1 : value;
            if (s.common && o.elfClass == 32) {
                // Elf32_Sym st_value holds size, st_size holds alignment
                s.commonAlignment = size == 0 ? 1 : size;
                s.size = value;
            }
            if (k < firstLocal) {
                // global/weak order per ELF spec
            }
            o.symbols.add(s);
        }
    }

    private boolean dynamicFalseDefined(ElfSymbol s) { return false; }

    private void parseRelocations(BinaryReader r, ParsedObject o, List<Section> sections) {
        for (Section s : sections) {
            if (s.type != Elf.SHT_RELA && s.type != Elf.SHT_REL) continue;
            if (s.info < 0 || s.info >= sections.size()) continue;
            Section symSec = sections.get((int) s.info);
            int ent = s.entsize == 0
                    ? (s.type == Elf.SHT_RELA ? (o.elfClass == 64 ? 24 : 12) : (o.elfClass == 64 ? 16 : 8))
                    : (int) s.entsize;
            if (ent <= 0 || s.size / ent > 5_000_000) continue;
            Map<Integer, ElfSymbol> byIndex = new HashMap<>();
            for (ElfSymbol es : o.symbols) if (es.dynamic == (symSec.type == Elf.SHT_DYNSYM)) byIndex.put(es.index, es);
            for (long off = s.offset; off + ent <= s.offset + s.size; off += ent) {
                Relocation rel = new Relocation();
                rel.section = s.name;
                long info;
                if (o.elfClass == 64) {
                    rel.offset = r.u64(off);
                    info = r.u64(off + 8);
                    rel.addend = s.type == Elf.SHT_RELA ? r.u64(off + 16) : 0;
                    rel.symbolIndex = (int) (info >>> 32);
                    rel.type = (int) (info & 0xffffffffL);
                } else {
                    rel.offset = r.u32(off);
                    info = r.u32(off + 4);
                    rel.addend = s.type == Elf.SHT_RELA ? (long) r.u32(off + 8) : 0;
                    rel.symbolIndex = (int) (info >>> 8);
                    rel.type = (int) (info & 0xff);
                }
                rel.typeName = relocName(rel.type);
                rel.copyRelocation = rel.type == Elf.R_X86_64_COPY;
                ElfSymbol target = byIndex.get(rel.symbolIndex);
                rel.symbolName = target == null ? "" : target.name;
                o.relocations.add(rel);
            }
        }
    }

    private String relocName(int type) {
        return switch (type) {
            case Elf.R_X86_64_64 -> "R_X86_64_64";
            case Elf.R_X86_64_PC32 -> "R_X86_64_PC32";
            case Elf.R_X86_64_32 -> "R_X86_64_32";
            case Elf.R_X86_64_COPY -> "R_X86_64_COPY";
            case Elf.R_X86_64_GLOB_DAT -> "R_X86_64_GLOB_DAT";
            case Elf.R_X86_64_JUMP_SLOT -> "R_X86_64_JUMP_SLOT";
            case Elf.R_X86_64_RELATIVE -> "R_X86_64_RELATIVE";
            default -> "R_" + type;
        };
    }

    private void parseVerdef(BinaryReader r, ParsedObject o, List<Section> sections) {
        Section vd = find(sections, Elf.SHT_GNU_verdef);
        if (vd == null) return;
        Section str = vd.link < sections.size() ? sections.get(vd.link) : null;
        if (str == null) return;
        long off = vd.offset;
        long end = vd.offset + vd.size;
        while (off + 20 <= end) {
            long base = off;
            int version = r.u16(base);
            int flags = r.u16(base + 2);
            int ndx = r.u16(base + 4);
            int cnt = r.u16(base + 6);
            long aux = r.u32(base + 12);
            long next = r.u32(base + 16);
            VersionDef def = new VersionDef();
            def.index = ndx;
            def.flags = flags;
            def.base = (flags & Elf.VER_FLG_BASE) != 0;
            long aoff = base + aux;
            for (int i = 0; i < cnt && aux != 0; i++) {
                long nameOff = r.u32(aoff);
                long anext = r.u32(aoff + 4);
                String vn = r.cstr(nameOff, str.offset, str.size);
                if (i == 0) def.name = vn; else def.aliases.add(vn);
                if (anext == 0) break;
                aoff += anext;
            }
            if (def.name == null) def.name = "";
            o.versionDefs.add(def);
            if (next == 0) break;
            off += next;
        }
    }

    private void parseVerneed(BinaryReader r, ParsedObject o, List<Section> sections) {
        Section vn = find(sections, Elf.SHT_GNU_verneed);
        if (vn == null) return;
        Section str = vn.link < sections.size() ? sections.get(vn.link) : null;
        if (str == null) return;
        int needEnt = vn.entsize == 0 ? 16 : (int) vn.entsize;
        long off = vn.offset;
        long end = vn.offset + vn.size;
        while (off + needEnt <= end) {
            long base = off;
            long aux = r.u32(base + 8);
            long next = r.u32(base + 12);
            String file = r.cstr(r.u32(base + 4), str.offset, str.size);
            int cnt = (int) r.u16(base + 2);
            long aoff = base + aux;
            for (int i = 0; i < cnt && aux != 0; i++) {
                int flags = (int) r.u16(aoff + 4);
                int ndx = r.u16(aoff + 6);
                long nameOff = r.u32(aoff + 8);
                long anext = r.u32(aoff + 12);
                VersionNeed need = new VersionNeed();
                need.index = ndx;
                need.flags = flags;
                need.file = file;
                need.weak = (flags & Elf.VER_FLG_WEAK) != 0;
                need.name = r.cstr(nameOff, str.offset, str.size);
                o.versionNeeds.add(need);
                if (anext == 0) break;
                aoff += anext;
            }
            if (next == 0) break;
            off += next;
        }
    }

    private void applyVersions(ParsedObject o, BinaryReader r, List<Section> sections) {
        Section vs = find(sections, Elf.SHT_GNU_versym);
        if (vs == null) return;
        Map<Integer, String> defById = new HashMap<>();
        Map<Integer, VersionDef> defObj = new HashMap<>();
        for (VersionDef d : o.versionDefs) {
            defById.put(d.index, d.name);
            defObj.put(d.index, d);
        }
        Map<Integer, String> needById = new HashMap<>();
        Map<Integer, VersionNeed> needObj = new HashMap<>();
        for (VersionNeed n : o.versionNeeds) {
            needById.put(n.index, n.name);
            needObj.put(n.index, n);
        }
        long max = Math.min(vs.size / 2L, (long) o.symbols.size());
        for (int k = 0; k < max; k++) {
            int raw = r.u16(vs.offset + k * 2L);
            int ndx = raw & Elf.VERSYM_VERSION_MASK;
            boolean hidden = (raw & Elf.VERSYM_HIDDEN) != 0;
            ElfSymbol s = o.symbols.get(k);
            s.versionIndex = raw;
            s.versionHiddenFlag = hidden;
            if (ndx == Elf.VER_NDX_LOCAL) { s.versionName = null; continue; }
            if (ndx == Elf.VER_NDX_GLOBAL) { s.versionName = null; continue; }
            if (defById.containsKey(ndx)) {
                s.versionName = defById.get(ndx);
                // bit 15 clear => default (@@) version of that name
                s.versionDefault = !hidden;
                VersionDef d = defObj.get(ndx);
                if (d != null && d.base) s.versionName = d.name;
            } else if (needById.containsKey(ndx)) {
                s.versionName = needById.get(ndx);
                // needed versions record the requested version; bit marks non-default request
                s.versionDefault = !hidden;
            } else {
                s.versionName = "v" + ndx;
            }
        }
    }

    private void parseGroups(BinaryReader r, ParsedObject o, List<Section> sections) {
        for (Section s : sections) {
            if (s.type != Elf.SHT_GROUP) continue;
            ComdatGroup g = new ComdatGroup();
            g.sectionIndex = s.index;
            long flag = r.u32(s.offset);
            g.comdat = flag == 1; // GRP_COMDAT
            if (s.link >= 0 && s.link < sections.size()) {
                Section symSec = sections.get(s.link);
                Section str = symSec.link < sections.size() ? sections.get(symSec.link) : null;
                int sigSym = (int) r.u32(s.offset + 4);
                if (str != null) {
                    long ent = o.elfClass == 64 ? 24 : 16;
                    long soff = symSec.offset + sigSym * ent;
                    int nameIdx = (int) r.u32(soff);
                    g.signature = r.cstr(nameIdx, str.offset, str.size);
                }
            }
            for (long off = s.offset + 8; off + 4 <= s.offset + s.size; off += 4) {
                g.members.add((int) r.u32(off));
            }
            o.groups.add(g);
        }
    }

    private void tagComdatSymbols(ParsedObject o) {
        for (ComdatGroup g : o.groups) {
            if (!g.comdat) continue;
            for (int m : g.members) {
                if (m < 0 || m >= o.sections.size()) continue;
                for (ElfSymbol s : o.symbols) {
                    if (s.shndx == m && s.comdatGroup == null) s.comdatGroup = g.signature;
                }
            }
        }
    }

    private Section find(List<Section> sections, int type) {
        for (Section s : sections) if (s.type == type) return s;
        return null;
    }
}
