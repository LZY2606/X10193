package com.symbolcourt.elf;

import com.symbolcourt.model.ElfFile;
import com.symbolcourt.model.Reloc;
import com.symbolcourt.model.Section;
import com.symbolcourt.model.Sym;
import com.symbolcourt.model.VersionDef;
import com.symbolcourt.model.VersionNeed;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 ELF64 对象。只实现裁决所需内容，所有访问走 {@link ElfReader} 的边界检查；
 * 任何结构性损坏抛 {@link ElfException}，由调用方隔离该对象自身。
 */
public class ElfParser {

    public ElfFile parse(byte[] data, String name) throws ElfException {
        ElfReader r = new ElfReader(data);
        if (data.length < 64) {
            throw new ElfException("文件短于 ELF 头: " + data.length + " 字节");
        }
        if (r.u8(0) != 0x7f || r.u8(1) != 'E' || r.u8(2) != 'L' || r.u8(3) != 'F') {
            throw new ElfException("ELF 魔数错误");
        }
        int cls = r.u8(4);
        if (cls != Elf.ELFCLASS64) {
            throw new ElfException("暂只支持 ELFCLASS64（class=" + cls + "）");
        }
        int enc = r.u8(5);
        if (enc != Elf.ELFDATA2LSB) {
            throw new ElfException("暂只支持小端 ELF（data=" + enc + "）");
        }

        ElfFile f = new ElfFile();
        f.name = name;
        f.elfClass = cls;
        f.dataEncoding = enc;
        f.littleEndian = true;
        f.type = r.u16(16);
        f.machine = r.u16(18);
        f.entry = r.u64(24);

        long phoff = r.u64(32);
        long shoff = r.u64(40);
        int phentsize = r.u16(54);
        int phnum = r.u16(56);
        int shentsize = r.u16(58);
        int shnum0 = r.u16(60);
        int shstrndx0 = r.u16(62);

        long dynOff = 0;
        long dynSize = 0;
        for (int i = 0; i < phnum; i++) {
            long base = phoff + (long) i * phentsize;
            long ptype = r.u32(base);
            long poff = r.u64(base + 8);
            long pfilesz = r.u64(base + 32);
            if (ptype == Elf.PT_DYNAMIC) {
                dynOff = poff;
                dynSize = pfilesz;
            }
        }

        if (shoff == 0) {
            parseDynamicOnly(f, r, dynOff, dynSize);
            return f;
        }

        if (shnum0 == 0) {
            shnum0 = (int) r.u64(shoff + 32);
        }
        int shstrndx = shstrndx0;
        if (shstrndx0 == Elf.SHN_XINDEX) {
            shstrndx = (int) r.u32(shoff + 40);
        }
        if (shentsize < 64) {
            throw new ElfException("e_shentsize 小于 ELF64 节头: " + shentsize);
        }

        for (int i = 0; i < shnum0; i++) {
            long base = shoff + (long) i * shentsize;
            Section s = new Section();
            s.index = i;
            s.name = String.valueOf(r.u32(base));
            s.type = (int) r.u32(base + 4);
            s.flags = r.u64(base + 8);
            s.addr = r.u64(base + 16);
            s.offset = r.u64(base + 24);
            s.size = r.u64(base + 32);
            s.link = (int) r.u32(base + 40);
            s.info = (int) r.u32(base + 44);
            s.align = r.u64(base + 48);
            s.entsize = r.u64(base + 56);
            f.sections.add(s);
        }
        if (shstrndx < 0 || shstrndx >= f.sections.size()) {
            throw new ElfException("e_shstrndx 越界: " + shstrndx);
        }
        Section shstr = f.sections.get(shstrndx);
        for (Section s : f.sections) {
            long nameOff = Long.parseLong(s.name);
            if (nameOff < 0 || nameOff >= shstr.size) {
                s.name = "?";
            } else {
                s.name = r.cstr(shstr.offset + nameOff, shstr.size - nameOff);
            }
        }

        for (Section g : f.sections) {
            if (g.type != Elf.SHT_GROUP || g.size < 4) {
                continue;
            }
            f.groupSections.add(g.index);
            int flags = (int) r.u32(g.offset);
            long count = (g.size - 4) / 4;
            for (int k = 0; k < count; k++) {
                int member = (int) r.u32(g.offset + 4 + 4L * k);
                if (member >= 0 && member < f.sections.size()) {
                    f.sections.get(member).groupIndex = g.index;
                }
            }
            g.info = flags;
        }

        for (Section s : f.sections) {
            if (s.type == Elf.SHT_SYMTAB) {
                parseSymtab(f, r, s, false);
            } else if (s.type == Elf.SHT_DYNSYM) {
                parseSymtab(f, r, s, true);
            }
        }

        parseVersionSections(f, r);
        annotateSymbolVersions(f);

        Section dynSec = findSection(f, Elf.SHT_DYNAMIC);
        if (dynSec != null) {
            parseDynamic(f, r, dynSec);
        } else if (dynOff != 0) {
            parseDynamic(f, r, dynOff, dynSize, findDynStrSection(f));
        }

        for (Section s : f.sections) {
            if (s.type == Elf.SHT_RELA || s.type == Elf.SHT_REL) {
                parseRelocs(f, r, s);
            }
        }
        return f;
    }

    private void parseSymtab(ElfFile f, ElfReader r, Section symSec, boolean dynamic) throws ElfException {
        if (symSec.link < 0 || symSec.link >= f.sections.size()) {
            throw new ElfException("符号表 sh_link 越界: " + symSec.link);
        }
        Section strSec = f.sections.get(symSec.link);
        long entsize = symSec.entsize > 0 ? symSec.entsize : 24;
        if (entsize < 24) {
            throw new ElfException("ELF64 符号条目过小: " + entsize);
        }
        long count = symSec.size / entsize;
        for (int i = 0; i < count; i++) {
            long base = symSec.offset + i * entsize;
            Sym s = new Sym();
            s.index = i;
            s.dynamic = dynamic;
            long nameOff = r.u32(base);
            if (nameOff < 0 || nameOff >= strSec.size) {
                s.name = "";
            } else {
                s.name = r.cstr(strSec.offset + nameOff, strSec.size - nameOff);
            }
            long info = r.u8(base + 4);
            s.bind = (int) (info >> 4);
            s.type = (int) (info & 0xf);
            s.visibility = r.u8(base + 5) & 0x3;
            s.shndx = r.u16(base + 6);
            s.value = r.u64(base + 8);
            s.size = r.u64(base + 16);
            s.common = s.shndx == Elf.SHN_COMMON;
            s.defined = s.shndx != Elf.SHN_UNDEF;
            if (s.shndx == Elf.SHN_XINDEX) {
                // SHT_SYMTAB_SHNDX 支持：fixture 不构造，但仍安全降级
                s.shndx = 0;
                s.defined = false;
            }
            f.symbols.add(s);
        }
    }

    private void parseRelocs(ElfFile f, ElfReader r, Section sec) throws ElfException {
        boolean rela = sec.type == Elf.SHT_RELA;
        long entsize = sec.entsize > 0 ? sec.entsize : (rela ? 24 : 16);
        if (entsize < (rela ? 24 : 16)) {
            throw new ElfException("重定位条目过小: section=" + sec.name + " entsize=" + entsize);
        }
        boolean linkedToDynsym = sec.info >= 0 && sec.info < f.sections.size()
                && f.sections.get(sec.info).type == Elf.SHT_DYNSYM;
        long count = sec.size / entsize;
        for (int i = 0; i < count; i++) {
            long base = sec.offset + i * entsize;
            Reloc rel = new Reloc();
            rel.rela = rela;
            rel.offset = r.u64(base);
            long info = r.u64(base + 8);
            rel.symIndex = (int) (info >> 32);
            rel.type = (int) (info & 0xffffffffL);
            rel.addend = rela ? r.u64(base + 16) : 0;
            rel.typeName = Elf.relocName(f.machine, rel.type);
            rel.sectionName = sec.name;
            rel.allocated = sec.allocated() || linkedToDynsym;
            Sym sym = linkedToDynsym ? f.dynamicSymbolAt(rel.symIndex) : f.symbolAt(rel.symIndex);
            rel.symName = sym == null ? null : sym.name;
            f.relocations.add(rel);
        }
    }

    private void parseVersionSections(ElfFile f, ElfReader r) throws ElfException {
        for (Section s : f.sections) {
            if (s.type == Elf.SHT_GNU_VERDEF) {
                parseVerdef(f, r, s);
            } else if (s.type == Elf.SHT_GNU_VERNEED) {
                parseVerneed(f, r, s);
            }
        }
    }

    private void parseVerdef(ElfFile f, ElfReader r, Section s) throws ElfException {
        if (s.link < 0 || s.link >= f.sections.size()) {
            throw new ElfException("verdef sh_link 越界");
        }
        Section str = f.sections.get(s.link);
        long p = s.offset;
        long end = s.offset + s.size;
        int guard = 0;
        while (p + 20 <= end && guard++ < 100000) {
            long vdVersion = r.u16(p);
            int vdFlags = r.u16(p + 2);
            int vdNdx = r.u16(p + 4);
            int vdCnt = r.u16(p + 6);
            long vdHash = r.u32(p + 8);
            long vdAux = r.u32(p + 12);
            long vdNext = r.u32(p + 16);
            if (vdVersion != 1) {
                break;
            }
            VersionDef def = new VersionDef();
            def.index = vdNdx;
            def.flags = vdFlags;
            long auxp = p + vdAux;
            for (int k = 0; k < vdCnt; k++) {
                long vnaName = r.u32(auxp + 4);
                long vnaNext = r.u32(auxp);
                String name;
                if (vnaName < 0 || vnaName >= str.size) {
                    name = "?";
                } else {
                    name = r.cstr(str.offset + vnaName, str.size - vnaName);
                }
                if (k == 0) {
                    def.name = name;
                } else {
                    def.aliases.add(name);
                }
                if (vnaNext == 0) {
                    break;
                }
                auxp += vnaNext;
                if (auxp >= end) {
                    throw new ElfException("verdaux 链越界");
                }
            }
            f.versionDefs.add(def);
            if (vdNext == 0) {
                break;
            }
            p += vdNext;
        }
    }

    private void parseVerneed(ElfFile f, ElfReader r, Section s) throws ElfException {
        if (s.link < 0 || s.link >= f.sections.size()) {
            throw new ElfException("verneed sh_link 越界");
        }
        Section str = f.sections.get(s.link);
        long p = s.offset;
        long end = s.offset + s.size;
        int indexSeed = 2;
        int guard = 0;
        while (p + 16 <= end && guard++ < 100000) {
            int vnVersion = r.u16(p);
            int vnCnt = r.u16(p + 2);
            long vnFile = r.u32(p + 4);
            long vnAux = r.u32(p + 8);
            long vnNext = r.u32(p + 12);
            if (vnVersion != 1) {
                break;
            }
            VersionNeed need = new VersionNeed();
            need.index = indexSeed++;
            need.file = vnFile < 0 || vnFile >= str.size ? "?"
                    : r.cstr(str.offset + vnFile, str.size - vnFile);
            long auxp = p + vnAux;
            for (int k = 0; k < vnCnt; k++) {
                long vnaHash = r.u32(auxp);
                int vnaFlags = r.u16(auxp + 4);
                int vnaOther = r.u16(auxp + 6);
                long vnaName = r.u32(auxp + 8);
                long vnaNext = r.u32(auxp + 12);
                if (vnaName < 0 || vnaName >= str.size) {
                    need.names.add("?");
                } else {
                    need.names.add(r.cstr(str.offset + vnaName, str.size - vnaName));
                }
                if (k == 0) {
                    need.flags = vnaFlags;
                    need.hash = (int) vnaHash;
                    need.index = vnaOther;
                }
                if (vnaNext == 0) {
                    break;
                }
                auxp += vnaNext;
                if (auxp >= end) {
                    throw new ElfException("vernaux 链越界");
                }
            }
            f.versionNeeds.add(need);
            if (vnNext == 0) {
                break;
            }
            p += vnNext;
        }
    }

    private void annotateSymbolVersions(ElfFile f) throws ElfException {
        Section versymSec = null;
        for (Section s : f.sections) {
            if (s.type == Elf.SHT_GNU_VERSYM) {
                versymSec = s;
                break;
            }
        }
        if (versymSec == null) {
            return;
        }
        long count = versymSec.size / 2;
        for (Sym s : f.symbols) {
            if (s.index >= count) {
                continue;
            }
            int raw = r.u16(versymSec.offset + 2L * s.index);
            s.versym = raw;
        }
    }

    private Section findSection(ElfFile f, int type) {
        for (Section s : f.sections) {
            if (s.type == type) {
                return s;
            }
        }
        return null;
    }

    private Section findDynStrSection(ElfFile f) {
        for (Section s : f.sections) {
            if (s.type == Elf.SHT_STRTAB && ".dynstr".equals(s.name)) {
                return s;
            }
        }
        return null;
    }

    private void parseDynamic(ElfFile f, ElfReader r, Section dynSec) throws ElfException {
        Section dynstr = findDynStrSection(f);
        parseDynamic(f, r, dynSec.offset, dynSec.size, dynstr);
    }

    private void parseDynamic(ElfFile f, ElfReader r, long off, long size, Section dynstr) throws ElfException {
        if (off == 0) {
            return;
        }
        long p = off;
        long end = size > 0 ? off + size : r.length();
        int guard = 0;
        while (p + 16 <= end && guard++ < 200000) {
            long tag = r.u64(p);
            long val = r.u64(p + 8);
            p += 16;
            if (tag == Elf.DT_NULL) {
                break;
            }
            if (tag == Elf.DT_NEEDED && dynstr != null && val >= 0 && val < dynstr.size) {
                f.needed.add(r.cstr(dynstr.offset + val, dynstr.size - val));
            } else if (tag == Elf.DT_SONAME && dynstr != null && val >= 0 && val < dynstr.size) {
                f.soname = r.cstr(dynstr.offset + val, dynstr.size - val);
            }
        }
    }

    /** 无节头时通过程序头还原动态符号等信息（如 strip 后的共享对象）。 */
    private void parseDynamicOnly(ElfFile f, ElfReader r, long dynOff, long dynSize) throws ElfException {
        if (dynOff == 0) {
            throw new ElfException("无节头且无 PT_DYNAMIC，无法解析");
        }
        long phoff = r.u64(32);
        int phentsize = r.u16(54);
        int phnum = r.u16(56);
        long[] segOff = new long[phnum];
        long[] segVaddr = new long[phnum];
        long[] segFilesz = new long[phnum];
        for (int i = 0; i < phnum; i++) {
            long base = phoff + (long) i * phentsize;
            segOff[i] = r.u64(base + 8);
            segVaddr[i] = r.u64(base + 16);
            segFilesz[i] = r.u64(base + 32);
        }

        java.util.HashMap<Long, Long> tags = new java.util.HashMap<>();
        long p = dynOff;
        long end = dynSize > 0 ? dynOff + dynSize : r.length();
        int guard = 0;
        while (p + 16 <= end && guard++ < 200000) {
            long tag = r.u64(p);
            long val = r.u64(p + 8);
            p += 16;
            if (tag == Elf.DT_NULL) {
                break;
            }
            tags.put(tag, val);
        }

        long symtab = tags.getOrDefault(Elf.DT_SYMTAB, 0L);
        long strtab = tags.getOrDefault(Elf.DT_STRTAB, 0L);
        long strsz = tags.getOrDefault(Elf.DT_STRSZ, 0L);
        long syment = tags.getOrDefault(Elf.DT_SYMENT, 24L);
        long hash = tags.getOrDefault(Elf.DT_HASH, 0L);
        long symCount = 0;
        if (hash != 0) {
            long ho = vaddrToOffset(r, hash, segOff, segVaddr, segFilesz, phnum);
            r.check(ho, 8);
            long nchain = r.u32(ho + 4);
            symCount = nchain;
        }
        if (symtab == 0 || strtab == 0 || symCount == 0) {
            return;
        }
        long strOff = vaddrToOffset(r, strtab, segOff, segVaddr, segFilesz, phnum);
        long symOff = vaddrToOffset(r, symtab, segOff, segVaddr, segFilesz, phnum);
        r.check(strOff, strsz > 0 ? strsz : 1);
        r.check(symOff, symCount * syment);
        for (int i = 0; i < symCount; i++) {
            long base = symOff + i * syment;
            Sym s = new Sym();
            s.index = i;
            s.dynamic = true;
            long nameOff = r.u32(base);
            s.name = (strsz <= 0 || nameOff >= strsz)
                    ? "" : r.cstr(strOff + nameOff, strsz - nameOff);
            long info = r.u8(base + 4);
            s.bind = (int) (info >> 4);
            s.type = (int) (info & 0xf);
            s.visibility = r.u8(base + 5) & 0x3;
            s.shndx = r.u16(base + 6);
            s.value = r.u64(base + 8);
            s.size = r.u64(base + 16);
            s.common = s.shndx == Elf.SHN_COMMON;
            s.defined = s.shndx != Elf.SHN_UNDEF;
            f.symbols.add(s);
        }
        // 动态重定位（用于 strip 对象的 copy/jump-slot 裁决）
        for (long[] relInfo : new long[][] {
                {tags.getOrDefault(0x17L /*DT_REL*/, 0L),
                        tags.getOrDefault(0x18L /*DT_RELSZ*/, 0L),
                        tags.getOrDefault(0x19L /*DT_RELENT*/, 0L), 0},
                {tags.getOrDefault(0x7L /*DT_RELA*/, 0L),
                        tags.getOrDefault(0x8L /*DT_RELASZ*/, 0L),
                        tags.getOrDefault(0x9L /*DT_RELAENT*/, 0L), 1}}) {
            if (relInfo[0] == 0 || relInfo[1] == 0) {
                continue;
            }
            boolean rela = relInfo[3] == 1;
            long es = relInfo[2] > 0 ? relInfo[2] : (rela ? 24 : 16);
            long ro = vaddrToOffset(r, relInfo[0], segOff, segVaddr, segFilesz, phnum);
            long count2 = relInfo[1] / es;
            for (int i = 0; i < count2; i++) {
                long base = ro + i * es;
                Reloc rel = new Reloc();
                rel.rela = rela;
                rel.allocated = true;
                rel.sectionName = "<dynamic>";
                rel.offset = r.u64(base);
                long info = r.u64(base + 8);
                rel.symIndex = (int) (info >> 32);
                rel.type = (int) (info & 0xffffffffL);
                rel.addend = rela ? r.u64(base + 16) : 0;
                rel.typeName = Elf.relocName(f.machine, rel.type);
                Sym sym = f.dynamicSymbolAt(rel.symIndex);
                rel.symName = sym == null ? null : sym.name;
                f.relocations.add(rel);
            }
        }
    }

    private long vaddrToOffset(ElfReader r, long vaddr, long[] segOff, long[] segVaddr,
                               long[] segFilesz, int phnum) throws ElfException {
        for (int i = 0; i < phnum; i++) {
            if (vaddr >= segVaddr[i] && vaddr < segVaddr[i] + segFilesz[i]) {
                return segOff[i] + (vaddr - segVaddr[i]);
            }
        }
        throw new ElfException("虚地址无法映射到文件: 0x" + Long.toHexString(vaddr));
    }
}
