package com.example.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/**
 * Pure-Java ELF + ar(5) archive parser. No system linker involvement.
 * All multi-byte reads are bounds checked; malformed objects throw ElfParseException
 * which the engine isolates to the single offending object.
 */
public final class ElfParser {

    private static final long MAX_SECTIONS = 1L << 20;
    private static final long MAX_SYMBOLS = 1L << 24;

    private ElfParser() {
    }

    public static ElfFile parse(String name, byte[] data) {
        if (data == null || data.length < 8) {
            throw new ElfParseException(name + ": file too small");
        }
        if (data[0] == '!' && data[1] == '<' && data.length > 7) {
            return ArArchive.parse(name, data);
        }
        if ((data[0] & 0xff) != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfParseException(name + ": bad ELF magic");
        }
        ElfFile f = new ElfFile();
        f.name = name;
        f.archive = false;
        f.elfClass = data[4] & 0xff;
        f.endian = data[5] & 0xff;
        if (f.elfClass != 1 && f.elfClass != 2) {
            throw new ElfParseException(name + ": unsupported ELF class " + f.elfClass);
        }
        if (f.endian != 1 && f.endian != 2) {
            throw new ElfParseException(name + ": unsupported endianness " + f.endian);
        }
        boolean le = f.endian == 1;
        ElfReader r = new ElfReader(data, le);
        f.elfType = r.u16(16);
        f.machine = r.u16(18);
        boolean is64 = f.elfClass == 2;
        long shoff = is64 ? r.u64(40) : r.u32(32);
        int shentsize = r.u16(is64 ? 58 : 46);
        long shnum = r.u16(is64 ? 60 : 48) & 0xffffL;
        long shstrndx = r.u16(is64 ? 62 : 50) & 0xffffL;
        if (shoff == 0) {
            throw new ElfParseException(name + ": no section headers");
        }
        if (shnum == 0) {
            if (shoff >= data.length) {
                throw new ElfParseException(name + ": section header table out of bounds");
            }
            shnum = is64 ? r.u64(shoff + 32) : r.u32(shoff + 20);
            if (shstrndx == 0xffffL) {
                shstrndx = is64 ? r.u64(shoff + 40) : r.u32(shoff + 24);
            }
        }
        if (shnum > MAX_SECTIONS) {
            throw new ElfParseException(name + ": implausible section count " + shnum);
        }
        long shend = (long) shentsize * shnum;
        r.u8(shoff + shend - 1);
        for (int i = 0; i < shnum; i++) {
            long o = shoff + (long) shentsize * i;
            ElfFile.Section s = new ElfFile.Section();
            s.index = i;
            if (is64) {
                s.name = r.u32(o);
                s.type = r.u32(o + 4);
                s.flags = r.u64(o + 8);
                s.addr = r.u64(o + 16);
                s.offset = r.u64(o + 24);
                s.size = r.u64(o + 32);
                s.link = r.u32(o + 40);
                s.info = r.u32(o + 44);
                s.addralign = r.u64(o + 48);
                s.entsize = r.u64(o + 56);
            } else {
                s.name = r.u32(o);
                s.type = r.u32(o + 4);
                s.flags = r.u32(o + 8);
                s.addr = r.u32(o + 12);
                s.offset = r.u32(o + 16);
                s.size = r.u32(o + 20);
                s.link = r.u32(o + 24);
                s.info = r.u32(o + 28);
                s.addralign = r.u32(o + 32);
                s.entsize = r.u32(o + 36);
            }
            f.sections.add(s);
        }
        resolveSectionNames(f, r, shstrndx);
        parseSymbols(f, r, is64);
        parseGroups(f, r);
        parseVersionInfo(f, r, is64);
        parseRelocations(f, r, is64);
        parseDynamic(f, r, is64);
        return f;
    }

    private static void resolveSectionNames(ElfFile f, ElfReader r, long shstrndx) {
        if (shstrndx == 0 || shstrndx >= f.sections.size()) {
            return;
        }
        ElfFile.Section str = f.sections.get((int) shstrndx);
        long limit = str.offset + str.size;
        if (limit > r.limit()) {
            limit = r.limit();
        }
        for (ElfFile.Section s : f.sections) {
            if (s.name == 0) {
                s.name = "";
                continue;
            }
            if (str.offset + s.name >= limit) {
                f.warnings.add("section " + s.index + ": name out of bounds");
                s.name = "";
                continue;
            }
            s.name = r.cstr(str.offset + s.name, limit);
        }
    }

    private static String strAt(ElfFile f, ElfReader r, ElfFile.Section strtab, long off) {
        if (strtab == null || off >= strtab.size) {
            f.warnings.add("string index " + off + " out of string table bounds");
            return "";
        }
        return r.cstr(strtab.offset + off, strtab.offset + strtab.size);
    }

    private static void parseSymbols(ElfFile f, ElfReader r, boolean is64) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != ElfFile.SHT_SYMTAB && s.type != ElfFile.SHT_DYNSYM) {
                continue;
            }
            boolean dynamic = s.type == ElfFile.SHT_DYNSYM;
            int ent = is64 ? 24 : 16;
            long count = s.entsize > 0 ? s.size / s.entsize : s.size / ent;
            if (count > MAX_SYMBOLS) {
                f.warnings.add(s.name + ": implausible symbol count");
                count = 0;
            }
            ElfFile.Section strtab = s.link < f.sections.size() ? f.sections.get((int) s.link) : null;
            long end = s.offset + (long) ent * count;
            if (end > r.limit()) {
                long fits = (r.limit() - s.offset) / ent;
                f.warnings.add(s.name + ": truncated symbol table (" + fits + "/" + count + ")");
                count = Math.max(0, fits);
            }
            for (int i = 0; i < count; i++) {
                long o = s.offset + (long) ent * i;
                ElfFile.Sym sym = new ElfFile.Sym();
                sym.index = i;
                sym.dynamic = dynamic;
                if (is64) {
                    sym.name = strAt(f, r, strtab, r.u32(o));
                    sym.info = r.u8(o + 4);
                    sym.other = r.u8(o + 5);
                    sym.shndx = r.u16(o + 6);
                    sym.value = r.u64(o + 8);
                    sym.size = r.u64(o + 16);
                } else {
                    sym.name = strAt(f, r, strtab, r.u32(o));
                    sym.value = r.u32(o + 4);
                    sym.size = r.u32(o + 8);
                    sym.info = r.u8(o + 12);
                    sym.other = r.u8(o + 13);
                    sym.shndx = r.u16(o + 14);
                }
                if (dynamic) {
                    f.dynsym.add(sym);
                } else {
                    f.symtab.add(sym);
                }
            }
        }
    }
}

    private static void parseGroups(ElfFile f, ElfReader r) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != ElfFile.SHT_GROUP) {
                continue;
            }
            ElfFile.Group g = new ElfFile.Group();
            g.sectionIndex = s.index;
            g.flags = r.u32(s.offset);
            long n = s.size / 4;
            if (n > MAX_SECTIONS) {
                f.warnings.add("group " + s.index + ": implausible member count");
                n = 0;
            }
            for (int i = 1; i < n; i++) {
                long idx = r.u32(s.offset + i * 4L);
                if (idx < 0 || idx >= f.sections.size()) {
                    f.warnings.add("group " + s.index + ": member index out of range");
                    continue;
                }
                g.memberSections.add((int) idx);
            }
            ElfFile.Section symtab = s.link < f.sections.size() ? f.sections.get((int) s.link) : null;
            long sigSym = s.info;
            if (symtab != null && sigSym < (symtab.type == ElfFile.SHT_DYNSYM ? f.dynsym.size() : f.symtab.size())) {
                java.util.List<ElfFile.Sym> pool =
                        symtab.type == ElfFile.SHT_DYNSYM ? f.dynsym : f.symtab;
                g.signature = pool.get((int) sigSym).name;
            }
            f.groups.add(g);
        }
    }

    private static void parseRelocations(ElfFile f, ElfReader r, boolean is64) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != ElfFile.SHT_RELA && s.type != ElfFile.SHT_REL) {
                continue;
            }
            boolean rela = s.type == ElfFile.SHT_RELA;
            int ent = is64 ? (rela ? 24 : 16) : (rela ? 12 : 8);
            long count = s.entsize > 0 ? s.size / s.entsize : s.size / ent;
            if (count > MAX_SYMBOLS) {
                f.warnings.add(s.name + ": implausible reloc count");
                count = 0;
            }
            long end = s.offset + (long) ent * count;
            if (end > r.limit()) {
                long fits = (r.limit() - s.offset) / ent;
                f.warnings.add(s.name + ": truncated relocation section");
                count = Math.max(0, fits);
            }
            String targetName = s.info < f.sections.size() ? f.sections.get((int) s.info).name : "";
            for (int i = 0; i < count; i++) {
                long o = s.offset + (long) ent * i;
                ElfFile.Reloc rel = new ElfFile.Reloc();
                rel.relocSectionIndex = s.index;
                rel.sectionIndex = (int) s.info;
                rel.sectionName = targetName;
                rel.rela = rela;
                if (is64) {
                    rel.offset = r.u64(o);
                    long info = r.u64(o + 8);
                    rel.symIndex = (int) (info >>> 32);
                    rel.type = info & 0xffffffffL;
                    if (rela) {
                        rel.addend = r.u64(o + 16);
                    }
                } else {
                    rel.offset = r.u32(o);
                    long info = r.u32(o + 4);
                    rel.symIndex = (int) (info >>> 8);
                    rel.type = info & 0xffL;
                    if (rela) {
                        rel.addend = r.u32(o + 8);
                    }
                }
                f.relocs.add(rel);
            }
        }
    }

    private static void parseDynamic(ElfFile f, ElfReader r, boolean is64) {
        int ent = is64 ? 16 : 8;
        for (ElfFile.Section s : f.sections) {
            if (s.type != ElfFile.SHT_DYNAMIC) {
                continue;
            }
            ElfFile.Section strtab = s.link < f.sections.size() ? f.sections.get((int) s.link) : null;
            long count = s.entsize > 0 ? s.size / s.entsize : s.size / ent;
            if (count > MAX_SYMBOLS) {
                f.warnings.add(".dynamic: implausible entry count");
                continue;
            }
            for (int i = 0; i < count; i++) {
                long o = s.offset + (long) ent * i;
                if (o + ent > r.limit()) {
                    f.warnings.add(".dynamic truncated");
                    break;
                }
                long tag = is64 ? r.u64(o) : r.u32(o);
                long val = is64 ? r.u64(o + 8) : r.u32(o + 4);
                if (tag == 0) {
                    break; // DT_NULL
                }
                if (tag == 1) { // DT_NEEDED
                    f.needed.add(strAt(f, r, strtab, val));
                } else if (tag == 14) { // DT_SONAME
                    f.soname = strAt(f, r, strtab, val);
                }
            }
        }
    }

    private static void parseVersionInfo(ElfFile f, ElfReader r, boolean is64) {
        ElfFile.Section versymSec = null;
        ElfFile.Section verdefSec = null;
        ElfFile.Section verneedSec = null;
        for (ElfFile.Section s : f.sections) {
            if ((s.type & 0xffffffffL) == ElfFile.SHT_GNU_VERSYM) {
                versymSec = s;
            } else if ((s.type & 0xffffffffL) == ElfFile.SHT_GNU_VERDEF) {
                verdefSec = s;
            } else if ((s.type & 0xffffffffL) == ElfFile.SHT_GNU_VERNEED) {
                verneedSec = s;
            }
        }
        if (verdefSec != null) {
            ElfFile.Section strtab = verdefSec.link < f.sections.size()
                    ? f.sections.get((int) verdefSec.link) : null;
            long base = verdefSec.offset;
            long limit = base + verdefSec.size;
            long p = base;
            int guard = 0;
            while (p + 20 <= limit && guard++ < MAX_SECTIONS) {
                long ndx = r.u16(p + 4) & 0xffffL;
                long cnt = r.u16(p + 6) & 0xffffL;
                long auxOff = r.u32(p + 12);
                long next = r.u32(p + 16);
                ElfFile.VerDef vd = new ElfFile.VerDef();
                vd.ndx = (int) ndx;
                vd.count = (int) cnt;
                long ap = p + auxOff;
                if (auxOff != 0 && ap + 8 <= limit) {
                    long nameOff = r.u32(ap);
                    vd.name = strAt(f, r, strtab, nameOff);
                }
                if (vd.ndx >= 2) {
                    f.verDefs.add(vd);
                }
                if (next == 0) {
                    break;
                }
                p += next;
                if (p <= base) {
                    f.warnings.add("verdef chain does not advance");
                    break;
                }
            }
        }
        if (verneedSec != null) {
            ElfFile.Section strtab = verneedSec.link < f.sections.size()
                    ? f.sections.get((int) verneedSec.link) : null;
            long base = verneedSec.offset;
            long limit = base + verneedSec.size;
            long p = base;
            int guard = 0;
            while (p + 16 <= limit && guard++ < MAX_SECTIONS) {
                long cnt = r.u16(p + 2) & 0xffffL;
                long fileOff = r.u32(p + 4);
                long auxOff = r.u32(p + 8);
                long next = r.u32(p + 12);
                ElfFile.VerNeed vn = new ElfFile.VerNeed();
                vn.file = strAt(f, r, strtab, fileOff);
                long ap = p + auxOff;
                int aguard = 0;
                for (int k = 0; k < cnt && ap + 16 <= limit && aguard++ < MAX_SECTIONS; k++) {
                    long other = r.u16(ap + 6) & 0xffffL;
                    long nameOff = r.u32(ap + 8);
                    long anext = r.u32(ap + 12);
                    ElfFile.VerNeedAux aux = new ElfFile.VerNeedAux();
                    aux.other = (int) other;
                    aux.name = strAt(f, r, strtab, nameOff);
                    vn.aux.add(aux);
                    if (anext == 0) {
                        break;
                    }
                    ap += anext;
                }
                f.verNeeds.add(vn);
                if (next == 0) {
                    break;
                }
                p += next;
                if (p <= base) {
                    f.warnings.add("verneed chain does not advance");
                    break;
                }
            }
        }
        if (versymSec != null && !f.dynsym.isEmpty()) {
            long count = versymSec.size / 2;
            long max = f.dynsym.size();
            if (count < max) {
                f.warnings.add(".gnu.version shorter than .dynsym");
            }
            f.versym = new int[(int) Math.min(count, max)];
            long end = versymSec.offset + f.versym.length * 2L;
            if (end > r.limit()) {
                f.warnings.add(".gnu.version truncated");
                int fits = (int) Math.max(0, (r.limit() - versymSec.offset) / 2);
                f.versym = new int[fits];
            }
            for (int i = 0; i < f.versym.length; i++) {
                f.versym[i] = r.u16(versymSec.offset + i * 2L);
            }
        }
    }

    static ElfFile parseMember(String displayName, byte[] data) {
        try {
            return parse(displayName, data);
        } catch (ElfParseException e) {
            ElfFile bad = new ElfFile();
            bad.name = displayName;
            bad.parseError = e.getMessage();
            return bad;
        }
    }
}
