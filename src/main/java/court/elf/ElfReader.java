package court.elf;

import java.util.ArrayList;
import java.util.List;

import static court.elf.ElfConst.*;

/**
 * Pure-Java ELF parser. Every field is read through {@link R} bounds checks; any
 * structural failure raises {@link ElfException}, which the caller treats as
 * isolation of that one object only.
 */
public final class ElfReader {

    public ElfObject parse(byte[] data) throws ElfException {
        if (data == null || data.length < 16) {
            throw new ElfException("file too small for ELF ident");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfException("bad ELF magic");
        }
        int eiClass = data[4] & 0xff;
        if (eiClass != 1 && eiClass != 2) {
            throw new ElfException("bad EI_CLASS " + eiClass);
        }
        int eiData = data[5] & 0xff;
        if (eiData != 1 && eiData != 2) {
            throw new ElfException("bad EI_DATA " + eiData);
        }
        boolean elf64 = eiClass == 2;
        boolean le = eiData == 1;
        R r = new R(data, le, elf64);

        ElfObject o = new ElfObject();
        o.raw = data;
        o.elf64 = elf64;
        o.littleEndian = le;
        o.elfClass = eiClass;
        o.dataEncoding = eiData;
        o.type = r.u16(16);
        o.typeName = typeName(o.type);
        o.machine = r.u16(18);
        o.machineName = machineName(o.machine);
        o.entry = elf64 ? r.u64(24) : r.u32(24);

        int shenoff, shentsize, shnum, shstrndx;
        if (elf64) {
            shenoff = (int) r.u64(40);
            shentsize = r.u16(58);
            shnum = r.u16(60);
            shstrndx = r.u16(62);
        } else {
            shenoff = (int) r.u32(32);
            shentsize = r.u16(46);
            shnum = r.u16(48);
            shstrndx = r.u16(50);
        }
        if (shnum == 0 && shenoff != 0) {
            // Extended section header: real count lives in section 0 sh_size.
            if (shentsize < (elf64 ? 64 : 40)) {
                throw new ElfException("bad shentsize for extended header");
            }
            r.need(shenoff, shentsize);
            shnum = (int) r.word(shenoff + (elf64 ? 32 : 20));
            int realStrndx = (int) r.word(shenoff + (elf64 ? 28 : 16));
            if (realStrndx != 0) shstrndx = realStrndx;
        }
        parseSections(r, o, shenoff, shentsize, shnum, shstrndx);
        resolveSectionNames(r, o, shstrndx);
        parseDynamic(r, o);
        parseSymtabs(r, o);
        parseRelocs(r, o);
        parseGroups(r, o);
        parseVersionSections(r, o);
        return o;
    }

    private void parseSections(R r, ElfObject o, int off, int entsize, int num, int shstrndx)
            throws ElfException {
        int minEnt = r.elf64 ? 64 : 40;
        if (num < 0 || entsize < minEnt) {
            throw new ElfException("invalid section header table");
        }
        for (int i = 0; i < num; i++) {
            int base = off + (long) i * entsize > Integer.MAX_VALUE
                    ? -1 : off + i * entsize;
            if (base < 0) throw new ElfException("section header offset overflow");
            r.need(base, minEnt);
            ElfObject.Section s = new ElfObject.Section();
            s.index = i;
            if (r.elf64) {
                int nameOff = r.u16(base);
                s.type = (int) r.u32(base + 4);
                s.flags = r.u64(base + 8);
                s.addr = r.u64(base + 16);
                s.offset = r.u64(base + 24);
                s.size = r.u64(base + 32);
                s.link = (int) r.u32(base + 40);
                s.info = (int) r.u32(base + 44);
                s.addralign = r.u64(base + 48);
                s.entsize = r.u64(base + 56);
                s.name = readShName(r, o, shstrndx, nameOff);
            } else {
                int nameOff = r.u16(base);
                s.type = r.u16(base + 4);
                s.flags = r.u32(base + 8);
                s.addr = r.u32(base + 12);
                s.offset = r.u32(base + 16);
                s.size = r.u32(base + 20);
                s.link = r.u16(base + 24);
                s.info = r.u16(base + 28);
                s.addralign = r.u32(base + 32);
                s.entsize = r.u32(base + 36);
                s.name = readShName(r, o, shstrndx, nameOff);
            }
            validateSectionRange(r, s);
            o.sections.add(s);
            o.sectionByIndex.add(s);
        }
        while (o.sectionByIndex.size() < num) o.sectionByIndex.add(null);
    }

    private String readShName(R r, ElfObject o, int shstrndx, int nameOff) {
        // Deferred: section names can only be resolved after the whole table is
        // parsed because shstrtab itself is one of the sections. We approximate
        // by reading directly from the linked section's raw bytes here.
        return "sh:" + nameOff;
    }

    private void validateSectionRange(R r, ElfObject.Section s) throws ElfException {
        if (s.type == SHT_NOBITS) return;
        long end = s.offset + s.size;
        if (s.offset < 0 || s.size < 0 || end > r.d.length || end < s.offset) {
            throw new ElfException("section " + s.index + " range out of file");
        }
    }

    private void resolveSectionNames(R r, ElfObject o, int shstrndx) throws ElfException {
        if (shstrndx <= 0 || shstrndx >= o.sectionByIndex.size()) return;
        ElfObject.Section str = o.sectionByIndex.get(shstrndx);
        if (str == null || str.type == SHT_NOBITS) return;
        for (ElfObject.Section s : o.sections) {
            String raw = s.name;
            if (raw != null && raw.startsWith("sh:")) {
                int noff = Integer.parseInt(raw.substring(3));
                int base = (int) str.offset;
                int end = (int) Math.min(r.d.length, base + str.size);
                if (noff < 0 || base + (long) noff >= end) {
                    s.name = "";
                } else {
                    int p = base + noff;
                    int e = p;
                    while (e < end && r.d[e] != 0) e++;
                    s.name = new String(r.d, p, e - p, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
    }

    private void parseDynamic(R r, ElfObject o) throws ElfException {
        for (ElfObject.Section s : o.sections) {
            if (s.type != SHT_DYNAMIC) continue;
            int ws = r.wordSize();
            if (s.entsize != 0 && s.entsize < ws * 2L) continue;
            ElfObject.Section strSec = s.link > 0 && s.link < o.sectionByIndex.size()
                    ? o.sectionByIndex.get(s.link) : null;
            long count = s.size / (ws * 2L);
            for (long i = 0; i < count; i++) {
                int p = (int) (s.offset + i * ws * 2);
                long tag = r.word(p);
                long val = r.word(p + ws);
                if (tag == DT_NULL) break;
                if (tag == DT_NEEDED || tag == DT_SONAME) {
                    String name = dynString(r, strSec, (int) val);
                    if (tag == DT_NEEDED) {
                        if (name != null && !name.isEmpty()) o.needed.add(name);
                    } else {
                        o.soname = name;
                    }
                }
            }
        }
    }

    private String dynString(R r, ElfObject.Section strSec, int off) throws ElfException {
        if (strSec == null || off < 0) return null;
        long end = strSec.offset + strSec.size;
        if (strSec.offset + (long) off >= end || strSec.type == SHT_NOBITS) return null;
        return new R(r.d, r.le, r.elf64).cstr((int) strSec.offset + off);
    }

    private void parseSymtabs(R r, ElfObject o) throws ElfException {
        for (ElfObject.Section s : o.sections) {
            if (s.type != SHT_SYMTAB && s.type != SHT_DYNSYM) continue;
            List<Sym> target = s.type == SHT_DYNSYM ? o.dynSyms : o.staticSyms;
            int ws = r.wordSize();
            int ent = s.type == SHT_SYMTAB && s.entsize > 0 ? (int) s.entsize
                    : (r.elf64 ? 24 : 16);
            ElfObject.Section strSec = s.link > 0 && s.link < o.sectionByIndex.size()
                    ? o.sectionByIndex.get(s.link) : null;
            ElfObject.Section xndxSec = findXindex(o, s);
            long count = s.size / ent;
            for (long i = 0; i < count; i++) {
                int p = (int) (s.offset + i * ent);
                Sym sym = new Sym();
                sym.index = (int) i;
                sym.dynamic = s.type == SHT_DYNSYM;
                if (r.elf64) {
                    int nameOff = (int) r.u32(p);
                    sym.info = r.u8(p + 4);
                    sym.other = r.u8(p + 5);
                    sym.shndx = (short) r.u16(p + 6);
                    sym.value = r.u64(p + 8);
                    sym.size = r.u64(p + 16);
                    sym.name = symString(r, strSec, nameOff);
                } else {
                    int nameOff = (int) r.u32(p);
                    sym.value = r.u32(p + 4);
                    sym.size = r.u32(p + 8);
                    sym.info = r.u8(p + 12);
                    sym.other = r.u8(p + 13);
                    sym.shndx = (short) r.u16(p + 14);
                    sym.name = symString(r, strSec, nameOff);
                }
                sym.bind = sym.info >> 4;
                sym.type = sym.info & 0xf;
                sym.visibility = sym.other & 0x3;
                if (sym.shndx == SHN_XINDEX) {
                    int x = extendedShndx(xndxSec, r, (int) i);
                    sym.shndx = (short) x;
                }
                int si = sym.shndx & 0xffff;
                if (si > 0 && si < o.sectionByIndex.size()) {
                    sym.section = o.sectionByIndex.get(si);
                }
                target.add(sym);
            }
        }
    }

    private ElfObject.Section findXindex(ElfObject o, ElfObject.Section symSec) {
        for (ElfObject.Section cand : o.sections) {
            if (cand.type == SHT_SYMTAB_SHNDX && cand.link == symSec.index) return cand;
        }
        return null;
    }

    private int extendedShndx(ElfObject.Section x, R r, int symIdx) throws ElfException {
        if (x == null) throw new ElfException("SHN_XINDEX without SHT_SYMTAB_SHNDX");
        int off = (int) x.offset + symIdx * 4;
        return (int) r.u32(off);
    }

    private String symString(R r, ElfObject.Section strSec, int off) {
        if (strSec == null || off < 0) return "";
        long end = strSec.offset + strSec.size;
        if (strSec.type == SHT_NOBITS || strSec.offset + (long) off >= end) return "";
        try {
            return new R(r.d, r.le, r.elf64).cstr((int) strSec.offset + off);
        } catch (ElfException e) {
            return "";
        }
    }

    private void parseRelocs(R r, ElfObject o) throws ElfException {
        for (ElfObject.Section s : o.sections) {
            if (s.type != SHT_REL && s.type != SHT_RELA) continue;
            boolean rela = s.type == SHT_RELA;
            int ent = rela ? (r.elf64 ? 24 : 12) : (r.elf64 ? 16 : 8);
            int targetIdx = s.info;
            ElfObject.Section target = targetIdx < o.sectionByIndex.size()
                    ? o.sectionByIndex.get(targetIdx) : null;
            ElfObject.Section symSec = s.link > 0 && s.link < o.sectionByIndex.size()
                    ? o.sectionByIndex.get(s.link) : null;
            boolean dynamic = symSec != null && symSec.type == SHT_DYNSYM;
            List<Sym> syms = dynamic ? o.dynSyms : o.staticSyms;
            long count = s.size / ent;
            for (long i = 0; i < count; i++) {
                int p = (int) (s.offset + i * ent);
                Reloc rel = new Reloc();
                rel.rela = rela;
                rel.section = target;
                if (r.elf64) {
                    rel.offset = r.u64(p);
                    rel.info = r.u64(p + 8);
                    if (rela) rel.addend = r.u64(p + 16);
                } else {
                    rel.offset = r.u32(p);
                    rel.info = r.u32(p + 4);
                    if (rela) rel.addend = (long) (int) r.u32(p + 8);
                }
                rel.symIndex = (int) (r.elf64 ? rel.info >>> 32 : rel.info >>> 8);
                rel.type = (int) (r.elf64 ? rel.info & 0xffffffffL : rel.info & 0xff);
                rel.typeName = RelocTypes.name(o.machine, rel.type);
                rel.copyReloc = RelocTypes.isCopy(o.machine, rel.type);
                if (rel.symIndex >= 0 && rel.symIndex < syms.size()) {
                    rel.symbol = syms.get(rel.symIndex);
                }
                o.relocs.add(rel);
            }
        }
    }

    private void parseGroups(R r, ElfObject o) throws ElfException {
        for (ElfObject.Section s : o.sections) {
            if (s.type != SHT_GROUP) continue;
            ElfObject.Group g = new ElfObject.Group();
            g.flags = (int) r.u32((int) s.offset);
            ElfObject.Section symSec = s.link > 0 && s.link < o.sectionByIndex.size()
                    ? o.sectionByIndex.get(s.link) : null;
            List<Sym> syms = symSec != null && symSec.type == SHT_DYNSYM
                    ? o.dynSyms : o.staticSyms;
            int sigIdx = s.info;
            if (sigIdx >= 0 && sigIdx < syms.size()) {
                g.signature = syms.get(sigIdx).name;
            }
            long n = (s.size - 4) / 4;
            for (long i = 0; i < n; i++) {
                int mem = (int) r.u32((int) s.offset + 4 + (int) i * 4);
                g.members.add(mem);
            }
            s.group = g;
        }
    }

    private void parseVersionSections(R r, ElfObject o) throws ElfException {
        ElfObject.Section versym = null, verdef = null, verneed = null;
        for (ElfObject.Section s : o.sections) {
            if (".gnu.version".equals(s.name)) versym = s;
            else if (".gnu.version_d".equals(s.name)) verdef = s;
            else if (".gnu.version_r".equals(s.name)) verneed = s;
        }
        if (versym != null) {
            int n = (int) (versym.size / 2);
            o.dynSymVersions = new int[n];
            for (int i = 0; i < n; i++) {
                o.dynSymVersions[i] = r.u16((int) versym.offset + i * 2);
            }
        }
        if (verdef != null) parseVerdef(r, o, verdef);
        if (verneed != null) parseVerneed(r, o, verneed);
        // Attach resolved version names to dynamic symbols.
        for (Sym sym : o.dynSyms) {
            int idx = sym.index < o.dynSymVersions.length ? o.dynSymVersions[sym.index] : 0;
            sym.rawVersionIndex = idx;
            sym.versionName = o.versionName(idx);
        }
    }

    private void parseVerdef(R r, ElfObject o, ElfObject.Section s) throws ElfException {
        int ws = r.wordSize();
        int p = (int) s.offset;
        int end = (int) Math.min(r.d.length, s.offset + s.size);
        int guard = 0;
        while (p + 20 <= end && guard++ < 100000) {
            int vdVersion = r.u16(p);
            int vdFlags = r.u16(p + 2);
            int vdNdx = r.u16(p + 4);
            int vdCnt = r.u16(p + 6);
            int vdHash = (int) r.u32(p + 8);
            int vdAux = (int) r.u32(p + 12);
            int vdNext = (int) r.u32(p + 16);
            if (vdVersion != 1) break;
            ElfObject.VerDefInfo info = new ElfObject.VerDefInfo();
            info.ndx = vdNdx;
            info.base = (vdFlags & VER_FLG_BASE) != 0;
            int ap = p + vdAux;
            for (int a = 0; a < vdCnt && ap + 16 <= end; a++) {
                int vdaName = (int) r.u32(ap);
                int vdaNext = (int) r.u32(ap + 4);
                String name = dynString(r, linkedStrtab(o, s), vdaName);
                if (name != null) {
                    if (a == 0) info.name = name;
                    else info.aliases.add(name);
                }
                if (vdaNext == 0) break;
                ap += vdaNext;
            }
            if (info.name != null) o.verDefs.put(vdNdx, info);
            if (vdNext == 0) break;
            p += vdNext;
        }
        // Parent links (vd_hash chains) are not material to adjudication; record
        // aliases already captured above.
    }

    private ElfObject.Section linkedStrtab(ElfObject o, ElfObject.Section s) {
        return s.link > 0 && s.link < o.sectionByIndex.size()
                ? o.sectionByIndex.get(s.link) : null;
    }

    private void parseVerneed(R r, ElfObject o, ElfObject.Section s) throws ElfException {
        int p = (int) s.offset;
        int end = (int) Math.min(r.d.length, s.offset + s.size);
        int guard = 0;
        while (p + 16 <= end && guard++ < 100000) {
            int vnVersion = r.u16(p);
            int vnCnt = r.u16(p + 2);
            int vnFile = (int) r.u32(p + 4);
            int vnAux = (int) r.u32(p + 8);
            int vnNext = (int) r.u32(p + 12);
            if (vnVersion != 1) break;
            ElfObject.VerNeed vn = new ElfObject.VerNeed();
            vn.file = dynString(r, linkedStrtab(o, s), vnFile);
            int ap = p + vnAux;
            for (int a = 0; a < vnCnt && ap + 16 <= end; a++) {
                int vnaHash = (int) r.u32(ap);
                int vnaFlags = r.u16(ap + 4);
                int vnaOther = r.u16(ap + 6);
                int vnaName = (int) r.u32(ap + 8);
                int vnaNext = (int) r.u32(p + 12); // placeholder, overwritten below
                vnaNext = (int) r.u32(ap + 12);
                ElfObject.VerNeed.Aux aux = new ElfObject.VerNeed.Aux();
                aux.vers = vnaOther;
                aux.name = dynString(r, linkedStrtab(o, s), vnaName);
                vn.aux.add(aux);
                if (vnaNext == 0) break;
                ap += vnaNext;
            }
            o.verNeeds.add(vn);
            if (vnNext == 0) break;
            p += vnNext;
        }
    }
}
