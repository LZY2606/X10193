package org.symcourt.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java ELF parser used by the arbitration engine. It does not invoke any
 * system linker or loader; only byte-level decoding is performed.
 *
 * Supported: ELFCLASS32/ELFCLASS64, LSB/MSB, ET_REL/ET_EXEC/ET_DYN,
 * SHT_SYMTAB/SHT_DYNSYM, REL/RELA, SHT_GROUP, SHT_DYNAMIC (DT_NEEDED /
 * DT_SONAME / DT_SYMBOLIC), GNU symbol versions (.gnu.version*, with version
 * indices restored to names), and extended section indices (SHN_XINDEX).
 */
public final class ElfParser {

    public ElfObject parse(byte[] data, String displayName) {
        if (data == null || data.length < 16) {
            throw new ElfException("文件过短，不是 ELF 对象");
        }
        boolean le = (data[Elf.EI_DATA] & 0xff) == Elf.ELFDATA2LSB;
        Bin r = new Bin(data, le);
        if (!r.startsWith(0, Elf.MAG)) {
            throw new ElfException("缺少 \\x7fELF 魔数");
        }
        int eiClass = data[Elf.EI_CLASS] & 0xff;
        if (eiClass != Elf.ELFCLASS32 && eiClass != Elf.ELFCLASS64) {
            throw new ElfException("不支持的 EI_CLASS=" + eiClass);
        }
        int eiData = data[Elf.EI_DATA] & 0xff;
        if (eiData != Elf.ELFDATA2LSB && eiData != Elf.ELFDATA2MSB) {
            throw new ElfException("不支持的 EI_DATA=" + eiData);
        }
        ElfObject obj = new ElfObject();
        obj.elf64 = eiClass == Elf.ELFCLASS64;
        obj.littleEndian = le;
        obj.displayName = displayName;
        obj.sourceName = displayName;
        parseHeader(r, obj);
        parseSections(r, obj);
        parseSymbols(r, obj);
        parseGroups(r, obj);
        markSectionGroups(obj);
        parseDynamic(r, obj);
        parseVersions(r, obj);
        parseRelocs(r, obj);
        return obj;
    }

    private void parseHeader(Bin r, ElfObject obj) {
        int elf64 = obj.elf64 ? 1 : 0;
        // ELF64 header: e_type@16 e_machine@18 ... e_shoff@40 e_shentsize@58
        //               e_shnum@60 e_shstrndx@62
        // ELF32 header: e_type@16 e_machine@18 ... e_shoff@32 e_shentsize@46
        //               e_shnum@48 e_shstrndx@50
        obj.type = r.u16(16);
        obj.machine = r.u16(18);
        obj.flags = (int) (obj.elf64 ? r.u32(48) : r.u32(36));
        obj.entry = obj.elf64 ? r.u64(24) : r.u32(24);
    }

    public long shoff(Bin r, ElfObject obj) {
        return obj.elf64 ? r.u64(40) : r.u32(32);
    }

    public int shentsize(Bin r, ElfObject obj) {
        return r.u16(obj.elf64 ? 58 : 46);
    }

    public int shnum(Bin r, ElfObject obj) {
        return r.u16(obj.elf64 ? 60 : 48);
    }

    public int shstrndx(Bin r, ElfObject obj) {
        return r.u16(obj.elf64 ? 62 : 50);
    }

    private void parseSections(Bin r, ElfObject obj) {
        long shoff = shoff(r, obj);
        int entsize = shentsize(r, obj);
        int count = shnum(r, obj);
        int strndx = shstrndx(r, obj);
        if (count == 0) {
            return;
        }
        if (entsize < 1) {
            throw new ElfException("e_shentsize 非法: " + entsize);
        }
        if (shoff < 0 || shoff + (long) count * entsize > r.length()) {
            throw new ElfException("节表头超出文件范围");
        }
        // Extended numbering: real count lives in section 0's sh_size.
        if (count == 0) {
            count = (int) r.u32((int) shoff + (obj.elf64 ? 32 : 20));
        }
        for (int i = 0; i < count; i++) {
            long base = shoff + (long) i * entsize;
            int off = (int) base;
            int nameOff = r.u16(off);
            int type = (int) r.u32(off + 4);
            long flags, addr, offset, size;
            int link, info;
            long addralign, entsz;
            if (obj.elf64) {
                flags = r.u64(off + 8);
                addr = r.u64(off + 16);
                offset = r.u64(off + 24);
                size = r.u64(off + 32);
                link = r.u32(off + 40);
                info = r.u32(off + 44);
                addralign = r.u64(off + 48);
                entsz = r.u64(off + 56);
            } else {
                flags = r.u32(off + 8);
                addr = r.u32(off + 12);
                offset = r.u32(off + 16);
                size = r.u32(off + 20);
                link = r.u32(off + 24);
                info = r.u32(off + 28);
                addralign = r.u32(off + 32);
                entsz = r.u32(off + 36);
            }
            if (offset > 0 && size > 0 && offset + size > r.length()) {
                throw new ElfException("节 " + i + " 数据超出文件范围");
            }
            obj.sections.add(new ElfSection(i, "", type, flags, addr, offset,
                    size, link, info, addralign, entsz));
        }
        if (strndx == Elf.SHN_XINDEX) {
            strndx = (int) r.u32((int) shoff + (obj.elf64 ? 40 : 24));
        }
        for (ElfSection sec : obj.sections) {
            int nameOff = r.u16((int) (shoff + (long) sec.index * entsize));
            sec.name = sectionName(r, obj, strndx, nameOff);
        }
    }

    private String sectionName(Bin r, ElfObject obj, int strndx, int strOff) {
        if (strndx <= 0 || strndx >= obj.sections.size()) {
            return "sec" + strOff;
        }
        ElfSection sh = obj.sections.get(strndx);
        if (sh.type != Elf.SHT_STRTAB || strOff < 0 || strOff >= sh.size) {
            return "sec" + strOff;
        }
        return r.cstr((int) sh.offset + strOff, (int) sh.size);
    }

    private String stringAt(Bin r, ElfSection strtab, int strOff) {
        if (strtab == null) {
            throw new ElfException("缺少字符串表");
        }
        if (strOff < 0 || strOff >= strtab.size) {
            throw new ElfException("字符串索引越界: " + strOff);
        }
        return r.cstr((int) strtab.offset + strOff, (int) strtab.size);
    }

    // ---- symbol tables -------------------------------------------------

    private void parseSymbols(Bin r, ElfObject obj) {
        for (ElfSection sec : obj.sections) {
            if (sec.type != Elf.SHT_SYMTAB && sec.type != 11 /*SHT_DYNSYM*/) {
                continue;
            }
            parseOneSymtab(r, obj, sec);
        }
    }

    private void parseOneSymtab(Bin r, ElfObject obj, ElfSection symSec) {
        ElfSection strSec = symSec.link > 0 && symSec.link < obj.sections.size()
                ? obj.sections.get(symSec.link) : null;
        if (strSec == null || strSec.type != Elf.SHT_STRTAB) {
            throw new ElfException(symSec.shortDesc() + " 的 sh_link 未指向字符串表");
        }
        int entsize = (int) Math.max(symSec.entrySize, obj.elf64 ? 24 : 16);
        if (symSec.size < 0 || symSec.size % entsize != 0) {
            throw new ElfException(symSec.shortDesc() + " 大小与表项宽度不一致");
        }
        int count = (int) (symSec.size / entsize);
        ElfSection xndx = sectionByType(obj, Elf.SHT_SYMTAB_SHNDX);
        Symbol.Table table = symSec.type == Elf.SHT_SYMTAB
                ? Symbol.Table.STATIC : Symbol.Table.DYNAMIC;
        List<Symbol> target = table == Symbol.Table.STATIC
                ? obj.symtab : obj.dynsym;
        for (int i = 0; i < count; i++) {
            long base = symSec.offset + (long) i * entsize;
            int o = (int) base;
            int nameOff = (int) r.u32(o);
            long value, size;
            int info, other, shndx;
            if (obj.elf64) {
                info = r.u8(o + 4) & 0xff;
                other = r.u8(o + 5) & 0xff;
                shndx = r.u16(o + 6);
                value = r.u64(o + 8);
                size = r.u64(o + 16);
            } else {
                value = r.u32(o + 4);
                size = r.u32(o + 8);
                info = r.u8(o + 12) & 0xff;
                other = r.u8(o + 13) & 0xff;
                shndx = r.u16(o + 14);
            }
            int binding = info >> 4;
            int type = info & 0xf;
            int vis = other & 0x3;
            String name = i == 0 ? "" : stringAt(r, strSec, nameOff);
            if (shndx == Elf.SHN_XINDEX) {
                shndx = extendedSectionIndex(r, xndx, i);
            }
            Symbol sym = new Symbol(i, table, name, value, size, binding,
                    type, vis, shndx);
            target.add(sym);
        }
    }

    private int extendedSectionIndex(Bin r, ElfSection xndx, int symIndex) {
        if (xndx == null) {
            throw new ElfException("符号引用 SHN_XINDEX 但缺少 SHT_SYMTAB_SHNDX");
        }
        long off = xndx.offset + (long) symIndex * 4;
        if (off + 4 > xndx.offset + xndx.size) {
            throw new ElfException("SHT_SYMTAB_SHNDX 索引越界");
        }
        return (int) r.u32((int) off);
    }

    private void markSectionGroups(ElfObject obj) {
        for (ElfSection sec : obj.sections) {
            if ((sec.flags & Elf.SHF_GROUP) == 0 || sec.info <= 0) {
                continue;
            }
            // sh_info points at the SHT_GROUP header section that carries
            // the group; resolve membership in parseGroups.
            SectionGroup g = findGroupFor(obj, sec);
            sec.groupIndex = g == null ? null : g.sectionIndex;
        }
    }

    // ---- section groups (COMDAT) --------------------------------------

    private void parseGroups(Bin r, ElfObject obj) {
        for (ElfSection sec : obj.sections) {
            if (sec.type != Elf.SHT_GROUP) {
                continue;
            }
            if (sec.link <= 0 || sec.link >= obj.sections.size()) {
                throw new ElfException(sec.shortDesc() + " 的 sh_link 非法");
            }
            ElfSection symSec = obj.sections.get(sec.link);
            if (sec.size < 4 || sec.size % 4 != 0) {
                throw new ElfException(sec.shortDesc() + " 组大小非法");
            }
            int count = (int) (sec.size / 4);
            int flags = (int) r.u32((int) sec.offset);
            int sigIdx = (int) r.u32((int) sec.offset + 4);
            List<Integer> members = new ArrayList<>();
            for (int i = 2; i < count; i++) {
                int m = (int) r.u32((int) sec.offset + i * 4);
                if (m < 0 || m >= obj.sections.size()) {
                    throw new ElfException(sec.shortDesc() + " 成员节索引越界");
                }
                members.add(m);
            }
            String signature = groupSignature(r, obj, symSec, sigIdx);
            obj.groups.add(new SectionGroup(sec.index, flags, signature,
                    members));
        }
    }

    private String groupSignature(Bin r, ElfObject obj, ElfSection symSec,
                                  int symIdx) {
        List<Symbol> table = symSec.type == Elf.SHT_SYMTAB ? obj.symtab
                : obj.dynsym;
        if (symIdx <= 0 || symIdx >= table.size()) {
            throw new ElfException("组签名符号索引越界: " + symIdx);
        }
        return table.get(symIdx).name;
    }

    // ---- dynamic section -----------------------------------------------

    private void parseDynamic(Bin r, ElfObject obj) {
        ElfSection dyn = sectionByType(obj, Elf.SHT_DYNAMIC);
        if (dyn == null) {
            return;
        }
        ElfSection strSec = dyn.link > 0 && dyn.link < obj.sections.size()
                ? obj.sections.get(dyn.link) : null;
        int entsize = (int) Math.max(dyn.entrySize, obj.elf64 ? 16 : 8);
        if (dyn.size % entsize != 0) {
            throw new ElfException("SHT_DYNAMIC 大小非法");
        }
        int count = (int) (dyn.size / entsize);
        for (int i = 0; i < count; i++) {
            int o = (int) (dyn.offset + (long) i * entsize);
            long tag = obj.elf64 ? r.u64(o) : r.u32(o);
            long val = obj.elf64 ? r.u64(o + 8) : r.u32(o + 4);
            if (tag == Elf.DT_NULL) {
                break;
            }
            if (tag == Elf.DT_NEEDED || tag == Elf.DT_SONAME) {
                String name = stringAt(r, strSec, (int) val);
                if (tag == Elf.DT_NEEDED) {
                    obj.needed.add(name);
                } else {
                    obj.soname = name;
                }
            } else if (tag == Elf.DT_SYMBOLIC
                    || (tag == Elf.DT_FLAGS && (val & Elf.DF_SYMBOLIC) != 0)) {
                obj.symbolic = true;
            }
        }
    }

    // ---- GNU symbol versions -------------------------------------------

    private void parseVersions(Bin r, ElfObject obj) {
        ElfSection verdef = sectionByType(obj, Elf.SHT_GNU_verdef);
        if (verdef != null) {
            parseVerdefs(r, obj, verdef);
        }
        ElfSection verneed = sectionByType(obj, Elf.SHT_GNU_verneed);
        if (verneed != null) {
            parseVerneeds(r, obj, verneed);
        }
        ElfSection versym = sectionByType(obj, Elf.SHT_GNU_versym);
        if (versym != null) {
            applyVersym(r, obj, versym);
        }
    }

    private void parseVerdefs(Bin r, ElfObject obj, ElfSection sec) {
        ElfSection strSec = sec.link > 0 && sec.link < obj.sections.size()
                ? obj.sections.get(sec.link) : null;
        long p = sec.offset;
        long end = sec.offset + sec.size;
        while (p < end) {
            int o = (int) p;
            int version = r.u16(o);
            if (version != 1) {
                throw new ElfException("不支持的 verdef 版本: " + version);
            }
            int flags = r.u16(o + 2);
            int ndx = r.u16(o + 4);
            int cnt = r.u16(o + 6);
            long aux = r.u32(o + 8);
            long next = r.u32(o + 12);
            VerDefNode node = new VerDefNode(ndx, flags);
            long ap = p + aux;
            for (int i = 0; i < cnt; i++) {
                int a = (int) ap;
                long nameOff = r.u32(a);
                long auxNext = r.u32(a + 4);
                node.names.add(stringAt(r, strSec, (int) nameOff));
                if (auxNext == 0) {
                    break;
                }
                ap += auxNext;
            }
            obj.verdefs.add(node);
            if (next == 0) {
                break;
            }
            p += next;
        }
    }

    private void parseVerneeds(Bin r, ElfObject obj, ElfSection sec) {
        ElfSection strSec = sec.link > 0 && sec.link < obj.sections.size()
                ? obj.sections.get(sec.link) : null;
        long p = sec.offset;
        long end = sec.offset + sec.size;
        while (p < end) {
            int o = (int) p;
            int version = r.u16(o);
            if (version != 1) {
                throw new ElfException("不支持的 verneed 版本: " + version);
            }
            int cnt = r.u16(o + 2);
            long fileOff = r.u32(o + 4);
            long aux = r.u32(o + 8);
            long next = r.u32(o + 12);
            String soname = stringAt(r, strSec, (int) fileOff);
            long ap = p + aux;
            for (int i = 0; i < cnt; i++) {
                int a = (int) ap;
                long hash = r.u32(a);
                int flags = r.u16(a + 4);
                int ndx = r.u16(a + 6);
                long nameOff = r.u32(a + 8);
                long auxNext = r.u32(a + 12);
                obj.verneeds.add(new VerNeedEntry(ndx, flags,
                        stringAt(r, strSec, (int) nameOff), soname));
                if (auxNext == 0) {
                    break;
                }
                ap += auxNext;
            }
            if (next == 0) {
                break;
            }
            p += next;
        }
    }

    private void applyVersym(Bin r, ElfObject obj, ElfSection sec) {
        // .gnu.version has one Elf64_Half per .dynsym entry, in order.
        int count = (int) (sec.size / 2);
        Map<Integer, VerDefNode> defByIndex = new HashMap<>();
        for (VerDefNode d : obj.verdefs) {
            defByIndex.put(d.index, d);
        }
        Map<Integer, VerNeedEntry> needByIndex = new HashMap<>();
        for (VerNeedEntry n : obj.verneeds) {
            needByIndex.put(n.index, n);
        }
        if (count != obj.dynsym.size()) {
            throw new ElfException(".gnu.version 项数(" + count
                    + ")与 .dynsym 项数(" + obj.dynsym.size() + ")不一致");
        }
        for (int i = 0; i < count; i++) {
            int raw = r.u16((int) (sec.offset + i * 2L));
            int ndx = raw & ~Elf.VERSYM_HIDDEN;
            boolean hiddenBit = (raw & Elf.VERSYM_HIDDEN) != 0;
            Symbol sym = obj.dynsym.get(i);
            sym.versionRaw = raw;
            sym.versionHidden = hiddenBit;
            VerDefNode def = defByIndex.get(ndx);
            if (def != null) {
                sym.defVersion = def.primary();
                sym.versionFlags = def.flags;
                List<String> aliases = new ArrayList<>(def.names);
                aliases.remove(0);
                sym.defAliases = aliases;
            } else {
                VerNeedEntry need = needByIndex.get(ndx);
                if (need != null) {
                    sym.needVersion = need.name;
                    sym.needFrom = need.fromSoname;
                    sym.versionFlags = need.flags;
                }
            }
        }
    }

    // ---- relocations ---------------------------------------------------

    private void parseRelocs(Bin r, ElfObject obj) {
        for (ElfSection sec : obj.sections) {
            if (sec.type != Elf.SHT_RELA && sec.type != Elf.SHT_REL) {
                continue;
            }
            if (sec.link <= 0 || sec.link >= obj.sections.size()) {
                throw new ElfException(sec.shortDesc() + " 的 sh_link 非法");
            }
            ElfSection symSec = obj.sections.get(sec.link);
            boolean rela = sec.type == Elf.SHT_RELA;
            int entsize = (int) Math.max(sec.entrySize,
                    obj.elf64 ? (rela ? 24 : 16) : (rela ? 12 : 8));
            if (sec.size % entsize != 0) {
                throw new ElfException(sec.shortDesc() + " 大小非法");
            }
            int count = (int) (sec.size / entsize);
            int symCount = symSec.type == Elf.SHT_SYMTAB
                    ? obj.symtab.size() : obj.dynsym.size();
            ElfSection target = sec.info > 0 && sec.info < obj.sections.size()
                    ? obj.sections.get(sec.info) : null;
            String targetName = target != null ? target.name
                    : ("sec" + sec.info);
            List<Reloc> sink = sec.alloc() ? obj.dynamicRelocs
                    : obj.staticRelocs;
            for (int i = 0; i < count; i++) {
                int o = (int) (sec.offset + (long) i * entsize);
                long offset, info, addend = 0;
                if (obj.elf64) {
                    offset = r.u64(o);
                    info = r.u64(o + 8);
                    if (rela) {
                        addend = r.u64(o + 16);
                    }
                } else {
                    offset = r.u32(o);
                    info = r.u32(o + 4);
                    if (rela) {
                        addend = r.u32(o + 8);
                    }
                }
                int symIdx = obj.elf64 ? (int) (info >>> 32) : (int) (info >> 8);
                int type = obj.elf64 ? (int) (info & 0xffffffffL)
                        : (int) (info & 0xff);
                if (symIdx < 0 || symIdx >= symCount) {
                    throw new ElfException(sec.shortDesc()
                            + " 重定位符号索引越界: " + symIdx);
                }
                sink.add(new Reloc(offset, addend, symIdx, type, rela,
                        targetName, sec.info));
            }
        }
    }
}
