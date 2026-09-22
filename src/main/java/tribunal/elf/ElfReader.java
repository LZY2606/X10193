package tribunal.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, defensive ELF parser used by the tribunal. It implements only what
 * symbol adjudication needs and never executes the parsed bytes.
 *
 * Every read is bounds checked; a malformed file throws {@link ElfFormatException},
 * which callers use to isolate exactly that unit.
 */
public final class ElfReader {

    private final byte[] b;
    private boolean le;

    public ElfReader(byte[] bytes) {
        this.b = bytes;
    }

    public ElfObject parse() {
        if (b.length < 16 || b[0] != 0x7f || b[1] != 'E' || b[2] != 'L' || b[3] != 'F') {
            throw new ElfFormatException("missing ELF magic");
        }
        int elfClass = b[4] & 0xff;
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfFormatException("unsupported EI_CLASS " + elfClass);
        }
        int dataEnc = b[5] & 0xff;
        if (dataEnc != 1 && dataEnc != 2) {
            throw new ElfFormatException("unsupported EI_DATA " + dataEnc);
        }
        this.le = dataEnc == 1;
        ElfObject o = new ElfObject();
        o.elfClass = elfClass;
        o.littleEndian = le;
        List<Hdr> headers = (elfClass == 2) ? parseHeaders64(o) : parseHeaders32(o);
        nameSections(o, headers);
        if (elfClass == 2) {
            parseSymbols64(o);
        } else {
            parseSymbols32(o);
        }
        parseDynamic(o);
        parseGroups(o);
        parseRelocations(o);
        parseVersionInfo(o);
        return o;
    }

    private record Hdr(int name, int type, long flags, long addr, long off, long size,
                       int link, int info, long align, long entsize) {}

    private List<Hdr> parseHeaders64(ElfObject o) {
        need(64);
        o.type = u16(16);
        o.machine = u16(18);
        o.entry = u64(24);
        o.flags = u32(48);
        long shoff = u64(40);
        int shentsize = u16(58);
        int shnum = u16(60);
        int shstrndx = u16(62);
        if (shentsize < 64) throw new ElfFormatException("bad 64-bit shentsize " + shentsize);
        if (shnum == 0) throw new ElfFormatException("no sections");
        List<Hdr> hs = new ArrayList<>(shnum);
        for (int i = 0; i < shnum; i++) {
            long off = shoff + (long) i * shentsize;
            need(off + 64);
            hs.add(new Hdr((int) u32(off), (int) u32(off + 4), u64(off + 8), u64(off + 16),
                    u64(off + 24), u64(off + 32), (int) u32(off + 40), (int) u32(off + 44),
                    u64(off + 48), u64(off + 56)));
        }
        if (shstrndx == 0xffff) {
            int real = hs.get(0).link();
            if (real >= hs.size()) throw new ElfFormatException("escaped shstrndx OOR");
            o.shStrndx = real;
        } else {
            if (shstrndx >= hs.size()) throw new ElfFormatException("shstrndx OOR");
            o.shStrndx = shstrndx;
        }
        return hs;
    }

    private List<Hdr> parseHeaders32(ElfObject o) {
        need(52);
        o.type = u16(16);
        o.machine = u16(18);
        o.entry = u32(24);
        o.flags = u32(36);
        long shoff = u32(32);
        int shentsize = u16(46);
        int shnum = u16(48);
        int shstrndx = u16(50);
        if (shentsize < 40) throw new ElfFormatException("bad 32-bit shentsize " + shentsize);
        if (shnum == 0) throw new ElfFormatException("no sections");
        List<Hdr> hs = new ArrayList<>(shnum);
        for (int i = 0; i < shnum; i++) {
            long off = shoff + (long) i * shentsize;
            need(off + 40);
            hs.add(new Hdr((int) u32(off), (int) u16(off + 4), u32(off + 8), u32(off + 12),
                    u32(off + 16), u32(off + 20), (int) u32(off + 24), (int) u32(off + 28),
                    u32(off + 32), u32(off + 36)));
        }
        if (shstrndx == 0xffff) {
            int real = hs.get(0).link();
            if (real >= hs.size()) throw new ElfFormatException("escaped shstrndx OOR");
            o.shStrndx = real;
        } else {
            if (shstrndx >= hs.size()) throw new ElfFormatException("shstrndx OOR");
            o.shStrndx = shstrndx;
        }
        return hs;
    }

    private void nameSections(ElfObject o, List<Hdr> hs) {
        byte[] names = sectionBytes(hs.get(o.shStrndx));
        for (int i = 0; i < hs.size(); i++) {
            Hdr h = hs.get(i);
            String name = (h.type() == 0) ? "" : cstr(names, h.name());
            o.sections.add(new Section(i, h.type(), h.flags(), h.addr(), h.off(), h.size(),
                    h.link(), h.info(), h.align(), h.entsize(), name));
        }
    }

    // ---------- symbol tables ----------

    private void parseSymbols64(ElfObject o) {
        for (Section s : o.sections) {
            if (s.type() != Elf.SHT_SYMTAB && s.type() != Elf.SHT_DYNSYM) continue;
            List<ElfSymbol> sink = s.type() == Elf.SHT_DYNSYM ? o.dynSymbols : o.symbols;
            if (s.type() == Elf.SHT_DYNSYM) o.hasDynSym = true;
            long entsize = s.entrySize() != 0 ? s.entrySize() : 24;
            if (entsize < 24) throw new ElfFormatException("64-bit sym entsize too small");
            byte[] strs = linkedStrings(o, s);
            int count = (int) (s.size() / entsize);
            int[] xindex = readXindex(o, s, count);
            for (int i = 0; i < count; i++) {
                long off = s.offset() + i * entsize;
                need(off + 24);
                int nameOff = (int) u32(off);
                int info = b[(int) off + 4] & 0xff;
                int other = b[(int) off + 5] & 0xff;
                int shndx = u16(off + 6);
                long value = u64(off + 8);
                long size = u64(off + 16);
                addSymbol(sink, i, cstr(strs, nameOff), info, other, shndx, value, size, xindex);
            }
        }
    }

    private void parseSymbols32(ElfObject o) {
        for (Section s : o.sections) {
            if (s.type() != Elf.SHT_SYMTAB && s.type() != Elf.SHT_DYNSYM) continue;
            List<ElfSymbol> sink = s.type() == Elf.SHT_DYNSYM ? o.dynSymbols : o.symbols;
            if (s.type() == Elf.SHT_DYNSYM) o.hasDynSym = true;
            long entsize = s.entrySize() != 0 ? s.entrySize() : 16;
            if (entsize < 16) throw new ElfFormatException("32-bit sym entsize too small");
            byte[] strs = linkedStrings(o, s);
            int count = (int) (s.size() / entsize);
            int[] xindex = readXindex(o, s, count);
            for (int i = 0; i < count; i++) {
                long off = s.offset() + i * entsize;
                need(off + 16);
                int nameOff = (int) u32(off);
                long value = u32(off + 4);
                long size = u32(off + 8);
                int info = b[(int) off + 12] & 0xff;
                int other = b[(int) off + 13] & 0xff;
                int shndx = u16(off + 14);
                addSymbol(sink, i, cstr(strs, nameOff), info, other, shndx, value, size, xindex);
            }
        }
    }

    private int[] readXindex(ElfObject o, Section symtab, int count) {
        int[] x = new int[count];
        for (int i = 0; i < count; i++) x[i] = 0;
        for (Section cand : o.sections) {
            if (cand.type() == Elf.SHT_SYMTAB_SHNDX && cand.link() == symtab.index()) {
                if (cand.size() / 4 < count) throw new ElfFormatException("short symtab_shndx");
                for (int i = 0; i < count; i++) {
                    x[i] = (int) u32(cand.offset() + i * 4L);
                }
            }
        }
        return x;
    }

    private void addSymbol(List<ElfSymbol> sink, int index, String name, int info, int other,
                           int shndx0, long value, long size, int[] xindex) {
        int bindCode = info >> 4;
        int typeCode = info & 0xf;
        int shndx = shndx0;
        if (shndx == Elf.SHN_XINDEX) {
            if (index >= xindex.length) throw new ElfFormatException("xindex missing for sym " + index);
            shndx = xindex[index];
        }
        boolean common = shndx == Elf.SHN_COMMON;
        boolean defined = shndx != Elf.SHN_UNDEF && !common;
        sink.add(new ElfSymbol(
                index, name,
                Elf.Bind.of(bindCode),
                Elf.kindOf(typeCode),
                Elf.visibilityOf(other),
                value, size, shndx, defined, common, null, false));
    }

    private byte[] linkedStrings(ElfObject o, Section s) {
        if (s.link() <= 0 || s.link() >= o.sections.size()) {
            throw new ElfFormatException("symbol section " + s.index() + " has bad string link");
        }
        return sectionBytes(o.sections.get(s.link()));
    }

    private void attachVersym(ElfObject o, Map<Integer, Integer> versym) {
        for (int i = 0; i < o.dynSymbols.size(); i++) {
            ElfSymbol s = o.dynSymbols.get(i);
            Integer vi = versym.get(i);
            if (vi != null) {
                boolean hidden = (vi & 0x8000) != 0;
                int clean = vi & 0x7fff;
                o.dynSymbols.set(i, new ElfSymbol(s.index(), s.name(), s.bind(), s.kind(),
                        s.visibility(), s.value(), s.size(), s.sectionIndex(), s.defined(),
                        s.common(), clean, hidden));
            }
        }
    }

    // ---------- dynamic section ----------

    private void parseDynamic(ElfObject o) {
        Section dyn = findSection(o, Elf.SHT_DYNAMIC);
        if (dyn == null) return;
        long ent = dyn.entrySize() != 0 ? dyn.entrySize() : (o.elfClass == 2 ? 16 : 8);
        boolean is64 = o.elfClass == 2;
        if (ent < (is64 ? 16 : 8)) throw new ElfFormatException("bad dynamic entsize");
        // DT_STRTAB normally points at a load address; fall back to the linked strtab section.
        byte[] strs = (dyn.link() > 0 && dyn.link() < o.sections.size())
                ? sectionBytes(o.sections.get(dyn.link())) : new byte[0];
        int count = (int) (dyn.size() / ent);
        long strtabVaddr = 0;
        for (int i = 0; i < count; i++) {
            long off = dyn.offset() + i * ent;
            long tag = is64 ? u64(off) : u32(off);
            long val = is64 ? u64(off + 8) : u32(off + 4);
            if (tag == Elf.DT_NULL) break;
            if (tag == Elf.DT_NEEDED) {
                String n = cstr(strs, (int) val);
                if (!n.isEmpty()) o.needed.add(n);
            } else if (tag == Elf.DT_SONAME) {
                o.soname = cstr(strs, (int) val);
            } else if (tag == Elf.DT_STRTAB) {
                strtabVaddr = val;
            }
        }
        if ((o.needed.isEmpty() || o.soname == null) && strtabVaddr != 0) {
            byte[] better = bytesAtVaddr(o, strtabVaddr);
            if (better != null) rereadDynStrings(o, dyn, better, is64, ent, count);
        }
    }

    private void rereadDynStrings(ElfObject o, Section dyn, byte[] strs, boolean is64, long ent, int count) {
        o.needed.clear();
        o.soname = null;
        for (int i = 0; i < count; i++) {
            long off = dyn.offset() + i * ent;
            long tag = is64 ? u64(off) : u32(off);
            long val = is64 ? u64(off + 8) : u32(off + 4);
            if (tag == Elf.DT_NULL) break;
            if (tag == Elf.DT_NEEDED) {
                String n = cstr(strs, (int) val);
                if (!n.isEmpty()) o.needed.add(n);
            } else if (tag == Elf.DT_SONAME) {
                o.soname = cstr(strs, (int) val);
            }
        }
    }

    private byte[] bytesAtVaddr(ElfObject o, long vaddr) {
        for (Section s : o.sections) {
            if (s.alloc() && vaddr >= s.address() && vaddr < s.address() + s.size()) {
                long delta = vaddr - s.address();
                if (s.offset() + delta < 0 || s.offset() + delta > b.length) return null;
                return slice((int) (s.offset() + delta), (int) (s.size() - delta));
            }
        }
        return null;
    }

    // ---------- COMDAT groups ----------

    private void parseGroups(ElfObject o) {
        for (Section g : o.sections) {
            if (g.type() != Elf.SHT_GROUP) continue;
            if (g.link() <= 0 || g.link() >= o.sections.size()) {
                throw new ElfFormatException("group " + g.index() + " bad symtab link");
            }
            Section symtab = o.sections.get(g.link());
            byte[] strs = linkedStrings(o, symtab);
            if (g.size() < 4) throw new ElfFormatException("group too small");
            int flag = (int) u32(g.offset());
            int n = (int) (g.size() / 4) - 1;
            int sigSym = g.info();
            String signature = null;
            ElfSymbol es = symbolAt(o, symtab, sigSym);
            if (es != null && !es.name().isEmpty()) {
                signature = es.name();
            } else if (es != null && es.sectionIndex() > 0 && es.sectionIndex() < o.sections.size()) {
                // signature symbol may be STT_SECTION: use section name
                signature = o.sections.get(es.sectionIndex()).name();
            }
            List<Integer> members = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                members.add((int) u32(g.offset() + 4 + i * 4L));
            }
            o.groups.add(new Group(g.index(), (flag & Elf.GRP_COMDAT) != 0, sigSym, signature, members));
        }
    }

    private ElfSymbol symbolAt(ElfObject o, Section symtab, int index) {
        List<ElfSymbol> table = symtab.type() == Elf.SHT_DYNSYM ? o.dynSymbols : o.symbols;
        if (index < 0 || index >= table.size()) return null;
        return table.get(index);
    }

    private Section findSection(ElfObject o, int type) {
        for (Section s : o.sections) if (s.type() == type) return s;
        return null;
    }

    // ---------- relocations ----------

    private void parseRelocations(ElfObject o) {
        int[] copyTypes;
        int[] jmpTypes;
        switch (o.machine) {
            case 62: // x86-64
                copyTypes = new int[]{1024};          // R_X86_64_COPY
                jmpTypes = new int[]{7};              // R_X86_64_JUMP_SLOT
                break;
            case 40: // ARM
                copyTypes = new int[]{20};            // R_ARM_COPY
                jmpTypes = new int[]{22};             // R_ARM_JUMP_SLOT
                break;
            case 183: // AArch64
                copyTypes = new int[]{1024};          // R_AARCH64_COPY
                jmpTypes = new int[]{1026};           // R_AARCH64_JUMP_SLOT
                break;
            case 3: // x86
                copyTypes = new int[]{5};             // R_386_COPY
                jmpTypes = new int[]{7};              // R_386_JMP_SLOT
                break;
            case 243: // RISC-V
                copyTypes = new int[]{0};             // no standard COPY; never matches
                jmpTypes = new int[]{5};
                break;
            default:
                copyTypes = new int[]{};
                jmpTypes = new int[]{};
        }
        for (Section s : o.sections) {
            if (s.type() != Elf.SHT_RELA && s.type() != Elf.SHT_REL) continue;
            if (s.link() <= 0 || s.link() >= o.sections.size()) {
                throw new ElfFormatException("relocation section " + s.index() + " bad symtab link");
            }
            Section symtab = o.sections.get(s.link());
            List<ElfSymbol> table = symtab.type() == Elf.SHT_DYNSYM ? o.dynSymbols : o.symbols;
            boolean is64 = o.elfClass == 2;
            long ent = s.entrySize() != 0 ? s.entrySize()
                    : (s.type() == Elf.SHT_RELA ? (is64 ? 24 : 12) : (is64 ? 16 : 8));
            int minEnt = s.type() == Elf.SHT_RELA ? (is64 ? 24 : 12) : (is64 ? 16 : 8);
            if (ent < minEnt) throw new ElfFormatException("bad relocation entsize");
            if (s.size() % ent != 0) throw new ElfFormatException("relocation size not a multiple of entsize");
            int count = (int) (s.size() / ent);
            for (int i = 0; i < count; i++) {
                long off = s.offset() + i * ent;
                long rOffset;
                long rInfo;
                long addend = 0;
                if (is64) {
                    need(off + (s.type() == Elf.SHT_RELA ? 24 : 16));
                    rOffset = u64(off);
                    rInfo = u64(off + 8);
                    if (s.type() == Elf.SHT_RELA) addend = u64(off + 16);
                } else {
                    need(off + (s.type() == Elf.SHT_RELA ? 12 : 8));
                    rOffset = u32(off);
                    rInfo = u32(off + 4);
                    if (s.type() == Elf.SHT_RELA) addend = u32(off + 8);
                }
                int symIndex;
                int type;
                if (is64) {
                    symIndex = (int) (rInfo >> 32);
                    type = (int) rInfo;
                } else {
                    symIndex = (int) (rInfo >> 8);
                    type = (int) (rInfo & 0xff);
                }
                if (symIndex < 0 || symIndex >= table.size()) {
                    throw new ElfFormatException("relocation sym index " + symIndex
                            + " out of range 0.." + table.size());
                }
                if (symIndex == 0) continue;
                boolean copy = contains(copyTypes, type);
                boolean jmp = contains(jmpTypes, type);
                int targetSection = s.info() >= 0 && s.info() < o.sections.size() ? s.info() : -1;
                o.relocations.add(new Relocation(s.index(), rOffset, type, addend, symIndex,
                        copy, jmp, targetSection));
            }
        }
    }

    private static boolean contains(int[] arr, int v) {
        for (int x : arr) if (x == v) return true;
        return false;
    }

    // ---------- GNU symbol versions ----------

    private void parseVersionInfo(ElfObject o) {
        Section verdef = sectionByName(o, ".gnu.version_d");
        Section verneed = sectionByName(o, ".gnu.version_need");
        Section versym = sectionByName(o, ".gnu.version");
        VersionInfo info = VersionInfo.EMPTY;
        if (verdef != null || verneed != null) {
            List<VersionInfo.VersionDefinition> defs = new ArrayList<>();
            List<VersionInfo.VersionRequirement> needs = new ArrayList<>();
            if (verdef != null) readVerdef(o, verdef, defs);
            if (verneed != null) readVerneed(o, verneed, needs);
            info = new VersionInfo(defs, needs);
        }
        o.versionInfo = info;
        Map<Integer, String> byIndex = new LinkedHashMap<>();
        for (VersionInfo.VersionDefinition d : info.definitions()) {
            byIndex.put(d.index(), d.name());
            for (String parent : d.parents()) {
                // aliases share the same index; map only the first (default/base) name
            }
        }
        for (VersionInfo.VersionRequirement r : info.requirements()) {
            for (VersionInfo.VersionRequirement.Entry e : r.entries()) {
                byIndex.put(e.index(), e.name() + "@" + r.library());
            }
        }
        o.versionByIndex = byIndex;
        if (versym != null) {
            if (versym.size() / 2 < o.dynSymbols.size()) {
                throw new ElfFormatException(".gnu.version shorter than dynsym");
            }
            Map<Integer, Integer> map = new LinkedHashMap<>();
            for (int i = 0; i < o.dynSymbols.size(); i++) {
                map.put(i, u16(versym.offset() + i * 2L));
            }
            attachVersym(o, map);
        }
    }

    private void readVerdef(ElfObject o, Section s, List<VersionInfo.VersionDefinition> out) {
        byte[] strs = linkedStrings(o, s);
        int count = (int) s.info();
        long p = s.offset();
        for (int i = 0; i < count; i++) {
            need(p + 20);
            int version = u16(p);
            int flags = u16(p + 2);
            int ndx = u16(p + 4);
            int cnt = u16(p + 6);
            long hash = u32(p + 8);
            int aux = u32(p + 12);
            int next = u32(p + 16);
            if (version != 1) throw new ElfFormatException("unsupported verdef version " + version);
            String base = null;
            List<String> parents = new ArrayList<>();
            long ap = p + (aux & 0xffffffffL);
            for (int k = 0; k < cnt; k++) {
                need(ap + 8);
                int nameOff = u32(ap);
                int aNext = u32(ap + 4);
                String nm = cstr(strs, nameOff);
                if (k == 0) base = nm; else parents.add(nm);
                if (aNext == 0) break;
                ap += aNext & 0xffffffffL;
            }
            boolean baseFlag = (flags & Elf.VER_FLG_BASE) != 0;
            out.add(new VersionInfo.VersionDefinition(ndx, base == null ? "" : base,
                    baseFlag, (flags & Elf.VER_FLG_WEAK) != 0, parents));
            if (next == 0) break;
            p += next & 0xffffffffL;
        }
    }

    private void readVerneed(ElfObject o, Section s, List<VersionInfo.VersionRequirement> out) {
        byte[] strs = linkedStrings(o, s);
        int count = (int) s.info();
        long p = s.offset();
        for (int i = 0; i < count; i++) {
            need(p + 16);
            int version = u16(p);
            int cnt = u16(p + 2);
            int fileOff = u32(p + 4);
            int aux = u32(p + 8);
            int next = u32(p + 12);
            if (version != 1) throw new ElfFormatException("unsupported verneed version " + version);
            String lib = cstr(strs, fileOff);
            List<VersionInfo.VersionRequirement.Entry> entries = new ArrayList<>();
            long ap = p + (aux & 0xffffffffL);
            for (int k = 0; k < cnt; k++) {
                need(ap + 16);
                int hash = (int) u32(ap);
                int flags = u16(ap + 4);
                int other = u16(ap + 6);
                int nameOff = u32(ap + 8);
                int aNext = u32(ap + 12);
                String nm = cstr(strs, nameOff);
                entries.add(new VersionInfo.VersionRequirement.Entry(other, hash, nm,
                        (flags & Elf.VER_FLG_WEAK) != 0));
                if (aNext == 0) break;
                ap += aNext & 0xffffffffL;
            }
            out.add(new VersionInfo.VersionRequirement(lib, entries));
            if (next == 0) break;
            p += next & 0xffffffffL;
        }
    }

    private Section sectionByName(ElfObject o, String name) {
        for (Section s : o.sections) if (name.equals(s.name())) return s;
        return null;
    }

    // ---------- low level bounded readers ----------

    private byte[] sectionBytes(Hdr h) {
        if (h.size() < 0 || h.off() < 0) throw new ElfFormatException("negative section range");
        long end = h.off() + h.size();
        if (h.type() == 8 /* SHT_NOBITS */ || end > b.length) {
            if (h.type() == 8) return new byte[(int) Math.min(h.size(), 0)];
            throw new ElfFormatException("section bytes out of file");
        }
        return slice((int) h.off(), (int) h.size());
    }

    private byte[] sectionBytes(Section s) {
        if (s == null) throw new ElfFormatException("missing section");
        if (s.type() == 8) return new byte[0];
        long end = s.offset() + s.size();
        if (s.offset() < 0 || end > b.length || s.size() < 0) {
            throw new ElfFormatException("section " + s.index() + " (" + s.name() + ") out of file");
        }
        return slice((int) s.offset(), (int) s.size());
    }

    private byte[] slice(int off, int len) {
        if (off < 0 || len < 0 || (long) off + len > b.length) {
            throw new ElfFormatException("slice OOR: off=" + off + " len=" + len + " file=" + b.length);
        }
        byte[] out = new byte[len];
        System.arraycopy(b, off, out, 0, len);
        return out;
    }

    private String cstr(byte[] data, int off) {
        if (off < 0 || off >= data.length) {
            throw new ElfFormatException("string offset OOR: " + off + " len=" + data.length);
        }
        int end = off;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, off, end - off, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void need(long endExclusive) {
        if (endExclusive < 0 || endExclusive > b.length) {
            throw new ElfFormatException("read past end of file: need " + endExclusive
                    + " have " + b.length);
        }
    }

    private int u16(long off) {
        need(off + 2);
        int p = (int) off;
        if (le) return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8);
        return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
    }

    private long u32(long off) {
        need(off + 4);
        int p = (int) off;
        long v;
        if (le) {
            v = (b[p] & 0xffL) | ((b[p + 1] & 0xffL) << 8)
                    | ((b[p + 2] & 0xffL) << 16) | ((b[p + 3] & 0xffL) << 24);
        } else {
            v = ((b[p] & 0xffL) << 24) | ((b[p + 1] & 0xffL) << 16)
                    | ((b[p + 2] & 0xffL) << 8) | (b[p + 3] & 0xffL);
        }
        return v & 0xffffffffL;
    }

    private long u64(long off) {
        need(off + 8);
        int p = (int) off;
        if (le) {
            long v = 0;
            for (int i = 7; i >= 0; i--) v = (v << 8) | (b[p + i] & 0xffL);
            return v;
        }
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (b[p + i] & 0xffL);
        return v;
    }
}
