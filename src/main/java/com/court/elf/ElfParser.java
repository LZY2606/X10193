package com.court.elf;

import com.court.model.ElfFile;
import com.court.model.ElfSection;
import com.court.model.ElfSymbol;
import com.court.model.Relocation;
import com.court.model.SectionGroup;
import com.court.model.VersionDef;
import com.court.model.VersionReq;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A minimal, purely-in-Java ELF reader used by the symbol court.
 * It supports ELFCLASS32/64 and both endiannesses, and parses:
 * section headers, symtab + dynsym, .gnu.version(.d/.r), SHT_GROUP,
 * SHT_REL/SHT_RELA, and the dynamic section (NEEDED/SONAME).
 * Every offset and index is validated; malformed input raises ElfParseException
 * so that only that single object is quarantined.
 */
public class ElfParser {

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_RELA = 4;
    private static final int SHT_HASH = 5;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_NOTE = 7;
    private static final int SHT_REL = 9;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GROUP = 17;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;

    private static final int DT_NEEDED = 1;
    private static final int DT_STRTAB = 5;
    private static final int DT_SONAME = 14;
    private static final int DT_VERSYM = 0x6ffffff0;
    private static final int DT_VERDEF = 0x6ffffffc;
    private static final int DT_VERDEFNUM = 0x6ffffffd;
    private static final int DT_VERNEED = 0x6ffffffe;
    private static final int DT_VERNEEDNUM = 0x6fffffff;
    private static final int DT_NULL = 0;

    private static final int VER_FLG_BASE = 0x1;
    private static final int VER_FLG_WEAK = 0x2;

    private final byte[] data;
    private final ByteReader r;
    private final boolean elf64;
    private final boolean bigEndian;

    public ElfParser(byte[] data) throws ElfParseException {
        this.data = data;
        if (data.length < 16) {
            throw new ElfParseException("file too small for an ELF header: " + data.length + " bytes");
        }
        if (data[0] != 0x7f || data[1] == 0 || data[2] == 0 || data[3] == 0) {
            throw new ElfParseException("bad ELF magic");
        }
        if (data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfParseException("bad ELF magic");
        }
        int elfClass = data[4] & 0xff;
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfParseException("unsupported EI_CLASS: " + elfClass);
        }
        this.elf64 = elfClass == 2;
        int enc = data[5] & 0xff;
        if (enc != 1 && enc != 2) {
            throw new ElfParseException("unsupported EI_DATA: " + enc);
        }
        this.bigEndian = enc == 2;
        this.r = new ByteReader(data, bigEndian);
    }

    public static ElfFile parse(byte[] bytes, String path, String member) throws ElfParseException {
        return new ElfParser(bytes).doParse(path, member);
    }

    private ElfFile doParse(String path, String member) throws ElfParseException {
        ElfFile f = new ElfFile(path, member);

        int type = r.u16(elf64 ? 0x10 : 0x10);
        f.setMachine(r.u16(0x12));
        f.setEntry(elf64 ? r.u64(0x18) : r.u32(0x18));
        f.setRelocatable(type == ET_REL);
        f.setExecutable(type == ET_EXEC);
        f.setShared(type == ET_DYN);
        if (type != ET_REL && type != ET_EXEC && type != ET_DYN) {
            throw new ElfParseException("unsupported e_type: " + type);
        }

        long shoff = elf64 ? r.u64(0x28) : r.u32(0x20);
        int shentsize = r.u16(elf64 ? 0x3a : 0x2e);
        int shnum = r.u16(elf64 ? 0x3c : 0x30);
        int shstrndx = r.u16(elf64 ? 0x3e : 0x32);

        if (shoff == 0 || shnum == 0) {
            throw new ElfParseException("no section headers (stripped core tables unsupported)");
        }
        int minEnt = elf64 ? 64 : 40;
        if (shentsize < minEnt) {
            throw new ElfParseException("bad e_shentsize: " + shentsize);
        }
        r.require(shoff, (long) shentsize * shnum);
        if (shnum == 0) {
            throw new ElfParseException("section count zero");
        }

        // Parse raw section headers.
        for (int i = 0; i < shnum; i++) {
            long base = shoff + (long) i * shentsize;
            ElfSection s = readSection(base, i);
            f.getSections().add(s);
        }

        // Extended numbering: shnum==0 / shstrndx==SHN_XINDEX use section 0 fields.
        int realShnum = shnum;
        int realShstr = shstrndx;
        ElfSection sec0 = f.getSections().get(0);
        if (shnum == 0) {
            if (f.getSections().isEmpty()) throw new ElfParseException("extended shnum unavailable");
            realShnum = (int) sec0.size();
            if (realShnum <= 0) throw new ElfParseException("bad extended shnum");
        }
        if (shstrndx == 0xffff) {
            realShstr = sec0.link();
        }

        if (realShstr >= 0 && realShstr < f.getSections().size()) {
            ElfSection strSec = f.getSections().get(realShstr);
            byte[] names = sectionBytes(strSec);
            for (int i = 0; i < f.getSections().size(); i++) {
                ElfSection current = f.getSections().get(i);
                int nameOff;
                try {
                    nameOff = Integer.parseInt(current.name());
                } catch (NumberFormatException ex) {
                    continue;
                }
                String nm = nameFromTable(names, nameOff);
                f.getSections().set(i, new ElfSection(i, nm, current.type(), current.flags(),
                        current.addr(), current.offset(), current.size(), current.link(),
                        current.info(), current.addralign(), current.entsize()));
            }
        }

        parseSymbols(f);
        parseGroups(f);
        parseRelocations(f);
        parseDynamic(f);
        parseVersionSections(f);

        return f;
    }

    private ElfSection readSection(long base, int index) throws ElfParseException {
        int nameOff = r.u16(base);
        int type = (int) r.u32(base + 4);
        long flags = elf64 ? r.u64(base + 8) : r.u32(base + 8);
        long addr = elf64 ? r.u64(base + 16) : r.u32(base + 12);
        long off = elf64 ? r.u64(base + 24) : r.u32(base + 16);
        long size = elf64 ? r.u64(base + 32) : r.u32(base + 20);
        int link = (int) r.u32(base + (elf64 ? 40 : 24));
        int info = (int) r.u32(base + (elf64 ? 44 : 28));
        long align = elf64 ? r.u64(base + 48) : r.u32(base + 32);
        long entsize = elf64 ? r.u64(base + 56) : r.u32(base + 36);
        return new ElfSection(index, String.valueOf(nameOff), type, flags, addr, off, size,
                link, info, align, entsize);
    }

    private byte[] sectionBytes(ElfSection s) throws ElfParseException {
        if (s.size() == 0) return new byte[0];
        return r.bytes(s.offset(), s.size());
    }

    private String nameFromTable(byte[] table, int off) throws ElfParseException {
        if (off < 0 || off >= table.length) {
            throw new ElfParseException("section name offset out of bounds: " + off);
        }
        int end = off;
        while (end < table.length && table[end] != 0) end++;
        if (end > table.length) throw new ElfParseException("unterminated section name");
        return new String(table, off, end - off, StandardCharsets.UTF_8);
    }

    private void parseSymbols(ElfFile f) throws ElfParseException {
        for (ElfSection s : f.getSections()) {
            if (s.type() != SHT_SYMTAB && s.type() != SHT_DYNSYM) continue;
            if (s.link() < 0 || s.link() >= f.getSections().size()) {
                throw new ElfParseException(s.name() + ": invalid strtab link " + s.link());
            }
            byte[] strtab = sectionBytes(f.getSections().get(s.link()));
            long ent = s.entsize() > 0 ? s.entsize() : (elf64 ? 24 : 16);
            if (ent < (elf64 ? 24 : 16)) {
                throw new ElfParseException(s.name() + ": bad entsize " + ent);
            }
            if (s.size() % ent != 0) {
                throw new ElfParseException(s.name() + ": size " + s.size()
                        + " not a multiple of entsize " + ent);
            }
            long count = s.size() / ent;
            for (int idx = 0; idx < count; idx++) {
                long base = s.offset() + idx * ent;
                int nameOff = (int) r.u32(base);
                long value, size;
                int info, other, shndx;
                if (elf64) {
                    info = r.u8(base + 4);
                    other = r.u8(base + 5);
                    shndx = r.u16(base + 6);
                    value = r.u64(base + 8);
                    size = r.u64(base + 16);
                } else {
                    value = r.u32(base + 4);
                    size = r.u32(base + 8);
                    info = r.u8(base + 12);
                    other = r.u8(base + 13);
                    shndx = r.u16(base + 14);
                }
                String nm = nameFromTable(strtab, nameOff);
                int bind = info >> 4;
                int type = info & 0xf;
                int vis = other & 0x3;
                ElfSymbol sym = new ElfSymbol(idx, nameOff, nm, bind, type, vis, shndx, value, size);
                if (s.type() == SHT_DYNSYM) {
                    f.getDynamicSymbols().add(sym);
                } else {
                    f.getStaticSymbols().add(sym);
                }
            }
        }
    }

    private void parseGroups(ElfFile f) throws ElfParseException {
        for (ElfSection s : f.getSections()) {
            if (s.type() != SHT_GROUP) continue;
            if (s.size() < 4) throw new ElfParseException(s.name() + ": empty SHT_GROUP");
            long flag = r.u32(s.offset());
            boolean comdat = (flag & SectionGroup.GRP_COMDAT) != 0;
            if (s.link() < 0 || s.link() >= f.getSections().size()) {
                throw new ElfParseException(s.name() + ": invalid group symtab link");
            }
            long members = (s.size() - 4) / 4;
            java.util.List<Integer> idxs = new java.util.ArrayList<>();
            for (int i = 0; i < members; i++) {
                int secIdx = (int) r.u32(s.offset() + 4 + i * 4L);
                if (secIdx < 0 || secIdx >= f.getSections().size()) {
                    throw new ElfParseException(s.name() + ": group member section out of range");
                }
                idxs.add(secIdx);
            }
            String sig = groupSignature(f, s);
            f.getGroups().add(new SectionGroup(sig, comdat, idxs));
        }
    }

    private String groupSignature(ElfFile f, ElfSection group) throws ElfParseException {
        int symIdx = group.info();
        ElfSection symtabSec = f.getSections().get(group.link());
        java.util.List<ElfSymbol> syms =
                symtabSec.type() == SHT_DYNSYM ? f.getDynamicSymbols() : f.getStaticSymbols();
        if (symIdx < 0 || symIdx >= syms.size()) {
            throw new ElfParseException(group.name() + ": group signature symbol out of range");
        }
        return syms.get(symIdx).getName();
    }

    private void parseRelocations(ElfFile f) throws ElfParseException {
        for (ElfSection s : f.getSections()) {
            boolean rela = s.type() == SHT_RELA;
            if (!rela && s.type() != SHT_REL) continue;
            if (s.link() < 0 || s.link() >= f.getSections().size()) {
                throw new ElfParseException(s.name() + ": invalid relocation symtab link");
            }
            ElfSection linkedSymtab = f.getSections().get(s.link());
            java.util.List<ElfSymbol> syms =
                    linkedSymtab.type() == SHT_DYNSYM ? f.getDynamicSymbols() : f.getStaticSymbols();
            long ent = s.entsize() > 0 ? s.entsize() : (elf64 ? (rela ? 24 : 16)
                                                               : (rela ? 12 : 8));
            if (s.size() % ent != 0) {
                throw new ElfParseException(s.name() + ": relocation size not a multiple of entsize");
            }
            long count = s.size() / ent;
            for (int i = 0; i < count; i++) {
                long base = s.offset() + i * ent;
                long offset = elf64 ? r.u64(base) : r.u32(base);
                long packedInfo = elf64 ? r.u64(base + 8) : r.u32(base + 4);
                int symIdx = (int) (elf64 ? packedInfo >>> 32 : packedInfo >> 8);
                int rtype = (int) (elf64 ? packedInfo & 0xffffffffL : packedInfo & 0xff);
                Long addend = null;
                if (rela) {
                    addend = elf64 ? r.u64(base + 16) : (long) r.u32(base + 8);
                }
                if (symIdx < 0 || symIdx >= syms.size()) {
                    throw new ElfParseException(s.name() + ": relocation symbol index "
                            + symIdx + " out of range [0," + syms.size() + ")");
                }
                ElfSymbol sym = syms.get(symIdx);
                boolean dyn = s.allocated() || linkedSymtab.type() == SHT_DYNSYM;
                f.getRelocations().add(new Relocation(s.info(), offset, symIdx,
                        sym.getName(), rtype, addend, rela, dyn));
            }
        }
    }

    private void parseDynamic(ElfFile f) throws ElfParseException {
        ElfSection dyn = findSection(f, SHT_DYNAMIC);
        if (dyn == null) return;
        if (dyn.link() < 0 || dyn.link() >= f.getSections().size()) {
            throw new ElfParseException(".dynamic: invalid strtab link");
        }
        byte[] strtab = sectionBytes(f.getSections().get(dyn.link()));
        long ent = dyn.entsize() > 0 ? dyn.entsize() : (elf64 ? 16 : 8);
        if (dyn.size() % ent != 0) {
            throw new ElfParseException(".dynamic size not a multiple of entsize");
        }
        long count = dyn.size() / ent;
        for (int i = 0; i < count; i++) {
            long base = dyn.offset() + i * ent;
            long tag = elf64 ? r.u64(base) : r.u32(base);
            long val = elf64 ? r.u64(base + 8) : r.u32(base + 4);
            if (tag == DT_NULL) break;
            if (tag == DT_NEEDED || tag == DT_SONAME) {
                String nm = nameFromTable(strtab, (int) val);
                if (tag == DT_NEEDED) {
                    f.getNeeded().add(nm);
                } else {
                    f.setSoname(nm);
                }
            }
        }
    }

    private ElfSection findSection(ElfFile f, int type) {
        for (ElfSection s : f.getSections()) {
            if (s.type() == type) return s;
        }
        return null;
    }

    private void parseVersionSections(ElfFile f) throws ElfParseException {
        ElfSection versym = null;
        ElfSection verdef = null;
        ElfSection verneed = null;
        for (ElfSection s : f.getSections()) {
            if (s.type() == SHT_GNU_VERSYM) versym = s;
            if (s.type() == SHT_GNU_VERDEF) verdef = s;
            if (s.type() == SHT_GNU_VERNEED) verneed = s;
        }
        if (verdef != null) readVerdef(f, verdef);
        if (verneed != null) readVerneed(f, verneed);
        if (versym != null) readVersym(f, versym);
    }

    private void readVersym(ElfFile f, ElfSection s) throws ElfParseException {
        if (s.size() % 2 != 0) throw new ElfParseException(".gnu.version bad size");
        long count = s.size() / 2;
        for (int i = 0; i < count; i++) {
            f.getDynVersym().add(r.u16(s.offset() + i * 2L));
        }
    }

    private void readVerdef(ElfFile f, ElfSection s) throws ElfParseException {
        byte[] strtab = sectionBytes(f.getSections().get(s.link()));
        long end = s.offset() + s.size();
        long off = s.offset();
        int guard = 0;
        while (off < end && guard++ < 65536) {
            if (off + 24 > end) {
                throw new ElfParseException(".gnu.version_d truncated entry");
            }
            int version = r.u16(off);
            if (version != 1) throw new ElfParseException(".gnu.version_d bad version " + version);
            int vdFlags = r.u16(off + 2);
            int vdNdx = (short) r.u16(off + 4);
            int vdCnt = r.u16(off + 6);
            long vdAux = r.u32(off + 12);
            long vdNext = r.u32(off + 16);
            boolean base = (vdFlags & VER_FLG_BASE) != 0;
            String vname = "";
            if (vdAux != 0 && vdCnt > 0) {
                long cur = off + vdAux;
                if (cur + 8 > end) throw new ElfParseException(".gnu.version_d truncated aux");
                int nameOff = (int) r.u32(cur);
                vname = nameFromTable(strtab, nameOff);
                f.getVersionDefs().add(new VersionDef(vdNdx, vname, false, base));
                // Additional verdaux names are non-default (hidden) aliases of the same index.
                long aNext = r.u32(cur + 4);
                int seen = 1;
                while (aNext != 0 && seen < vdCnt) {
                    cur += aNext;
                    if (cur + 8 > end) throw new ElfParseException(".gnu.version_d truncated aux chain");
                    int aliasOff = (int) r.u32(cur);
                    String alias = nameFromTable(strtab, aliasOff);
                    f.getVersionDefs().add(new VersionDef(vdNdx, alias, true, base));
                    seen++;
                    aNext = r.u32(cur + 4);
                }
            }
            if (vdNext == 0) break;
            off += vdNext;
        }
    }

    private void readVerneed(ElfFile f, ElfSection s) throws ElfParseException {
        byte[] strtab = sectionBytes(f.getSections().get(s.link()));
        long end = s.offset() + s.size();
        long vnOff = s.offset();
        int guard = 0;
        while (vnOff < end && guard++ < 65536) {
            if (vnOff + 16 > end) throw new ElfParseException(".gnu.version_r truncated entry");
            int version = r.u16(vnOff);
            if (version != 1) throw new ElfParseException(".gnu.version_r bad version " + version);
            int vnCnt = r.u16(vnOff + 2);
            int fileOff = (int) r.u32(vnOff + 4);
            long vnAux = r.u32(vnOff + 8);
            long vnNext = r.u32(vnOff + 12);
            String fileName = nameFromTable(strtab, fileOff);
            long cur = vnOff + vnAux;
            for (int k = 0; k < vnCnt; k++) {
                if (cur + 16 > end) throw new ElfParseException(".gnu.version_r truncated aux");
                int hash = (int) r.u32(cur);
                int flags = r.u16(cur + 4);
                int other = r.u16(cur + 6);
                int nameOff = (int) r.u32(cur + 8);
                long next = r.u32(cur + 12);
                String vname = nameFromTable(strtab, nameOff);
                f.getVersionReqs().add(new VersionReq(fileName, vname,
                        (flags & VER_FLG_WEAK) != 0, other));
                if (next == 0) break;
                cur += next;
            }
            if (vnNext == 0) break;
            vnOff += vnNext;
        }
    }
}
