package court.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java 的 ELF 解析：支持 ELF32/ELF64、小端/大端，识别 ET_REL/ET_DYN。
 * 不调用任何系统工具。所有访问都经过 ByteReader 边界校验；任何畸形结构都抛
 * ElfException，由 ImportService 隔离到单个对象。
 */
public final class ElfParser {

    private static final int EI_CLASS = 4;
    private static final int EI_DATA = 5;
    private static final int ELFCLASS32 = 1;
    private static final int ELFCLASS64 = 2;
    private static final int ELFDATA2LSB = 1;
    private static final int ELFDATA2MSB = 2;

    private static final int SHN_UNDEF = 0;
    private static final int SHN_XINDEX = 0xffff;
    private static final long SHF_GROUP = 0x200L;

    // .dynamic 标签
    private static final long DT_NULL = 0;
    private static final long DT_NEEDED = 1;
    private static final long DT_STRTAB = 5;
    private static final long DT_SYMTAB = 6;
    private static final long DT_STRSZ = 10;
    private static final long DT_SYMENT = 11;
    private static final long DT_SONAME = 14;
    private static final long DT_HASH = 4;
    private static final long DT_GNU_HASH = 0x6ffffef5L;
    private static final long DT_VERSYM = 0x6ffffff0L;
    private static final long DT_VERDEF = 0x6ffffffcL;
    private static final long DT_VERDEFNUM = 0x6ffffffdL;
    private static final long DT_VERNEED = 0x6ffffffeL;
    private static final long DT_VERNEEDNUM = 0x6fffffffL;

    private final byte[] data;

    private ElfParser(byte[] data) {
        this.data = data;
    }

    public static ElfObject parse(byte[] data, String unitId, String displayName) {
        if (data == null || data.length < 16) {
            throw new ElfException("文件过短，不是 ELF（不足 16 字节）");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfException("魔数不匹配，不是 ELF");
        }
        ElfParser p = new ElfParser(data);
        int elfClass = data[EI_CLASS] & 0xff;
        if (elfClass != ELFCLASS32 && elfClass != ELFCLASS64) {
            throw new ElfException("不支持的 EI_CLASS=" + elfClass);
        }
        int dataEncoding = data[EI_DATA] & 0xff;
        if (dataEncoding != ELFDATA2LSB && dataEncoding != ELFDATA2MSB) {
            throw new ElfException("不支持的 EI_DATA=" + dataEncoding);
        }
        ElfObject obj = elfClass == ELFCLASS64 ? p.parse64() : p.parse32();
        obj.unitId = unitId;
        obj.displayName = displayName;
        return obj;
    }

    private ElfObject parse64() {
        boolean le = (data[EI_DATA] & 0xff) == ELFDATA2LSB;
        ByteReader br = new ByteReader(data, le);
        ElfObject obj = new ElfObject();
        obj.elf64 = true;
        obj.elfClass = 2;
        obj.littleEndian = le;
        // ELF64 header
        br.require(0, 64);
        obj.type = br.u16(16);
        obj.machine = br.u16(18);
        obj.machineName = RelocTypes.machineName(obj.machine);
        long shoff = br.u64(0x28);
        int shentsize = br.u16(0x3a);
        int shnum = br.u16(0x3c);
        int shstrndx = br.u16(0x3e);
        parseSections64(obj, br, shoff, shentsize, shnum, shstrndx);
        postParse(obj, br, 64);
        return obj;
    }

    private ElfObject parse32() {
        boolean le = (data[EI_DATA] & 0xff) == ELFDATA2LSB;
        ByteReader br = new ByteReader(data, le);
        ElfObject obj = new ElfObject();
        obj.elf64 = false;
        obj.elfClass = 1;
        obj.littleEndian = le;
        br.require(0, 52);
        obj.type = br.u16(16);
        obj.machine = br.u16(18);
        obj.machineName = RelocTypes.machineName(obj.machine);
        long shoff = br.u32(0x20);
        int shentsize = br.u16(0x2e);
        int shnum = br.u16(0x30);
        int shstrndx = br.u16(0x32);
        parseSections32(obj, br, shoff, shentsize, shnum, shstrndx);
        postParse(obj, br, 32);
        return obj;
    }

    // ---------------------------------------------------------------- 节区头

    private void parseSections64(ElfObject obj, ByteReader br, long shoff, int shentsize,
                                 int shnum, int shstrndx) {
        if (shoff == 0) {
            // 没有节区头（strip 过的共享对象），后续走 program header 回退路径
            return;
        }
        if (shnum == 0) {
            // sh_size of section 0 承载真实数量（ELF 扩展编号约定）
            br.require(shoff, Math.max(shentsize, 64));
            shnum = (int) br.u64(shoff + 32);
        }
        if (shentsize < 64) {
            throw new ElfException("ELF64 sh_entsize 过小: " + shentsize);
        }
        br.require(shoff, (long) shentsize * shnum);
        for (int i = 0; i < shnum; i++) {
            long o = shoff + (long) i * shentsize;
            ElfSection sec = new ElfSection(
                    i,
                    br.u32(o),
                    br.u32(o + 4),
                    br.u64(o + 8),
                    br.u64(o + 16),
                    br.u64(o + 24),
                    br.u64(o + 32),
                    (int) br.u32(o + 40),
                    (int) br.u32(o + 44),
                    br.u64(o + 48),
                    br.u64(o + 56));
            validateSectionRange(sec);
            obj.sections.add(sec);
        }
        if (shstrndx == SHN_XINDEX && !obj.sections.isEmpty()) {
            shstrndx = (int) obj.sections.get(0).link;
        }
        applySectionNames(obj, br, shstrndx);
    }

    private void parseSections32(ElfObject obj, ByteReader br, long shoff, int shentsize,
                                 int shnum, int shstrndx) {
        if (shoff == 0) {
            return;
        }
        if (shnum == 0) {
            br.require(shoff, Math.max(shentsize, 40));
            shnum = (int) br.u32(shoff + 20);
        }
        if (shentsize < 40) {
            throw new ElfException("ELF32 sh_entsize 过小: " + shentsize);
        }
        br.require(shoff, (long) shentsize * shnum);
        for (int i = 0; i < shnum; i++) {
            long o = shoff + (long) i * shentsize;
            ElfSection sec = new ElfSection(
                    i,
                    br.u32(o),
                    br.u32(o + 4),
                    br.u32(o + 8),
                    br.u32(o + 12),
                    br.u32(o + 16),
                    br.u32(o + 20),
                    br.u16(o + 24),
                    br.u16(o + 28),
                    br.u32(o + 32),
                    br.u32(o + 36));
            validateSectionRange(sec);
            obj.sections.add(sec);
        }
        if (shstrndx == SHN_XINDEX && !obj.sections.isEmpty()) {
            shstrndx = obj.sections.get(0).link;
        }
        applySectionNames(obj, br, shstrndx);
    }

    private void validateSectionRange(ElfSection sec) {
        if (sec.type != 8 /* SHT_NOBITS */ && sec.size > 0) {
            if (sec.offset < 0 || sec.offset > data.length
                    || sec.size > data.length - sec.offset) {
                throw new ElfException("section[" + sec.index + "] 数据越界: offset="
                        + sec.offset + " size=" + sec.size);
            }
        }
        if (sec.link < 0 || sec.info < 0) {
            throw new ElfException("section[" + sec.index + "] link/info 非法");
        }
    }

    private void applySectionNames(ElfObject obj, ByteReader br, int shstrndx) {
        if (shstrndx <= 0 || shstrndx >= obj.sections.size()) {
            return;
        }
        ElfSection str = obj.sections.get(shstrndx);
        for (ElfSection sec : obj.sections) {
            if (sec.nameOffset < str.size) {
                sec.name = br.cstr(str.offset + sec.nameOffset, str.size - sec.nameOffset);
            }
        }
    }

    // ---------------------------------------------------------------- 解析后编排

    private void postParse(ElfObject obj, ByteReader br, int cls) {
        if (obj.sections.isEmpty()) {
            parseFromProgramHeaders(obj, br, cls);
            return;
        }
        parseDynamic(obj, br, cls);
        Map<Integer, Integer> xindex = parseSymtabShndx(obj, br, cls);
        parseSymbolTables(obj, br, cls, xindex);
        parseGroups(obj, br);
        parseVersions(obj, br, cls);
        parseRelocs(obj, br, cls);
    }

    private ElfSection findSection(ElfObject obj, long type) {
        for (ElfSection s : obj.sections) {
            if (s.type == type) {
                return s;
            }
        }
        return null;
    }

    private List<ElfSection> findSections(ElfObject obj, long type) {
        List<ElfSection> out = new ArrayList<>();
        for (ElfSection s : obj.sections) {
            if (s.type == type) {
                out.add(s);
            }
        }
        return out;
    }

    private String readStrtabAt(ByteReader br, ElfSection str, long off) {
        if (str == null || off < 0 || off >= str.size) {
            return "";
        }
        return br.cstr(str.offset + off, str.size - off);
    }

    // ---------------------------------------------------------------- SHT_GROUP

    private void parseGroups(ElfObject obj, ByteReader br) {
        for (ElfSection sec : obj.sections) {
            if (sec.type != ElfSection.SHT_GROUP || sec.size < 4) {
                continue;
            }
            if (sec.size % 4 != 0) {
                throw new ElfException("group section " + sec.index + " 长度不是 4 的倍数");
            }
            long flags = br.u32(sec.offset) & 0xffffffffL;
            int count = (int) (sec.size / 4) - 1;
            List<Integer> members = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int member = (int) br.u32(sec.offset + 4 + 4L * i);
                if (member < 0 || member >= obj.sections.size()) {
                    throw new ElfException("group section " + sec.index + " 成员索引越界: " + member);
                }
                members.add(member);
            }
            String sig = "";
            int sigIdx = sec.info;
            // 组签名字符名来自关联符号表的 sh_info 指向的符号
            ElfSection symtab = obj.sectionAt(sec.link);
            if (symtab != null) {
                for (ElfSymbol sym : obj.symbols) {
                    if (sym.dynamic == (symtab.type == ElfSection.SHT_DYNSYM) && sym.index == sigIdx) {
                        sig = sym.name;
                        break;
                    }
                }
            }
            obj.groups.add(new ComdatGroup(sig, (flags & ComdatGroup.GRP_COMDAT) != 0, sigIdx, members));
        }
    }

    // SHT_SYMTAB_SHNDX: 扩展 section 索引表（符号数 == 关联 symtab 符号数）
    private Map<Integer, Integer> parseSymtabShndx(ElfObject obj, ByteReader br, int cls) {
        Map<Integer, Integer> xindex = new LinkedHashMap<>();
        for (ElfSection sec : obj.sections) {
            if (sec.type != 18 /* SHT_SYMTAB_SHNDX */) {
                continue;
            }
            ElfSection symtab = obj.sectionAt(sec.link);
            if (symtab == null) {
                continue;
            }
            int ents = cls == 64 ? 24 : 16;
            int n = (int) (symtab.size / Math.max(symtab.entsize, ents));
            long words = sec.size / 4L;
            for (int i = 0; i < Math.min(n, words); i++) {
                long val = br.u32(sec.offset + 4L * i);
                if (val != 0) {
                    xindex.put(i, (int) val);
                }
            }
        }
        return xindex;
    }

    // ---------------------------------------------------------------- 符号表

    private void parseSymbolTables(ElfObject obj, ByteReader br, int cls, Map<Integer, Integer> xindex) {
        for (ElfSection sec : obj.sections) {
            if (sec.type != ElfSection.SHT_SYMTAB && sec.type != ElfSection.SHT_DYNSYM) {
                continue;
            }
            boolean dyn = sec.type == ElfSection.SHT_DYNSYM;
            ElfSection str = obj.sectionAt(sec.link);
            if (str == null) {
                throw new ElfException("符号表 section " + sec.index + " 的 link 指向无效字符串表 " + sec.link);
            }
            long ents = sec.entsize > 0 ? sec.entsize : (cls == 64 ? 24 : 16);
            if (ents < (cls == 64 ? 24 : 16)) {
                throw new ElfException("符号表 entsize 过小: section " + sec.index);
            }
            long count = sec.size / ents;
            if ((long) (int) count != count) {
                throw new ElfException("符号数量溢出");
            }
            for (int i = 0; i < count; i++) {
                long o = sec.offset + i * ents;
                int nameOff;
                int info;
                int other;
                int shndx;
                long value;
                long size;
                if (cls == 64) {
                    nameOff = (int) br.u32(o);
                    info = br.u8(o + 4);
                    other = br.u8(o + 5);
                    shndx = br.u16(o + 6);
                    value = br.u64(o + 8);
                    size = br.u64(o + 16);
                } else {
                    nameOff = (int) br.u32(o);
                    value = br.u32(o + 4);
                    size = br.u32(o + 8);
                    info = br.u8(o + 12);
                    other = br.u8(o + 13);
                    shndx = br.u16(o + 14);
                }
                if (shndx == SHN_XINDEX && xindex.get(i) != null) {
                    shndx = xindex.get(i);
                }
                String name = readStrtabAt(br, str, nameOff & 0xffffffffL);
                int bind = info >> 4;
                int type = info & 0xf;
                int vis = other & 0x3;
                ElfSymbol sym = new ElfSymbol(i, dyn, name, bind, type, vis, shndx,
                        value, size, 0, null);
                // section 归属必须合法（特殊段索引或真实 section）
                if (shndx != SHN_UNDEF && shndx != ElfSymbol.SHN_ABS
                        && shndx != ElfSymbol.SHN_COMMON && shndx != 0xfff0 /* SHN_LORESERVE.. */
                        && shndx < 0xff00 && shndx >= obj.sections.size()) {
                    throw new ElfException("符号 " + safeName(name) + " 的 st_shndx 越界: " + shndx);
                }
                obj.symbols.add(sym);
            }
        }
    }

    private static String safeName(String s) {
        return s == null || s.isEmpty() ? "<noname>" : s;
    }

    // ---------------------------------------------------------------- .dynamic

    private void parseDynamic(ElfObject obj, ByteReader br, int cls) {
        ElfSection dyn = findSection(obj, ElfSection.SHT_DYNAMIC);
        if (dyn == null || dyn.size <= 0) {
            return;
        }
        long ents = dyn.entsize > 0 ? dyn.entsize : (cls == 64 ? 16 : 8);
        ElfSection dynstr = obj.sectionAt(dyn.link);
        long sonameOff = -1;
        for (long o = dyn.offset; o < dyn.offset + dyn.size; o += ents) {
            if (o + ents > dyn.offset + dyn.size) {
                throw new ElfException(".dynamic 条目越过节区边界");
            }
            long tag;
            long val;
            if (cls == 64) {
                tag = br.u64(o);
                val = br.u64(o + 8);
            } else {
                tag = br.u32(o);
                val = br.u32(o);
            }
            if (tag == DT_NULL) {
                break;
            } else if (tag == DT_NEEDED) {
                obj.needed.add(readStrtabAt(br, dynstr, val));
            } else if (tag == DT_SONAME) {
                sonameOff = val;
            }
        }
        if (sonameOff >= 0 && dynstr != null) {
            obj.soname = readStrtabAt(br, dynstr, sonameOff);
        }
    }

    // ---------------------------------------------------------------- 版本节

    private void parseVersions(ElfObject obj, ByteReader br, int cls) {
        // 版本节优先用 section header 定位，失败再退回 .dynamic
        ElfSection verdef = findNamedSection(obj, ".gnu.version_d");
        ElfSection verneed = findNamedSection(obj, ".gnu.version_r");
        ElfSection versym = findNamedSection(obj, ".gnu.version");
        if (verdef == null || verneed == null) {
            for (ElfSection sec : obj.sections) {
                if (sec.type == 0x6ffffffdL /* SHT_GNU_VERDEF */) {
                    verdef = verdef == null ? sec : verdef;
                }
                if (sec.type == 0x6ffffffeL /* SHT_GNU_VERNEED */) {
                    verneed = verneed == null ? sec : verneed;
                }
                if (sec.type == 0x6fffffffL /* SHT_GNU_VERSYM */) {
                    versym = versym == null ? sec : versym;
                }
            }
        }
        if (verdef != null) {
            parseVerdef(obj, br, verdef, cls);
        }
        if (verneed != null) {
            parseVerneed(obj, br, verneed, cls);
        }
        if (versym != null) {
            applyVersym(obj, br, versym);
        }
    }

    private ElfSection findNamedSection(ElfObject obj, String name) {
        for (ElfSection s : obj.sections) {
            if (name.equals(s.name)) {
                return s;
            }
        }
        return null;
    }

    private void parseVerdef(ElfObject obj, ByteReader br, ElfSection sec, int cls) {
        // Elf64_Verdef 与 Elf32_Verdef 同构；aux 为同节内偏移
        long end = sec.offset + sec.size;
        long off = sec.offset;
        while (off + 20 <= end) {
            long vdVersion = br.u16(off) & 0xffffL;
            long vdFlags = br.u16(off + 2) & 0xffffL;
            long vdNdx = br.u16(off + 4) & 0xffffL;
            long vdCnt = br.u16(off + 6) & 0xffffL;
            long vdHash = br.u32(off + 8);
            long vdAux = br.u32(off + 12);
            long vdNext = br.u32(off + 16);
            if (vdVersion == 0 || vdNdx == 0) {
                throw new ElfException("Verdef 版本/索引非法 @offset=" + off);
            }
            VersionNode node = new VersionNode((int) vdNdx);
            node.definition = true;
            node.base = (vdFlags & 1 /* VER_FLG_BASE */) != 0;
            long auxOff = off + vdAux;
            for (long a = 0; a < vdCnt && auxOff + 8 <= end; a++) {
                long vnaName = br.u32(auxOff + 4);
                long vnaNext = br.u32(auxOff);
                ElfSection dynstr = obj.sectionAt(sec.link);
                String vname = readStrtabAt(br, dynstr, vnaName);
                if (a == 0) {
                    node.name = vname;
                } else {
                    node.aliases.add(vname);
                }
                if (vnaNext == 0) {
                    break;
                }
                auxOff += vnaNext;
            }
            obj.versionsByIndex.put(node.index, node);
            if (vdNext == 0) {
                break;
            }
            off += vdNext;
        }
    }

    private void parseVerneed(ElfObject obj, ByteReader br, ElfSection sec, int cls) {
        long end = sec.offset + sec.size;
        long off = sec.offset;
        ElfSection dynstr = obj.sectionAt(sec.link);
        while (off + 16 <= end) {
            long vnVersion = br.u16(off) & 0xffffL;
            long vnCnt = br.u16(off + 2) & 0xffffL;
            long vnFile = br.u32(off + 4);
            long vnAux = br.u32(off + 8);
            long vnNext = br.u32(off + 12);
            if (vnVersion == 0) {
                throw new ElfException("Verneed vn_version 非法 @offset=" + off);
            }
            String fileName = readStrtabAt(br, dynstr, vnFile);
            long auxOff = off + vnAux;
            for (long a = 0; a < vnCnt && auxOff + 16 <= end; a++) {
                long vnaHash = br.u32(auxOff);
                long vnaFlags = br.u16(auxOff + 4) & 0xffffL;
                long vnaOther = br.u16(auxOff + 6) & 0xffffL;
                long vnaName = br.u32(auxOff + 8);
                long vnaNext = br.u32(auxOff + 12);
                VersionNode node = new VersionNode((int) vnaOther);
                node.definition = false;
                node.weakNeed = (vnaFlags & 2 /* VER_FLG_WEAK */) != 0;
                node.name = readStrtabAt(br, dynstr, vnaName);
                node.neededFile = fileName;
                node.neededFileIndex = (int) vnFile;
                obj.versionsByIndex.put(node.index, node);
                if (vnaNext == 0) {
                    break;
                }
                auxOff += vnaNext;
            }
            if (vnNext == 0) {
                break;
            }
            off += vnNext;
        }
    }

    private void applyVersym(ElfObject obj, ByteReader br, ElfSection versym) {
        // versym 与 .dynsym 逐项对应；一个 ELF 至多一个 dynsym
        ElfSection dynsym = findSection(obj, ElfSection.SHT_DYNSYM);
        if (dynsym == null) {
            return;
        }
        long words = versym.size / 2;
        for (ElfSymbol sym : obj.symbols) {
            if (!sym.dynamic || sym.index >= words) {
                continue;
            }
            int raw = br.u16(versym.offset + 2L * sym.index);
            int ndx = raw & 0x7fff;
            VersionNode node = obj.versionsByIndex.get(ndx);
            if (node != null && (raw & 0x8000) != 0) {
                node.nonDefault = true;
            }
            sym.setVersion(raw, node);
        }
    }


    // ---------------------------------------------------------------- 重定位

    private void parseRelocs(ElfObject obj, ByteReader br, int cls) {
        for (ElfSection sec : obj.sections) {
            if (sec.type != ElfSection.SHT_REL && sec.type != ElfSection.SHT_RELA) {
                continue;
            }
            boolean rela = sec.type == ElfSection.SHT_RELA;
            long ents = sec.entsize > 0 ? sec.entsize : (cls == 64
                    ? (rela ? 24 : 16) : (rela ? 12 : 8));
            long minEnts = cls == 64 ? (rela ? 24 : 16) : (rela ? 12 : 8);
            if (ents < minEnts) {
                throw new ElfException("重定位节 " + sec.index + " entsize 过小");
            }
            ElfSection target = obj.sectionAt(sec.info);
            String targetName = target != null ? target.name : ("sh#" + sec.info);
            long count = sec.size / ents;
            for (long i = 0; i < count; i++) {
                long o = sec.offset + i * ents;
                long offset;
                long info;
                long addend = 0;
                if (cls == 64) {
                    offset = br.u64(o);
                    info = br.u64(o + 8);
                    if (rela) {
                        addend = br.u64(o + 16);
                    }
                } else {
                    offset = br.u32(o);
                    info = br.u32(o + 4);
                    if (rela) {
                        addend = br.u32(o + 8);
                    }
                }
                int symIdx = cls == 64 ? (int) (info >>> 32) : (int) (info >>> 8);
                int type = cls == 64 ? (int) info : (int) (info & 0xff);
                String tn = RelocTypes.name(obj.machine, type);
                obj.relocs.add(new ElfReloc(offset, info, addend, rela, cls,
                        tn, targetName, sec.index));
                if (symIdx < 0) {
                    throw new ElfException("重定位节 " + sec.index + " 符号索引非法");
                }
            }
        }
    }

    // ----------------------- 无节区头回退：program header + PT_DYNAMIC

    private static final int PT_LOAD = 1;
    private static final int PT_DYNAMIC = 2;

    private void parseFromProgramHeaders(ElfObject obj, ByteReader br, int cls) {
        int phoffOff = cls == 64 ? 0x20 : 0x1c;
        int phentsizeOff = cls == 64 ? 0x36 : 0x2a;
        int phnumOff = cls == 64 ? 0x38 : 0x2c;
        long phoff = cls == 64 ? br.u64(phoffOff) : br.u32(phoffOff);
        int phentsize = br.u16(phentsizeOff);
        int phnum = br.u16(phnumOff);
        if (phoff == 0 || phnum == 0) {
            return; // 无节区头也无 program header：无法解析，返回空对象（不抛异常，仅隔离信息）
        }
        int minPh = cls == 64 ? 56 : 32;
        if (phentsize < minPh) {
            throw new ElfException("program header entsize 过小: " + phentsize);
        }
        br.require(phoff, (long) phentsize * phnum);
        List<long[]> loads = new ArrayList<>(); // [vaddr, offset, filesz]
        long dynVaddr = -1;
        long dynFilesz = 0;
        for (int i = 0; i < phnum; i++) {
            long o = phoff + (long) i * phentsize;
            long type = cls == 64 ? br.u32(o) : br.u32(o);
            if (type == PT_LOAD) {
                long vaddr = cls == 64 ? br.u64(o + 16) : br.u32(o + 8);
                long foff = cls == 64 ? br.u64(o + 8) : br.u32(o + 4);
                long filesz = cls == 64 ? br.u64(o + 32) : br.u32(o + 16);
                loads.add(new long[]{vaddr, foff, filesz});
            } else if (type == PT_DYNAMIC) {
                dynVaddr = cls == 64 ? br.u64(o + 16) : br.u32(o + 8);
                dynFilesz = cls == 64 ? br.u64(o + 32) : br.u32(o + 16);
            }
        }
        if (dynVaddr < 0) {
            return;
        }
        long dynOff = vaddrToOffset(loads, dynVaddr, dynFilesz);
        parseDynamicFallback(obj, br, cls, loads, dynOff, dynFilesz);
    }

    private long vaddrToOffset(List<long[]> loads, long vaddr, long need) {
        for (long[] l : loads) {
            long base = l[0];
            long foff = l[1];
            long filesz = l[2];
            if (vaddr >= base && vaddr - base + need <= filesz) {
                return foff + (vaddr - base);
            }
        }
        throw new ElfException("虚拟地址无法映射到文件: vaddr=0x" + Long.toHexString(vaddr));
    }

    private void parseDynamicFallback(ElfObject obj, ByteReader br, int cls, List<long[]> loads,
                                      long dynOff, long dynFilesz) {
        long ents = cls == 64 ? 16 : 8;
        long symtab = -1;
        long strtab = -1;
        long strsz = 0;
        long syment = 0;
        long hash = -1;
        long gnuHash = -1;
        long versymAddr = -1;
        long verdefAddr = -1;
        long verdefNum = 0;
        long verneedAddr = -1;
        long verneedNum = 0;
        for (long o = dynOff; o + ents <= dynOff + dynFilesz; o += ents) {
            long tag = cls == 64 ? br.u64(o) : br.u32(o);
            long val = cls == 64 ? br.u64(o + 8) : br.u32(o + 4);
            if (tag == DT_NULL) {
                break;
            }
            if (tag == DT_NEEDED) {
                obj.needed.add(String.valueOf(val)); // 暂存偏移，拿到 strtab 后还原
            } else if (tag == DT_SONAME) {
                obj.soname = String.valueOf(val);
            } else if (tag == DT_SYMTAB) {
                symtab = val;
            } else if (tag == DT_STRTAB) {
                strtab = val;
            } else if (tag == DT_STRSZ) {
                strsz = val;
            } else if (tag == DT_SYMENT) {
                syment = val;
            } else if (tag == DT_HASH) {
                hash = val;
            } else if (tag == DT_GNU_HASH) {
                gnuHash = val;
            } else if (tag == DT_VERSYM) {
                versymAddr = val;
            } else if (tag == DT_VERDEF) {
                verdefAddr = val;
            } else if (tag == DT_VERDEFNUM) {
                verdefNum = val;
            } else if (tag == DT_VERNEED) {
                verneedAddr = val;
            } else if (tag == DT_VERNEEDNUM) {
                verneedNum = val;
            }
        }
        if (strtab < 0 || symtab < 0) {
            throw new ElfException("PT_DYNAMIC 缺少 DT_SYMTAB/DT_STRTAB");
        }
        long symEnt = syment > 0 ? syment : (cls == 64 ? 24 : 16);
        long strOff = vaddrToOffset(loads, strtab, Math.min(strsz, 1));
        // 还原 NEEDED / SONAME 字符串
        for (int i = 0; i < obj.needed.size(); i++) {
            long sOff = Long.parseLong(obj.needed.get(i));
            obj.needed.set(i, readFallbackString(br, loads, strtab, strsz, sOff));
        }
        if (obj.soname != null) {
            long sOff = Long.parseLong(obj.soname);
            obj.soname = readFallbackString(br, loads, strtab, strsz, sOff);
        }
        long nSym = deriveDynsymCount(br, cls, loads, hash, gnuHash, versymAddr, symtab);
        long tableOff = vaddrToOffset(loads, symtab, symEnt);
        parseFallbackSymbols(obj, br, cls, loads, symtab, strtab, strsz, tableOff, nSym, symEnt);
        parseFallbackVersions(obj, br, cls, loads, verdefAddr, verdefNum, verneedAddr,
                verneedNum, versymAddr, strtab, strsz, nSym);
    }

    private long deriveDynsymCount(ByteReader br, int cls, List<long[]> loads,
                                   long hash, long gnuHash, long versym, long symtab) {
        if (hash >= 0) {
            long off = vaddrToOffset(loads, hash, 8);
            return br.u32(off + 4); // nchain == dynsym 数量
        }
        if (gnuHash >= 0) {
            long off = vaddrToOffset(loads, gnuHash, 16);
            long nbuckets = br.u32(off);
            long symoffset = br.u32(off + 4);
            long bloomSize = br.u32(off + 8);
            long bp = cls == 64 ? 8 : 4;
            long bucketsOff = off + 16 + bloomSize * bp;
            long maxBucket = 0;
            for (long i = 0; i < nbuckets; i++) {
                maxBucket = Math.max(maxBucket, br.u32(bucketsOff + 4 * i));
            }
            if (maxBucket < symoffset) {
                return symoffset; // 没有链时至少到 symoffset
            }
            long chainOff = bucketsOff + 4 * nbuckets;
            long j = maxBucket - symoffset;
            while (true) {
                long val = br.u32(chainOff + 4 * j);
                j++;
                if ((val & 1) != 0) {
                    break;
                }
                if (j > 1_000_000) {
                    throw new ElfException("GNU_HASH 链过长，疑似损坏");
                }
            }
            return symoffset + j + 1;
        }
        if (versym >= 0) {
            // versym 与 dynsym 等长；以 symtab 到 versym 之间的空间估算
            long bytes = versym - symtab;
            if (bytes > 0) {
                return bytes / (cls == 64 ? 24 : 16);
            }
        }
        throw new ElfException("无节区头且缺少 DT_HASH/DT_GNU_HASH，无法确定 dynsym 数量");
    }

    private String readFallbackString(ByteReader br, List<long[]> loads, long strtab,
                                      long strsz, long relOff) {
        if (relOff < 0 || (strsz > 0 && relOff >= strsz)) {
            return "";
        }
        long off = vaddrToOffset(loads, strtab + relOff, 1);
        return br.cstr(off, strsz > 0 ? strsz - relOff : 4096);
    }

    private void parseFallbackSymbols(ElfObject obj, ByteReader br, int cls, List<long[]> loads,
                                      long symtab, long strtab, long strsz, long tableOff,
                                      long nSym, long symEnt) {
        if (symEnt < (cls == 64 ? 24 : 16)) {
            throw new ElfException("DT_SYMENT 过小");
        }
        for (int i = 0; i < nSym; i++) {
            long o = tableOff + i * symEnt;
            int nameOff;
            int info;
            int other;
            int shndx;
            long value;
            long size;
            if (cls == 64) {
                nameOff = (int) br.u32(o);
                info = br.u8(o + 4);
                other = br.u8(o + 5);
                shndx = br.u16(o + 6);
                value = br.u64(o + 8);
                size = br.u64(o + 16);
            } else {
                nameOff = (int) br.u32(o);
                value = br.u32(o + 4);
                size = br.u32(o + 8);
                info = br.u8(o + 12);
                other = br.u8(o + 13);
                shndx = br.u16(o + 14);
            }
            String name = readFallbackString(br, loads, strtab, strsz, nameOff & 0xffffffffL);
            ElfSymbol sym = new ElfSymbol(i, true, name, info >> 4, info & 0xf,
                    other & 3, shndx, value, size, 0, null);
            obj.symbols.add(sym);
        }
    }

    private void parseFallbackVersions(ElfObject obj, ByteReader br, int cls, List<long[]> loads,
                                       long verdefAddr, long verdefNum, long verneedAddr,
                                       long verneedNum, long versymAddr, long strtab,
                                       long strsz, long nSym) {
        // 构造两个轻量“伪 section”复用既有版本解析逻辑
        if (verdefAddr >= 0 && verdefNum > 0) {
            long off = vaddrToOffset(loads, verdefAddr, 20);
            parseVersionChains(obj, br, off, loads, strtab, strsz, true, verdefNum);
        }
        if (verneedAddr >= 0 && verneedNum > 0) {
            long off = vaddrToOffset(loads, verneedAddr, 16);
            parseVersionChains(obj, br, off, loads, strtab, strsz, false, verneedNum);
        }
        if (versymAddr >= 0) {
            long off = vaddrToOffset(loads, versymAddr, 2);
            for (ElfSymbol sym : obj.symbols) {
                if (sym.index >= nSym) {
                    continue;
                }
                int raw = br.u16(off + 2L * sym.index);
                VersionNode node = obj.versionsByIndex.get(raw & 0x7fff);
                if (node != null && (raw & 0x8000) != 0) {
                    node.nonDefault = true;
                }
                sym.setVersion(raw, node);
            }
        }
    }

    private void parseVersionChains(ElfObject obj, ByteReader br, long off, List<long[]> loads,
                                    long strtab, long strsz, boolean def, long chainNum) {
        long cur = off;
        for (long c = 0; c < chainNum; c++) {
            if (def) {
                long flags = br.u16(cur + 2) & 0xffffL;
                long ndx = br.u16(cur + 4) & 0xffffL;
                long cnt = br.u16(cur + 6) & 0xffffL;
                long aux = br.u32(cur + 12);
                long next = br.u32(cur + 16);
                VersionNode node = new VersionNode((int) ndx);
                node.definition = true;
                node.base = (flags & 1) != 0;
                long a = cur + aux;
                for (long k = 0; k < cnt; k++) {
                    long nameOff = br.u32(a + 4);
                    long anext = br.u32(a);
                    String vname = readFallbackString(br, loads, strtab, strsz, nameOff);
                    if (k == 0) {
                        node.name = vname;
                    } else {
                        node.aliases.add(vname);
                    }
                    if (anext == 0) {
                        break;
                    }
                    a += anext;
                }
                obj.versionsByIndex.put(node.index, node);
                if (next == 0) {
                    break;
                }
                cur += next;
            } else {
                long cnt = br.u16(cur + 2) & 0xffffL;
                long fileOff = br.u32(cur + 4);
                long aux = br.u32(cur + 8);
                long next = br.u32(cur + 12);
                String fileName = readFallbackString(br, loads, strtab, strsz, fileOff);
                long a = cur + aux;
                for (long k = 0; k < cnt; k++) {
                    long vnaFlags = br.u16(a + 4) & 0xffffL;
                    long other = br.u16(a + 6) & 0xffffL;
                    long nameOff = br.u32(a + 8);
                    long anext = br.u32(a + 12);
                    VersionNode node = new VersionNode((int) other);
                    node.definition = false;
                    node.weakNeed = (vnaFlags & 2) != 0;
                    node.name = readFallbackString(br, loads, strtab, strsz, nameOff);
                    node.neededFile = fileName;
                    obj.versionsByIndex.put(node.index, node);
                    if (anext == 0) {
                        break;
                    }
                    a += anext;
                }
                if (next == 0) {
                    break;
                }
                cur += next;
            }
        }
    }
}
