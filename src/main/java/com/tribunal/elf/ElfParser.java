package com.tribunal.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯 Java ELF 解析器：支持 ELF32/ELF64、大小端；解析 section header、
 * .symtab/.dynsym、.gnu.version(_d/_r)、SHT_GROUP、.rel/.rela、DT_NEEDED。
 * 所有偏移与索引均做边界检查，损坏数据抛 {@link ElfException}（隔离该对象自身）。
 */
public class ElfParser {

    /** section header table 位置（解析期内部状态）。 */
    private static final class Shdr {
        long off;
        long entsize;
        long num;
        long strndx;
    }

    public ElfFile parse(byte[] data, String logicalName) {
        if (data == null || data.length < 16) {
            throw new ElfException("文件过短，缺少 ELF ident");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfException("ELF magic 不匹配");
        }
        int cls = data[4] & 0xff;
        int endian = data[5] & 0xff;
        if (cls != 1 && cls != 2) throw new ElfException("不支持的 ELF class: " + cls);
        if (endian != 1 && endian != 2) throw new ElfException("不支持的字节序: " + endian);

        Reader r = new Reader(data, endian == 2);
        ElfFile f = new ElfFile();
        f.name = logicalName;
        f.elfClass = cls;

        Shdr shdr = (cls == 2) ? header64(r, f) : header32(r, f);
        parseSections(r, f, shdr);
        parseDynamicTags(r, f);
        int[][] versym = parseVersym(r, f);
        parseVerdef(r, f);
        parseVerneed(r, f);
        parseSymbols(r, f, versym);
        parseGroups(r, f);
        parseRelocs(r, f);
        return f;
    }

    private Shdr header64(Reader r, ElfFile f) {
        if (r.len < 64) throw new ElfException("ELF64 header 不完整");
        f.type = r.u16(16);
        f.machine = r.u16(18);
        Shdr h = new Shdr();
        h.off = r.u64(40);
        h.entsize = r.u16(58);
        h.num = r.u16(60);
        h.strndx = r.u16(62);
        validateShdr(r, h, 64);
        return h;
    }

    private Shdr header32(Reader r, ElfFile f) {
        if (r.len < 52) throw new ElfException("ELF32 header 不完整");
        f.type = r.u16(16);
        f.machine = r.u16(18);
        Shdr h = new Shdr();
        h.off = r.u32(32);
        h.entsize = r.u16(46);
        h.num = r.u16(48);
        h.strndx = r.u16(50);
        validateShdr(r, h, 40);
        return h;
    }

    private void validateShdr(Reader r, Shdr h, long minEnt) {
        if (h.off <= 0 || h.num == 0) throw new ElfException("缺少 section header table");
        if (h.entsize < minEnt) throw new ElfException("sh_entsize 过小: " + h.entsize);
        if (h.num > 0xff00) throw new ElfException("扩展 section 编号暂不支持");
        if (h.off + h.num * h.entsize > r.len) throw new ElfException("section header table 越界");
        if (h.strndx == Elf.SHN_XINDEX || h.strndx >= h.num) {
            throw new ElfException("shstrndx 越界: " + h.strndx);
        }
    }

    private void parseSections(Reader r, ElfFile f, Shdr h) {
        for (int i = 0; i < h.num; i++) {
            long base = h.off + (long) i * h.entsize;
            ElfFile.Section s = new ElfFile.Section();
            s.index = i;
            if (f.elfClass == 2) {
                s.nameOff = r.u32at(base);
                s.type = (int) r.u32at(base + 4);
                s.flags = r.u64at(base + 8);
                s.addr = r.u64at(base + 16);
                s.offset = r.u64at(base + 24);
                s.size = r.u64at(base + 32);
                s.link = (int) r.u32at(base + 40);
                s.info = (int) r.u32at(base + 44);
                s.entsize = r.u64at(base + 56);
            } else {
                s.nameOff = r.u32at(base);
                s.type = (int) r.u32at(base + 4);
                s.flags = r.u32at(base + 8);
                s.addr = r.u32at(base + 12);
                s.offset = r.u32at(base + 16);
                s.size = r.u32at(base + 20);
                s.link = (int) r.u32at(base + 24);
                s.info = (int) r.u32at(base + 28);
                s.entsize = r.u32at(base + 36);
            }
            if (s.type != 8 /* SHT_NOBITS */ && s.offset + s.size > r.len) {
                throw new ElfException("section[" + i + "] 数据越界 (offset=" + s.offset + " size=" + s.size + ")");
            }
            if (s.link != 0 && s.link >= h.num) {
                throw new ElfException("section[" + i + "] sh_link 越界: " + s.link);
            }
            f.sections.add(s);
        }
        ElfFile.Section shstr = f.sections.get((int) h.strndx);
        for (ElfFile.Section s : f.sections) {
            s.name = r.str(shstr.offset, shstr.size, s.nameOff);
        }
    }

    private void parseDynamicTags(Reader r, ElfFile f) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != Elf.SHT_DYNAMIC) continue;
            long ent = s.entsize > 0 ? s.entsize : (f.elfClass == 2 ? 16 : 8);
            if (ent <= 0 || s.size % ent != 0) throw new ElfException(".dynamic 长度非法");
            ElfFile.Section strtab = s.link != 0 ? requireSection(f, s.link, "动态字符串表") : null;
            for (long i = 0; i < s.size / ent; i++) {
                long b = s.offset + i * ent;
                long tag = f.elfClass == 2 ? r.u64at(b) : r.u32at(b);
                long val = f.elfClass == 2 ? r.u64at(b + 8) : r.u32at(b + 4);
                if (tag == Elf.DT_NULL) break;
                if (tag == Elf.DT_NEEDED || tag == Elf.DT_SONAME) {
                    if (strtab == null) throw new ElfException("动态字符串表缺失");
                    String str = r.str(strtab.offset, strtab.size, val);
                    if (tag == Elf.DT_NEEDED) f.needed.add(str);
                    else f.soname = str;
                }
            }
        }
    }

    /** 返回按 section 下标对齐的 versym 数组（无 versym 的 section 为 null）。 */
    private int[][] parseVersym(Reader r, ElfFile f) {
        int[][] out = new int[f.sections.size()][];
        for (ElfFile.Section s : f.sections) {
            if (s.type != Elf.SHT_GNU_VERSYM) continue;
            if (s.entsize != 0 && s.entsize != 2) throw new ElfException("versym entsize 非法");
            if (s.size % 2 != 0) throw new ElfException("versym 长度非法");
            int n = (int) (s.size / 2);
            int[] vs = new int[n];
            for (int i = 0; i < n; i++) vs[i] = r.u16at(s.offset + i * 2L);
            out[s.index] = vs;
        }
        return out;
    }

    private void parseVerdef(Reader r, ElfFile f) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != Elf.SHT_GNU_VERDEF) continue;
            ElfFile.Section strtab = requireSection(f, s.link, ".gnu.version_d 的字符串表");
            long off = s.offset;
            long end = s.offset + s.size;
            while (true) {
                if (off + 20 > end) throw new ElfException("verdef 越界");
                int vdVersion = r.u16at(off);
                if (vdVersion != 1) throw new ElfException("verdef 版本非法: " + vdVersion);
                ElfFile.Verdef vd = new ElfFile.Verdef();
                vd.flags = r.u16at(off + 2);
                vd.index = r.u16at(off + 4);
                long vdCnt = r.u16at(off + 6);
                long vdAux = r.u32at(off + 12);
                long vdNext = r.u32at(off + 16);
                if (vdCnt <= 0) throw new ElfException("verdef vd_cnt 非法");
                long aoff = off + vdAux;
                for (long a = 0; a < vdCnt; a++) {
                    if (aoff + 8 > end) throw new ElfException("verdaux 越界");
                    String nm = r.str(strtab.offset, strtab.size, r.u32at(aoff));
                    long vdaNext = r.u32at(aoff + 4);
                    if (a == 0) vd.name = nm; else vd.aliases.add(nm);
                    if (vdaNext == 0) break;
                    aoff += vdaNext;
                }
                f.verdefs.add(vd);
                if (vdNext == 0) break;
                off += vdNext;
            }
        }
    }

    private void parseVerneed(Reader r, ElfFile f) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != Elf.SHT_GNU_VERNEED) continue;
            ElfFile.Section strtab = requireSection(f, s.link, ".gnu.version_r 的字符串表");
            long off = s.offset;
            long end = s.offset + s.size;
            while (true) {
                if (off + 16 > end) throw new ElfException("verneed 越界");
                int vnVersion = r.u16at(off);
                if (vnVersion != 1) throw new ElfException("verneed 版本非法: " + vnVersion);
                long vnCnt = r.u16at(off + 2);
                long vnFile = r.u32at(off + 4);
                long vnAux = r.u32at(off + 8);
                long vnNext = r.u32at(off + 12);
                ElfFile.Verneed vn = new ElfFile.Verneed();
                vn.file = r.str(strtab.offset, strtab.size, vnFile);
                long aoff = off + vnAux;
                for (long a = 0; a < vnCnt; a++) {
                    if (aoff + 16 > end) throw new ElfException("vernaux 越界");
                    ElfFile.Vernaux va = new ElfFile.Vernaux();
                    va.flags = r.u16at(aoff + 4);
                    va.other = r.u16at(aoff + 6);
                    va.name = r.str(strtab.offset, strtab.size, r.u32at(aoff + 8));
                    vn.entries.add(va);
                    if (r.u32at(aoff + 12) == 0) break;
                    aoff += r.u32at(aoff + 12);
                }
                f.verneeds.add(vn);
                if (vnNext == 0) break;
                off += vnNext;
            }
        }
    }

    private void parseSymbols(Reader r, ElfFile f, int[][] versymBySection) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != Elf.SHT_SYMTAB && s.type != Elf.SHT_DYNSYM) continue;
            boolean dynamic = s.type == Elf.SHT_DYNSYM;
            ElfFile.Section strtab = requireSection(f, s.link, "符号字符串表");
            long minEnt = f.elfClass == 2 ? 24 : 16;
            long ent = s.entsize > 0 ? s.entsize : minEnt;
            if (ent < minEnt) throw new ElfException("symtab entsize 非法: " + ent);
            if (s.size % ent != 0) throw new ElfException("符号表长度非法: " + s.name);
            int[] versym = s.index < versymBySection.length ? versymBySection[s.index] : null;
            for (long i = 0; i < s.size / ent; i++) {
                long b = s.offset + i * ent;
                ElfFile.Symbol sym = new ElfFile.Symbol();
                sym.tableIndex = (int) i;
                sym.dynamic = dynamic;
                if (f.elfClass == 2) {
                    sym.name = r.str(strtab.offset, strtab.size, r.u32at(b));
                    int info = r.u8at(b + 4);
                    sym.bind = info >> 4;
                    sym.type = info & 0xf;
                    sym.visibility = r.u8at(b + 5) & 3;
                    sym.shndx = r.u16at(b + 6);
                    sym.value = r.u64at(b + 8);
                    sym.size = r.u64at(b + 16);
                } else {
                    sym.name = r.str(strtab.offset, strtab.size, r.u32at(b));
                    sym.value = r.u32at(b + 4);
                    sym.size = r.u32at(b + 8);
                    int info = r.u8at(b + 12);
                    sym.bind = info >> 4;
                    sym.type = info & 0xf;
                    sym.visibility = r.u8at(b + 13) & 3;
                    sym.shndx = r.u16at(b + 14);
                }
                if (versym != null && sym.tableIndex < versym.length) {
                    int raw = versym[sym.tableIndex];
                    sym.versionHidden = (raw & Elf.VERSYM_HIDDEN) != 0;
                    sym.versionIndex = raw & Elf.VERSYM_VERSION;
                }
                if (dynamic) f.dynSymbols.add(sym); else f.symbols.add(sym);
            }
        }
        resolveVersions(f);
    }

    /** 把 versym 索引还原到 verdef/verneed 给出的版本名。 */
    private void resolveVersions(ElfFile f) {
        for (ElfFile.Symbol sym : f.dynSymbols) {
            int vi = sym.versionIndex;
            if (vi <= Elf.VER_NDX_GLOBAL) continue;
            boolean matched = false;
            for (ElfFile.Verdef vd : f.verdefs) {
                if (vd.index == vi) { sym.versionName = vd.name; matched = true; break; }
            }
            if (!matched) {
                outer:
                for (ElfFile.Verneed vn : f.verneeds) {
                    for (ElfFile.Vernaux va : vn.entries) {
                        if ((va.other & Elf.VERSYM_VERSION) == vi) {
                            sym.versionName = va.name;
                            matched = true;
                            break outer;
                        }
                    }
                }
            }
            if (!matched) {
                throw new ElfException("符号 " + sym.name + " 的版本索引 " + vi + " 无法还原为版本名");
            }
        }
    }

    private void parseGroups(Reader r, ElfFile f) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != Elf.SHT_GROUP) continue;
            if (s.size % 4 != 0) throw new ElfException("group 长度非法");
            ElfFile.Symbol sig = null;
            List<ElfFile.Symbol> symtab = s.link >= 0 && s.link < f.sections.size()
                    && f.sections.get(s.link).type == Elf.SHT_DYNSYM ? f.dynSymbols : f.symbols;
            if (s.info >= 0 && s.info < symtab.size()) sig = symtab.get(s.info);
            if (sig == null) throw new ElfException("group 签名符号越界: " + s.info);
            ElfFile.Group g = new ElfFile.Group();
            g.signature = sig.name;
            for (long i = 0; i < s.size / 4; i++) {
                long w = r.u32at(s.offset + i * 4);
                if (i == 0) {
                    g.comdat = (w & Elf.GRP_COMDAT) != 0;
                } else {
                    if (w >= f.sections.size()) throw new ElfException("group 成员 section 越界: " + w);
                    g.sectionIndices.add((int) w);
                }
            }
            f.groups.add(g);
        }
    }

    private void parseRelocs(Reader r, ElfFile f) {
        for (ElfFile.Section s : f.sections) {
            if (s.type != Elf.SHT_REL && s.type != Elf.SHT_RELA) continue;
            boolean rela = s.type == Elf.SHT_RELA;
            long minEnt = f.elfClass == 2 ? (rela ? 24 : 16) : (rela ? 12 : 8);
            long ent = s.entsize > 0 ? s.entsize : minEnt;
            if (ent < minEnt) throw new ElfException("relocation entsize 非法: " + s.name);
            if (s.size % ent != 0) throw new ElfException("relocation 长度非法: " + s.name);
            boolean dyn = s.link < f.sections.size()
                    && f.sections.get(s.link).type == Elf.SHT_DYNSYM;
            int symCount = dyn ? f.dynSymbols.size() : f.symbols.size();
            for (long i = 0; i < s.size / ent; i++) {
                long b = s.offset + i * ent;
                ElfFile.Reloc rel = new ElfFile.Reloc();
                rel.sectionName = s.name;
                rel.targetSection = s.info;
                rel.dynamic = dyn;
                if (f.elfClass == 2) {
                    rel.offset = r.u64at(b);
                    long info = r.u64at(b + 8);
                    rel.symIndex = (int) (info >>> 32);
                    rel.relocType = info & 0xffffffffL;
                    rel.addend = rela ? r.u64at(b + 16) : null;
                } else {
                    rel.offset = r.u32at(b);
                    long info = r.u32at(b + 4);
                    rel.symIndex = (int) (info >>> 8);
                    rel.relocType = info & 0xff;
                    rel.addend = rela ? r.u32at(b + 8) : null;
                }
                if (rel.symIndex >= symCount) {
                    throw new ElfException("relocation 符号下标越界: " + rel.symIndex + " (" + s.name + ")");
                }
                f.relocs.add(rel);
            }
        }
    }

    private ElfFile.Section requireSection(ElfFile f, int index, String what) {
        if (index <= 0 || index >= f.sections.size()) {
            throw new ElfException(what + "缺失或越界: sh_link=" + index);
        }
        return f.sections.get(index);
    }

    /** 带边界检查的字节读取器。 */
    static final class Reader {
        final byte[] d;
        final boolean be;
        final int len;

        Reader(byte[] d, boolean be) {
            this.d = d;
            this.be = be;
            this.len = d.length;
        }

        private void need(long off, long n) {
            if (off < 0 || n < 0 || off + n > len) {
                throw new ElfException("读取越界: offset=" + off + " length=" + n + " (文件长度 " + len + ")");
            }
        }

        int u8at(long off) {
            need(off, 1);
            return d[(int) off] & 0xff;
        }

        int u16at(long off) {
            need(off, 2);
            int i = (int) off;
            if (be) return ((d[i] & 0xff) << 8) | (d[i + 1] & 0xff);
            return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8);
        }

        long u32at(long off) {
            need(off, 4);
            int i = (int) off;
            if (be) {
                return ((long) (d[i] & 0xff) << 24) | ((long) (d[i + 1] & 0xff) << 16)
                        | ((long) (d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
            }
            return (d[i] & 0xff) | ((long) (d[i + 1] & 0xff) << 8)
                    | ((long) (d[i + 2] & 0xff) << 16) | ((long) (d[i + 3] & 0xff) << 24);
        }

        long u64at(long off) {
            need(off, 8);
            long lo = u32at(off);
            long hi = u32at(off + 4);
            return be ? (lo << 32) | hi : (hi << 32) | lo;
        }

        int u16(long off) { return u16at(off); }
        long u32(long off) { return u32at(off); }
        long u64(long off) { return u64at(off); }

        /** 从字符串表读取 NUL 结尾字符串，带边界检查。 */
        String str(long tableOff, long tableSize, long strOff) {
            if (strOff < 0 || strOff >= Math.max(tableSize, 1)) {
                if (strOff == 0 && tableSize == 0) return "";
                throw new ElfException("字符串表偏移越界: " + strOff + " (表长 " + tableSize + ")");
            }
            long start = tableOff + strOff;
            need(start, 1);
            long end = start;
            long limit = tableOff + tableSize;
            while (end < limit && d[(int) end] != 0) end++;
            if (end >= limit) throw new ElfException("字符串未终止: offset=" + strOff);
            return new String(d, (int) start, (int) (end - start), StandardCharsets.UTF_8);
        }
    }
}
