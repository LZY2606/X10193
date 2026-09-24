package tribunal.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 只读解析 ELF 字节。任何结构性越界都会抛出 ElfFormatException，由调用方隔离单个对象。 */
public final class ElfReader {

    public static final int SHT_PROGBITS = 1, SHT_SYMTAB = 2, SHT_STRTAB = 3, SHT_RELA = 4;
    public static final int SHT_HASH = 5, SHT_DYNAMIC = 6, SHT_NOTE = 7, SHT_NOBITS = 8, SHT_REL = 9;
    public static final int SHT_DYNSYM = 11, SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd, SHT_GNU_VERNEED = 0x6ffffffe, SHT_GNU_VERSYM = 0x6fffffff;

    public static final int DT_NEEDED = 1, DT_SONAME = 14, DT_STRTAB = 5, DT_STRSZ = 10, DT_SYMTAB = 6;
    public static final int DT_VERSYM = 0x6ffffff0, DT_VERDEF = 0x6ffffffc, DT_VERDEFNUM = 0x6ffffffd;
    public static final int DT_VERNEED = 0x6ffffffe, DT_VERNEEDNUM = 0x6fffffff;

    public static final int VERSYM_HIDDEN = 0x8000;

    private ElfReader() {}

    public static ElfData parse(byte[] data) {
        ElfData e = new ElfData();
        R r = new R(data);
        try {
            if (data.length < 16) throw new ElfFormatException("file shorter than ELF ident");
            if (r.u8(0) != 0x7f || r.u8(1) != 'E' || r.u8(2) != 'L' || r.u8(3) != 'F')
                throw new ElfFormatException("bad ELF magic");
            int cls = r.u8(4);
            if (cls != 1 && cls != 2) throw new ElfFormatException("bad EI_CLASS " + cls);
            e.elf64 = cls == 2;
            int enc = r.u8(5);
            if (enc != 1 && enc != 2) throw new ElfFormatException("bad EI_DATA " + enc);
            e.littleEndian = enc == 1;
            r.le = e.littleEndian;
            e.type = r.u16(16);
            e.machine = r.u16(e.elf64 ? 18 : 18);
            e.machineName = machineName(e.machine);
            int shoffOff = e.elf64 ? 40 : 32;
            int shentsOff = e.elf64 ? 58 : 46;
            int shnumOff = e.elf64 ? 60 : 48;
            int shstrOff = e.elf64 ? 62 : 50;
            long shoff = r.ptr(shoffOff);
            int shentsize = r.u16(shentsOff);
            int shnum = r.u16(shnumOff);
            int shstrndx = r.u16(shstrOff);
            if (shnum == 0) return e;
            int minEnt = e.elf64 ? 64 : 40;
            if (shentsize < minEnt) throw new ElfFormatException("shentsize too small: " + shentsize);
            readSections(e, r, shoff, shentsize, shnum);
            if (shstrndx != 0 && shstrndx != 0xffff && shstrndx < e.sections.size())
                assignNames(e, r, e.sections.get(shstrndx));
            readSymbols(e, r);
            readGroups(e, r);
            readRela(e, r);
            readDynamic(e, r);
            readVersions(e, r);
            assignVersionNames(e);
            markGroups(e);
            return e;
        } catch (ElfFormatException ex) {
            e.corrupt = true;
            e.error = ex.getMessage();
            return e;
        } catch (RuntimeException ex) {
            e.corrupt = true;
            e.error = "malformed: " + ex.getClass().getSimpleName() + " " + ex.getMessage();
            return e;
        }
    }

    private static String machineName(int m) {
        return switch (m) {
            case 3 -> "x86";
            case 8 -> "MIPS";
            case 40 -> "ARM";
            case 62 -> "x86_64";
            case 183 -> "AArch64";
            case 243 -> "RISC-V";
            default -> "MACHINE_" + m;
        };
    }

    private static void readSections(ElfData e, R r, long shoff, int entsize, int shnum) {
        for (int i = 0; i < shnum; i++) {
            long base = Math.addExact(shoff, (long) i * entsize);
            Section s = new Section();
            if (e.elf64) {
                s.nameOffset = (int) r.u32(base);
                s.type = (int) r.u32(base + 4);
                s.flags = r.u64(base + 8);
                s.addr = r.u64(base + 16);
                s.offset = r.u64(base + 24);
                s.size = r.u64(base + 32);
                s.link = (int) r.u32(base + 40);
                s.info = (int) r.u32(base + 44);
                s.addralign = r.u64(base + 48);
                s.entsize = r.u64(base + 56);
            } else {
                s.nameOffset = (int) r.u32(base);
                s.type = (int) r.u32(base + 4);
                s.flags = r.u32(base + 8);
                s.addr = r.u32(base + 12);
                s.offset = r.u32(base + 16);
                s.size = r.u32(base + 20);
                s.link = (int) r.u32(base + 24);
                s.info = (int) r.u32(base + 28);
                s.addralign = r.u32(base + 32);
                s.entsize = r.u32(base + 36);
            }
            e.sections.add(s);
        }
    }

    private static void assignNames(ElfData e, R r, Section str) {
        if (str.type != SHT_STRTAB || str.type == SHT_NOBITS) {
            // 仍尝试读取
        }
        for (Section s : e.sections) {
            s.name = r.strAt(str.offset, str.size, s.nameOffset);
        }
    }

    private static void readSymbols(ElfData e, R r) {
        for (Section s : e.sections) {
            boolean dyn = s.type == SHT_DYNSYM;
            if (s.type != SHT_SYMTAB && !dyn) continue;
            Section str = (s.link >= 0 && s.link < e.sections.size()) ? e.sections.get(s.link) : null;
            long strOff = str == null ? -1 : str.offset;
            long strSize = str == null ? 0 : str.size;
            long count = s.entsize == 0 ? 0 : s.size / s.entsize;
            if (count <= 0) {
                long ent = e.elf64 ? 24 : 16;
                if (s.size >= ent && s.entsize == 0) count = s.size / ent;
            }
            List<SymV> target = dyn ? e.dynsym : e.symtab;
            for (int i = 0; i < count; i++) {
                long base = s.offset + i * s.entsize;
                SymV sym = new SymV();
                sym.dynamic = dyn;
                if (e.elf64) {
                    int nameIdx = (int) r.u32(base);
                    sym.bind = r.u8(base + 4) >> 4;
                    sym.type = r.u8(base + 4) & 0xf;
                    sym.visibility = r.u8(base + 5) & 3;
                    sym.shndx = r.u16(base + 6);
                    sym.value = r.u64(base + 8);
                    sym.size = r.u64(base + 16);
                    sym.name = strOff < 0 ? "" : r.strAt(strOff, strSize, nameIdx);
                } else {
                    int nameIdx = (int) r.u32(base);
                    sym.value = r.u32(base + 4);
                    sym.size = r.u32(base + 8);
                    sym.bind = r.u8(base + 12) >> 4;
                    sym.type = r.u8(base + 12) & 0xf;
                    sym.visibility = r.u8(base + 13) & 3;
                    sym.shndx = r.u16(base + 14);
                    sym.name = strOff < 0 ? "" : r.strAt(strOff, strSize, nameIdx);
                }
                target.add(sym);
            }
        }
    }

    private static void readGroups(ElfData e, R r) {
        for (int gi = 0; gi < e.sections.size(); gi++) {
            Section s = e.sections.get(gi);
            if (s.type != SHT_GROUP) continue;
            Group g = new Group();
            g.sectionHeaderIndex = gi;
            g.flags = s.info;
            g.comdat = (s.info & 1) == 1;
            List<SymV> syms = e.section(s.link) == null ? List.of()
                    : (e.sections.get(s.link).type == SHT_DYNSYM ? e.dynsym : e.symtab);
            long cnt = s.size / 4;
            for (int i = 0; i < cnt; i++) {
                long memberIdx;
                    memberIdx = r.u32(s.offset + 4L * i);
                if (i == 0) {
                    if (!syms.isEmpty() && memberIdx >= 0 && memberIdx < syms.size()) {
                        g.signature = syms.get((int) memberIdx);
                        g.signatureName = g.signature.name;
                    }
                } else {
                    g.memberSections.add((int) memberIdx);
                }
            }
            if (g.signatureName == null) g.signatureName = "";
            e.groups.add(g);
            for (int mi : g.memberSections) {
                if (mi >= 0 && mi < e.sections.size()) {
                    Section mem = e.sections.get(mi);
                    mem.groupIndex = e.groups.size() - 1;
                }
            }
        }
    }

    private static void readRela(ElfData e, R r) {
        for (Section s : e.sections) {
            boolean rela = s.type == SHT_RELA;
            boolean rel = s.type == SHT_REL;
            if (!rela && !rel) continue;
            Section symsec = (s.link >= 0 && s.link < e.sections.size()) ? e.sections.get(s.link) : null;
            boolean dyn = symsec != null && symsec.type == SHT_DYNSYM;
            List<SymV> syms = symsec == null ? List.of()
                    : (dyn ? e.dynsym : e.symtab);
            Section target = (s.info >= 0 && s.info < e.sections.size()) ? e.sections.get(s.info) : null;
            long ent = rela ? (e.elf64 ? 24 : 12) : (e.elf64 ? 16 : 8);
            long count = s.size / ent;
            for (int i = 0; i < count; i++) {
                long base = s.offset + i * ent;
                Rela relObj = new Rela();
                relObj.section = s.name;
                relObj.applySection = target == null ? null : target.name;
                relObj.discardedTarget = target != null && target.discarded;
                relObj.dynamic = dyn;
                relObj.symTableKind = dyn ? 1 : 0;
                if (e.elf64) {
                    relObj.offset = r.u64(base);
                    long info = r.u64(base + 8);
                    relObj.symIndex = info >> 32;
                    relObj.type = (int) (info & 0xffffffffL);
                    relObj.relocTypeRaw = relObj.type;
                    if (rela) relObj.addend = r.u64(base + 16);
                } else {
                    relObj.offset = r.u32(base);
                    long info = r.u32(base + 4);
                    relObj.symIndex = info >> 8;
                    relObj.type = (int) (info & 0xff);
                    relObj.relocTypeRaw = relObj.type;
                    if (rela) relObj.addend = r.u32(base + 8);
                }
                relObj.addendPresent = rela;
                if (relObj.symIndex >= 0 && relObj.symIndex < syms.size())
                    relObj.sym = syms.get((int) relObj.symIndex);
                e.relas.add(relObj);
            }
        }
    }

    private static void readDynamic(ElfData e, R r) {
        Section dyn = null;
        for (Section s : e.sections) if (s.type == SHT_DYNAMIC) { dyn = s; break; }
        if (dyn == null) return;
        Section strSec = null;
        long strAddr = -1, strSize = 0, symAddr = -1;
        long ent = e.elf64 ? 16 : 8;
        int pairs = (int) (dyn.size / ent);
        long[] tags = new long[pairs * 2];
        for (int i = 0; i < pairs; i++) {
            long base = dyn.offset + i * ent;
            long tag = e.elf64 ? r.s64(base) : r.s32(base);
            long val = e.elf64 ? r.u64(base + 8) : r.u32(base + 4);
            tags[2 * i] = tag;
            tags[2 * i + 1] = val;
            if (tag == DT_STRTAB) strAddr = val;
            if (tag == DT_STRSZ) strSize = val;
            if (tag == DT_SYMTAB) symAddr = val;
        }
        // 将 strtab vaddr 映射到 section 文件偏移
        if (strAddr >= 0) {
            for (Section s : e.sections) {
                if (s.type == SHT_STRTAB && strAddr >= s.addr && strAddr < s.addr + Math.max(s.size, 1)) {
                    strSec = s; break;
                }
            }
            if (strSec == null) strSize = 0;
        }
        for (int i = 0; i < pairs; i++) {
            long tag = tags[2 * i], val = tags[2 * i + 1];
            if (tag == 0) break;
            if ((tag == DT_NEEDED || tag == DT_SONAME) && strSec != null) {
                long fileOff = strSec.offset + (val - strAddr);
                String nm = r.strAt(fileOff, strSize - (val - strAddr), 0);
                if (tag == DT_NEEDED) e.needed.add(nm);
                else e.soname = nm;
            }
        }
    }

    private static void readVersions(ElfData e, R r) {
        Section verdefSec = null, verneedSec = null, versymSec = null;
        Section verdefStr = null, verneedStr = null;
        for (Section s : e.sections) {
            if (s.type == SHT_GNU_VERDEF) { verdefSec = s; verdefStr = e.section(s.link); }
            else if (s.type == SHT_GNU_VERNEED) { verneedSec = s; verneedStr = e.section(s.link); }
            else if (s.type == SHT_GNU_VERSYM) versymSec = s;
        }
        if (versymSec != null) {
            long cnt = versymSec.size / 2;
            for (int i = 0; i < cnt; i++) e.versym.add((int) r.u16(versymSec.offset + 2L * i));
        }
        if (verdefSec != null && verdefStr != null) {
            long off = verdefSec.offset;
            long end = verdefSec.offset + verdefSec.size;
            int guard = 0;
            while (off != 0 && off + 20 <= end && guard++ < 10000) {
                VerDef vd = new VerDef();
                vd.flags = r.u16(off + 2);
                vd.index = r.u16(off + 4);
                int cnt = r.u16(off + 6);
                long auxOff = off + r.u32(off + 12);
                long next = r.u32(off + 16);
                long aoff = auxOff;
                for (int i = 0; i < cnt; i++) {
                    if (aoff + 8 > end) break;
                    long nameIdx = r.u32(aoff);
                    vd.names.add(r.strAt(verdefStr.offset, verdefStr.size, (int) nameIdx));
                    long anext = r.u32(aoff + 4);
                    if (anext == 0) break;
                    aoff += anext;
                }
                vd.base = vd.index == 1;
                e.verdefs.add(vd);
                if (next == 0) break;
                off += next;
            }
        }
        if (verneedSec != null && verneedStr != null) {
            long off = verneedSec.offset;
            long end = verneedSec.offset + verneedSec.size;
            int guard = 0;
            while (off != 0 && off + 16 <= end && guard++ < 10000) {
                int cnt = r.u16(off + 2);
                long fileNameIdx = r.u32(off + 4);
                long auxOff = off + r.u32(off + 8);
                long next = r.u32(off + 12);
                VerNeed vn = new VerNeed();
                vn.fileName = r.strAt(verneedStr.offset, verneedStr.size, (int) fileNameIdx);
                long aoff = auxOff;
                for (int i = 0; i < cnt; i++) {
                    if (aoff + 16 > end) break;
                    VerNeed.VAux aux = new VerNeed.VAux();
                    aux.flags = (int) r.u16(aoff + 2);
                    aux.hidden = (aux.flags & 0x2) != 0;
                    aux.weak = (aux.flags & 0x1) != 0;
                    aux.index = r.u16(aoff + 4);
                    long nameIdx = r.u32(aoff + 8);
                    aux.name = r.strAt(verneedStr.offset, verneedStr.size, (int) nameIdx);
                    vn.aux.add(aux);
                    long anext = r.u32(aoff + 12);
                    if (anext == 0) break;
                    aoff += anext;
                }
                e.verneeds.add(vn);
                if (next == 0) break;
                off += next;
            }
        }
    }

    private static void assignVersionNames(ElfData e) {
        for (int i = 0; i < e.dynsym.size(); i++) {
            SymV sym = e.dynsym.get(i);
            if (i >= e.versym.size()) continue;
            int raw = e.versym.get(i);
            sym.versionHidden = (raw & VERSYM_HIDDEN) != 0;
            sym.versionIndex = raw & 0x7fff;
        }
    }

    private static void markGroups(ElfData e) {
        // section.discarded 初始为 false；跨对象 COMDAT 胜负在裁决阶段决定。
    }

    /** 将版本索引解析为可读版本名；返回 null 表示无版本/特殊索引。 */
    public static String versionName(ElfData e, SymV sym) {
        int idx = sym.versionIndex;
        if (idx <= 1) return null;
        for (VerDef vd : e.verdefs) if (vd.index == idx) return vd.name();
        for (VerNeed vn : e.verneeds) for (VerNeed.VAux a : vn.aux) if (a.index == idx) return a.name;
        return "VER" + idx;
    }

    /** 带边界检查的二进制读取器。 */
    static final class R {
        private final byte[] d;
        boolean le = true;

        R(byte[] data) { this.d = data; }

        int u8(long off) {
            if (off < 0 || off >= d.length) throw new ElfFormatException("read u8 at " + off + ", len=" + d.length);
            return d[(int) off] & 0xff;
        }

        int u16(long off) {
            if (off < 0 || off + 2 > d.length) throw new ElfFormatException("read u16 at " + off + ", len=" + d.length);
            int a = u8(off), b = u8(off + 1);
            return le ? (a | b << 8) : (b | a << 8);
        }

        long u32(long off) { return s32(off) & 0xffffffffL; }

        long s32(long off) {
            if (off < 0 || off + 4 > d.length) throw new ElfFormatException("read u32 at " + off + ", len=" + d.length);
            int v = le
                    ? (u8(off) | u8(off + 1) << 8 | u8(off + 2) << 16 | u8(off + 3) << 24)
                    : (u8(off + 3) | u8(off + 2) << 8 | u8(off + 1) << 16 | u8(off) << 24);
            return v;
        }

        long u64(long off) {
            if (off < 0 || off + 8 > d.length) throw new ElfFormatException("read u64 at " + off + ", len=" + d.length);
            long v = 0;
            for (int i = 0; i < 8; i++) {
                int b = u8(off + (le ? i : 7 - i));
                v |= ((long) b) << (8 * i);
            }
            return v;
        }

        long s64(long off) { return u64(off); }

        long ptr(long off) { return off < 0 ? 0 : u64(off); }

        String strAt(long sectionOff, long sectionSize, int strOff) {
            if (strOff < 0 || strOff >= sectionSize) throw new ElfFormatException("strtab index " + strOff + " out of [0," + sectionSize + ")");
            long start = sectionOff + strOff;
            long end = sectionOff + sectionSize;
            if (start < 0 || start > d.length) throw new ElfFormatException("strtab offset out of file");
            int max = (int) Math.min(end, d.length);
            int p = (int) start;
            while (p < max && d[p] != 0) p++;
            return new String(d, (int) start, p - (int) start, StandardCharsets.UTF_8);
        }
    }
}
