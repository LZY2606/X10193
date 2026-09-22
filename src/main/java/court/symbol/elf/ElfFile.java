package court.symbol.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fully parsed ELF object. Parsing is defensive: every offset/length/index is
 * validated against the actual file size. A malformed object throws
 * {@link ElfException} and the engine isolates just that object.
 */
public record ElfFile(byte[] raw,
                      Elf.Header header,
                      List<Elf.Section> sections,
                      List<Elf.SymbolTable> symbolTables,
                      List<Elf.VersionDefinition> versionDefinitions,
                      List<Elf.VersionNeed> versionNeeds,
                      List<Elf.SectionGroup> groups,
                      List<String> needed,
                      String soname) {

    public boolean relocatable() {
        return header.type() == Elf.ET_REL;
    }

    public boolean sharedObject() {
        return header.type() == Elf.ET_DYN;
    }

    public List<Elf.Symbol> symbols() {
        return primaryTable() == null ? List.of() : primaryTable().symbols();
    }

    public Elf.SymbolTable primaryTable() {
        Elf.SymbolTable dyn = null;
        for (Elf.SymbolTable t : symbolTables) {
            if (t.dynamic()) {
                dyn = t;
            } else {
                return t;
            }
        }
        return dyn;
    }

    public Elf.SymbolTable dynamicTable() {
        for (Elf.SymbolTable t : symbolTables) {
            if (t.dynamic()) {
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ parse

    public static ElfFile parse(byte[] data) {
        if (data == null || data.length < Elf.EI_NIDENT) {
            throw new ElfException("文件过小，不是 ELF");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfException("ELF 魔数不匹配");
        }
        int elfClass = data[4] & 0xff;
        if (elfClass != Elf.ELFCLASS32 && elfClass != Elf.ELFCLASS64) {
            throw new ElfException("未知 ELF 类别: " + elfClass);
        }
        int endian = data[5] & 0xff;
        if (endian != Elf.ELFDATA2LSB && endian != Elf.ELFDATA2MSB) {
            throw new ElfException("未知 ELF 字节序: " + endian);
        }
        boolean is64 = elfClass == Elf.ELFCLASS64;
        ByteReader r = new ByteReader(data, endian == Elf.ELFDATA2LSB);

        int type = r.u16(16);
        int machine = r.u16(18);
        long entry = is64 ? r.u64(24) : r.u32(24);

        // section header table
        long shoff = is64 ? r.u64(40) : r.u32(32);
        int shentsize = r.u16(is64 ? 58 : 46);
        int shnum = r.u16(is64 ? 60 : 48);
        int shstrndx = r.u16(is64 ? 62 : 50);
        int expectedEnt = is64 ? 64 : 40;
        if (shentsize < expectedEnt) {
            throw new ElfException("section header 表项过小: " + shentsize);
        }
        if (shnum == 0 && shoff != 0) {
            // extended numbering: real count and name table live in section 0.
            if (shoff > (long) data.length - expectedEnt) {
                throw new ElfException("扩展 section 头越界");
            }
            shnum = (int) (is64 ? r.u64(shoff + 32) : r.u32(shoff + 20));
            long shLink0 = is64 ? r.u64(shoff + 40) : r.u32(shoff + 24);
            if (shstrndx == 0) {
                shstrndx = (int) shLink0;
            }
        }
        if (shnum < 0 || shoff > 0 && shoff > (long) data.length - (long) shentsize * shnum) {
            throw new ElfException("section header 表越界");
        }

        List<Elf.Section> sections = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            long o = shoff + (long) i * shentsize;
            int nameIdx = r.u16(o);
            int stype = (int) r.u32(o + 4);
            long flags, addr, offset, size;
            int link, info;
            long align, entsize;
            if (is64) {
                flags = r.u64(o + 8);
                addr = r.u64(o + 16);
                offset = r.u64(o + 24);
                size = r.u64(o + 32);
                link = (int) r.u32(o + 40);
                info = (int) r.u32(o + 44);
                align = r.u64(o + 48);
                entsize = r.u64(o + 56);
            } else {
                flags = r.u32(o + 8);
                addr = r.u32(o + 12);
                offset = r.u32(o + 16);
                size = r.u32(o + 20);
                link = (int) r.u32(o + 24);
                info = (int) r.u32(o + 28);
                align = r.u32(o + 32);
                entsize = r.u32(o + 36);
            }
            sections.add(new Elf.Section(i, nameIdx, "", stype, flags, addr, offset, size,
                    link, info, align, entsize));
        }

        StringTable sectionNames = StringTable.of(r, sectionAt(sections, shstrndx));
        List<Elf.Section> named = new ArrayList<>();
        for (Elf.Section s : sections) {
            String n = sectionNames.at(s.nameIndex());
            named.add(new Elf.Section(s.index(), s.nameIndex(), n, s.type(), s.flags(),
                    s.addr(), s.offset(), s.size(), s.link(), s.info(),
                    s.alignment(), s.entrySize()));
        }

        // versions must be resolved before symbols (they annotate each symbol)
        Map<Integer, String> verDefNames = new LinkedHashMap<>();
        Map<Integer, Integer> verDefFlags = new LinkedHashMap<>();
        List<Elf.VersionDefinition> verDefs = parseVerDef(r, named, verDefNames, verDefFlags);
        Map<Integer, Elf.VersionNeed> verNeedsByNdx = new LinkedHashMap<>();
        List<Elf.VersionNeed> verNeeds = parseVerNeed(r, named, verNeedsByNdx);
        int[] versym = parseVersym(r, named);

        List<Elf.SymbolTable> tables = new ArrayList<>();
        for (Elf.Section s : named) {
            if (s.type() == Elf.SHT_SYMTAB || s.type() == Elf.SHT_DYNSYM) {
                tables.add(parseSymbolTable(r, named, s, verDefNames, verDefFlags,
                        verNeedsByNdx, versym, is64));
            }
        }

        List<Elf.SectionGroup> groups = parseGroups(r, named, is64);
        List<String> needed = new ArrayList<>();
        String[] soname = {null};
        parseDynamic(r, named, is64, needed, soname);

        Elf.Header header = new Elf.Header(is64, endian == Elf.ELFDATA2LSB, type, machine,
                entry, shstrndx);
        return new ElfFile(data, header, List.copyOf(named), List.copyOf(tables),
                List.copyOf(verDefs), List.copyOf(verNeeds), List.copyOf(groups),
                List.copyOf(needed), soname[0]);
    }

    private static Elf.Section sectionAt(List<Elf.Section> sections, int index) {
        if (index <= 0 || index >= sections.size()) {
            return null;
        }
        return sections.get(index);
    }

    // ------------------------------------------------------------- symbol tab

    private static Elf.SymbolTable parseSymbolTable(ByteReader r, List<Elf.Section> sections,
                                                    Elf.Section symSec,
                                                    Map<Integer, String> verDefNames,
                                                    Map<Integer, Integer> verDefFlags,
                                                    Map<Integer, Elf.VersionNeed> verNeeds,
                                                    int[] versym, boolean is64) {
        if (symSec.link() <= 0 || symSec.link() >= sections.size()) {
            throw new ElfException("符号表 " + symSec.index() + " 的字符串表链接非法");
        }
        Elf.Section strSec = sections.get(symSec.link());
        StringTable strtab = StringTable.of(r, strSec);
        long ent = symSec.entrySize() == 0 ? (is64 ? 24 : 16) : symSec.entrySize();
        long need = ent;
        if (ent < need) {
            throw new ElfException("符号表表项过小: " + ent);
        }
        long count = ent == 0 ? 0 : symSec.size() / ent;
        if (symSec.offset() > 0 && symSec.offset() > (long) r.size() - symSec.size()) {
            throw new ElfException("符号表节区越界");
        }
        if (count > 5_000_000) {
            throw new ElfException("符号数量异常: " + count);
        }

        int[] extendedShndx = parseSymtabShndx(r, sections, symSec, is64, (int) count);

        List<Elf.Symbol> symbols = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long o = symSec.offset() + i * ent;
            int nameIdx;
            long value, size;
            int info, other, shndx;
            if (is64) {
                nameIdx = (int) r.u32(o);
                info = r.u8(o + 4);
                other = r.u8(o + 5);
                shndx = r.u16(o + 6);
                value = r.u64(o + 8);
                size = r.u64(o + 16);
            } else {
                nameIdx = (int) r.u32(o);
                value = r.u32(o + 4);
                size = r.u32(o + 8);
                info = r.u8(o + 12);
                other = r.u8(o + 13);
                shndx = r.u16(o + 14);
            }
            int binding = info >> 4;
            int stype = info & 0xf;
            int visibility = other & 0x3;
            if (shndx == Elf.SHN_XINDEX && extendedShndx != null && i < extendedShndx.length) {
                shndx = extendedShndx[i] & 0xffff;
            }
            boolean defined = shndx != Elf.SHN_UNDEF;
            boolean common = shndx == Elf.SHN_COMMON || stype == Elf.STT_COMMON;
            String name = strtab.at(nameIdx);
            Elf.VersionRef version = resolveVersion(i, symSec.type(), versym,
                    verDefNames, verDefFlags, verNeeds);
            symbols.add(new Elf.Symbol(i, name, binding, stype, visibility, value, size,
                    shndx, defined, common, version));
        }

        Map<Integer, List<Elf.Relocation>> rels = new LinkedHashMap<>();
        for (Elf.Section s : sections) {
            if ((s.type() == Elf.SHT_REL || s.type() == Elf.SHT_RELA) && s.info() == symSec.index()) {
                rels.put(s.index(), parseRelocations(r, s, sections, symbols, is64));
            }
        }
        return new Elf.SymbolTable(symSec, List.copyOf(symbols), rels,
                symSec.type() == Elf.SHT_DYNSYM);
    }

    private static Elf.VersionRef resolveVersion(int symIndex, int sectionType, int[] versym,
                                                 Map<Integer, String> verDefNames,
                                                 Map<Integer, Integer> verDefFlags,
                                                 Map<Integer, Elf.VersionNeed> verNeeds) {
        if (sectionType != Elf.SHT_DYNSYM) {
            return new Elf.VersionRef(Elf.VER_NDX_GLOBAL, null, false, Elf.VersionFlavor.GLOBAL);
        }
        if (versym == null || symIndex >= versym.length) {
            return new Elf.VersionRef(Elf.VER_NDX_GLOBAL, null, false, Elf.VersionFlavor.NONE);
        }
        int raw = versym[symIndex];
        int ndx = raw & 0x7fff;
        boolean hidden = (raw & Elf.VERSYM_HIDDEN) != 0;
        if (ndx == Elf.VER_NDX_LOCAL) {
            return new Elf.VersionRef(ndx, null, hidden, Elf.VersionFlavor.LOCAL);
        }
        if (ndx == Elf.VER_NDX_GLOBAL) {
            return new Elf.VersionRef(ndx, null, hidden, Elf.VersionFlavor.NONE);
        }
        String def = verDefNames.get(ndx);
        if (def != null) {
            int flags = verDefFlags.getOrDefault(ndx, 0);
            Elf.VersionFlavor flavor = (flags & 1) == 0
                    ? Elf.VersionFlavor.DEFAULT : Elf.VersionFlavor.NON_DEFAULT;
            return new Elf.VersionRef(ndx, def, hidden, flavor);
        }
        Elf.VersionNeed need = verNeeds.get(ndx);
        if (need != null) {
            return new Elf.VersionRef(ndx, need.name(), hidden,
                    need.ndx() == ndx ? Elf.VersionFlavor.NON_DEFAULT : Elf.VersionFlavor.NON_DEFAULT);
        }
        // index names a version that neither this object defines nor satisfies
        return new Elf.VersionRef(ndx, "version-index-" + ndx, hidden,
                Elf.VersionFlavor.UNRESOLVED);
    }

    private static int[] parseSymtabShndx(ByteReader r, List<Elf.Section> sections,
                                          Elf.Section symSec, boolean is64, int count) {
        for (Elf.Section s : sections) {
            if (s.type() == Elf.SHT_SYMTAB_SHNDX && s.link() == symSec.index()) {
                if (s.offset() > 0 && s.offset() > (long) r.size() - s.size()) {
                    throw new ElfException("SYMTAB_SHNDX 节区越界");
                }
                int[] arr = new int[count];
                long n = s.size() / 4;
                for (int i = 0; i < Math.min(n, count); i++) {
                    arr[i] = (int) r.u32(s.offset() + i * 4L);
                }
                return arr;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ relocations

    private static List<Elf.Relocation> parseRelocations(ByteReader r, Elf.Section relSec,
                                                         List<Elf.Section> sections,
                                                         List<Elf.Symbol> symbols,
                                                         boolean is64) {
        boolean rela = relSec.type() == Elf.SHT_RELA;
        int entSize = rela ? (is64 ? 24 : 12) : (is64 ? 16 : 8);
        long count = relSec.size() / entSize;
        if (relSec.offset() > (long) r.size() - relSec.size()) {
            throw new ElfException("重定位节区越界: " + relSec.index());
        }
        int targetSection = relSec.info();
        String targetName = targetSection >= 0 && targetSection < sections.size()
                ? sections.get(targetSection).name() : null;
        List<Elf.Relocation> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long o = relSec.offset() + (long) i * entSize;
            long offset = is64 ? r.u64(o) : r.u32(o);
            long info = is64 ? r.u64(o + 8) : r.u32(o + 4);
            long addend = 0;
            int symIndex;
            int relType;
            if (is64) {
                symIndex = (int) (info >>> 32);
                relType = (int) (info & 0xffffffffL);
                if (rela) {
                    addend = r.u64(o + 16);
                }
            } else {
                symIndex = (int) (info >>> 8);
                relType = (int) (info & 0xff);
                if (rela) {
                    addend = (int) r.u32(o + 8);
                }
            }
            if (symbols != null && symIndex >= symbols.size()) {
                throw new ElfException("重定位符号索引越界: " + symIndex
                        + " (符号数=" + symbols.size() + ")");
            }
            boolean copy = relType == Elf.R_X86_64_COPY || relType == Elf.R_AARCH64_COPY;
            out.add(new Elf.Relocation(offset, addend, relType, symIndex,
                    relSec.index(), rela, copy, targetName));
        }
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- groups

    private static List<Elf.SectionGroup> parseGroups(ByteReader r, List<Elf.Section> sections,
                                                      boolean is64) {
        List<Elf.SectionGroup> out = new ArrayList<>();
        for (Elf.Section s : sections) {
            if (s.type() != Elf.SHT_GROUP) {
                continue;
            }
            if (s.size() < 4 || s.offset() > (long) r.size() - s.size()) {
                throw new ElfException("GROUP 节区越界: " + s.index());
            }
            int flagWord = (int) r.u32(s.offset());
            long count = (s.size() - 4) / 4;
            List<Integer> members = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int memberIdx = (int) r.u32(s.offset() + 4 + (long) i * 4);
                members.add(memberIdx);
            }
            // info holds the symbol table index of the group signature.
            long sig = s.info();
            out.add(new Elf.SectionGroup(s.index(), sig, flagWord == 1, List.copyOf(members)));
        }
        return out;
    }

    // --------------------------------------------------------------- dynamic

    private static void parseDynamic(ByteReader r, List<Elf.Section> sections, boolean is64,
                                     List<String> needed, String[] soname) {
        for (Elf.Section s : sections) {
            if (s.type() != Elf.SHT_DYNAMIC) {
                continue;
            }
            if (s.offset() > (long) r.size() - s.size()) {
                throw new ElfException("DYNAMIC 节区越界");
            }
            if (s.link() <= 0 || s.link() >= sections.size()) {
                continue;
            }
            StringTable dynstr = StringTable.of(r, sections.get(s.link()));
            int ent = is64 ? 16 : 8;
            long count = s.size() / ent;
            for (int i = 0; i < count; i++) {
                long o = s.offset() + (long) i * ent;
                long tag = is64 ? r.u64(o) : r.u32(o);
                long val = is64 ? r.u64(o + 8) : r.u32(o + 4);
                if (tag == Elf.DT_NULL) {
                    break;
                }
                if (tag == Elf.DT_NEEDED) {
                    needed.add(dynstr.at((int) val));
                } else if (tag == Elf.DT_SONAME) {
                    soname[0] = dynstr.at((int) val);
                }
            }
            return;
        }
    }

    // --------------------------------------------------------------- versions

    private static List<Elf.VersionDefinition> parseVerDef(
            ByteReader r, List<Elf.Section> sections,
            Map<Integer, String> namesByNdx, Map<Integer, Integer> flagsByNdx) {
        List<Elf.VersionDefinition> out = new ArrayList<>();
        for (Elf.Section s : sections) {
            if (s.type() != Elf.SHT_GNU_VERDEF) {
                continue;
            }
            if (s.link() <= 0 || s.link() >= sections.size()
                    || s.offset() > (long) r.size() - s.size()) {
                throw new ElfException("VERDEF 节区非法");
            }
            StringTable str = StringTable.of(r, sections.get(s.link()));
            long off = s.offset();
            long end = s.offset() + s.size();
            while (off + 20 <= end) {
                int version = r.u16(off);          // version of structure
                int flags = r.u16(off + 2);
                int ndx = r.u16(off + 4);
                int cnt = r.u16(off + 6);
                long hash = r.u32(off + 8);
                long aux = r.u32(off + 12);
                long next = r.u32(off + 16);
                if (version == 0) {
                    break;
                }
                String primary = null;
                List<String> aliases = new ArrayList<>();
                long auxOff = off + aux;
                for (int a = 0; a < cnt && auxOff + 8 <= end; a++) {
                    long nameOff = r.u32(auxOff);
                    long nextAux = r.u32(auxOff + 4);
                    String nm = str.at((int) nameOff);
                    if (a == 0) {
                        primary = nm;
                    } else {
                        aliases.add(nm);
                    }
                    if (nextAux == 0) {
                        break;
                    }
                    auxOff += nextAux;
                }
                if (primary == null) {
                    primary = "";
                }
                namesByNdx.put(ndx, primary);
                flagsByNdx.put(ndx, flags);
                out.add(new Elf.VersionDefinition(ndx, primary, List.copyOf(aliases), flags));
                if (next == 0) {
                    break;
                }
                off += next;
            }
            return out;
        }
        return out;
    }

    private static List<Elf.VersionNeed> parseVerNeed(
            ByteReader r, List<Elf.Section> sections,
            Map<Integer, Elf.VersionNeed> byNdx) {
        List<Elf.VersionNeed> out = new ArrayList<>();
        for (Elf.Section s : sections) {
            if (s.type() != Elf.SHT_GNU_VERNEED) {
                continue;
            }
            if (s.link() <= 0 || s.link() >= sections.size()
                    || s.offset() > (long) r.size() - s.size()) {
                throw new ElfException("VERNEED 节区非法");
            }
            StringTable str = StringTable.of(r, sections.get(s.link()));
            long off = s.offset();
            long end = s.offset() + s.size();
            while (off + 16 <= end) {
                int version = r.u16(off);
                int cnt = r.u16(off + 2);
                long fileOff = r.u32(off + 4);
                long aux = r.u32(off + 8);
                long next = r.u32(off + 12);
                if (version == 0) {
                    break;
                }
                String soname = str.at((int) fileOff);
                long auxOff = off + aux;
                for (int a = 0; a < cnt && auxOff + 16 <= end; a++) {
                    int flags = r.u16(auxOff + 10);
                    int ndx = r.u16(auxOff + 12);
                    long nameOff = r.u32(auxOff);
                    long nextAux = r.u32(auxOff + 4);
                    String vname = str.at((int) nameOff);
                    boolean weak = (flags & Elf.VER_FLG_WEAK) != 0;
                    Elf.VersionNeed vn = new Elf.VersionNeed(soname, ndx, vname, weak);
                    out.add(vn);
                    byNdx.put(ndx, vn);
                    if (nextAux == 0) {
                        break;
                    }
                    auxOff += nextAux;
                }
                if (next == 0) {
                    break;
                }
                off += next;
            }
            return out;
        }
        return out;
    }

    private static int[] parseVersym(ByteReader r, List<Elf.Section> sections) {
        for (Elf.Section s : sections) {
            if (s.type() != Elf.SHT_GNU_VERSYM) {
                continue;
            }
            if (s.offset() > (long) r.size() - s.size()) {
                throw new ElfException("VERSYM 节区越界");
            }
            int n = (int) (s.size() / 2);
            int[] arr = new int[n];
            for (int i = 0; i < n; i++) {
                arr[i] = r.u16(s.offset() + (long) i * 2);
            }
            return arr;
        }
        return null;
    }
}
