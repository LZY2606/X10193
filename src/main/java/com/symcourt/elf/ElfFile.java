package com.symcourt.elf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed ELF object. Pure parsing: no linking decisions live here.
 * Supports ELFCLASS32/64 and little/big endian; every offset/length is
 * validated against the file size, and a malformed object only aborts
 * its own parse.
 */
public final class ElfFile {

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STV_DEFAULT = 0;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int GRP_COMDAT = 1;
    public static final int VERSYM_HIDDEN = 0x8000;

    public static final class Section {
        public final int index;
        public final String name;
        public final int type;
        public final long flags;
        public final long addr;
        public final long offset;
        public final long size;
        public final int link;
        public final int info;
        public final long entsize;
        int nameOff;

        Section(int index, String name, int type, long flags, long addr, long offset,
                long size, int link, int info, long entsize) {
            this.index = index;
            this.name = name;
            this.type = type;
            this.flags = flags;
            this.addr = addr;
            this.offset = offset;
            this.size = size;
            this.link = link;
            this.info = info;
            this.entsize = entsize;
        }
    }

    public static final class Symbol {
        public final int index;
        public final String name;
        public final int info;
        public final int other;
        public final int shndx;
        public final long value;
        public final long size;

        Symbol(int index, String name, int info, int other, int shndx, long value, long size) {
            this.index = index;
            this.name = name;
            this.info = info;
            this.other = other;
            this.shndx = shndx;
            this.value = value;
            this.size = size;
        }

        public int bind() { return info >>> 4; }
        public int type() { return info & 0xf; }
        public int visibility() { return other & 3; }
        public boolean isUndefined() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON; }
        public boolean isWeak() { return bind() == STB_WEAK; }
        public boolean isLocal() { return bind() == STB_LOCAL; }
    }

    public static final class Reloc {
        public final long offset;
        public final int symIndex;
        public final int type;
        public final long addend;
        public final String sectionName;

        Reloc(long offset, int symIndex, int type, long addend, String sectionName) {
            this.offset = offset;
            this.symIndex = symIndex;
            this.type = type;
            this.addend = addend;
            this.sectionName = sectionName;
        }
    }

    public static final class VersionDef {
        public final int index;
        public final String name;

        VersionDef(int index, String name) {
            this.index = index;
            this.name = name;
        }
    }

    public static final class VersionNeedEntry {
        public final String file;
        public final String name;
        public final int index;

        VersionNeedEntry(String file, String name, int index) {
            this.file = file;
            this.name = name;
            this.index = index;
        }
    }

    public static final class VersionRef {
        public final String name;
        public final boolean hidden;

        VersionRef(String name, boolean hidden) {
            this.name = name;
            this.hidden = hidden;
        }
    }

    public static final class Group {
        public final String signature;
        public final boolean comdat;
        public final List<Integer> sectionIndices;

        Group(String signature, boolean comdat, List<Integer> sectionIndices) {
            this.signature = signature;
            this.comdat = comdat;
            this.sectionIndices = sectionIndices;
        }
    }

    private final byte[] data;
    private final boolean is64;
    private final int elfType;
    private final List<Section> sections = new ArrayList<>();
    private final List<Symbol> symtab = new ArrayList<>();
    private final List<Symbol> dynsym = new ArrayList<>();
    private final List<Reloc> relocs = new ArrayList<>();
    private final List<VersionDef> versionDefs = new ArrayList<>();
    private final List<VersionNeedEntry> versionNeeds = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private int[] versym = new int[0];

    private ElfFile(byte[] data, boolean is64, int elfType) {
        this.data = data;
        this.is64 = is64;
        this.elfType = elfType;
    }

    public static ElfFile parse(byte[] data) {
        if (data == null || data.length < 16) {
            throw new ElfParseException("file shorter than ELF ident");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfParseException("bad ELF magic");
        }
        int cls = data[4] & 0xff;
        if (cls != 1 && cls != 2) {
            throw new ElfParseException("unsupported EI_CLASS " + cls);
        }
        int enc = data[5] & 0xff;
        if (enc != 1 && enc != 2) {
            throw new ElfParseException("unsupported EI_DATA " + enc);
        }
        boolean is64 = cls == 2;
        ByteOrder order = enc == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer buf = ByteBuffer.wrap(data).order(order);
        int elfType = buf.getShort(16) & 0xffff;
        if (elfType != ET_REL && elfType != ET_EXEC && elfType != ET_DYN && elfType != 0) {
            throw new ElfParseException("unsupported e_type " + elfType);
        }
        ElfFile f = new ElfFile(data, is64, elfType);
        f.parseSections(buf);
        return f;
    }

    private void require(long offset, long length) {
        if (offset < 0 || length < 0 || offset > data.length || offset + length > data.length) {
            throw new ElfParseException(
                    "offset/length out of bounds: off=" + offset + " len=" + length + " file=" + data.length);
        }
    }

    private void parseSections(ByteBuffer buf) {
        long shoff = is64 ? buf.getLong(40) : unsigned(buf.getInt(32));
        int shentsize = is64 ? buf.getShort(58) & 0xffff : buf.getShort(46) & 0xffff;
        int shnum = is64 ? buf.getShort(60) & 0xffff : buf.getShort(48) & 0xffff;
        int shstrndx = is64 ? buf.getShort(62) & 0xffff : buf.getShort(50) & 0xffff;
        if (shoff == 0) {
            return;
        }
        if (shentsize <= 0) {
            throw new ElfParseException("invalid shentsize " + shentsize);
        }
        require(shoff, (long) shentsize * shnum);
        List<Section> raw = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            raw.add(sectionAt(buf, shoff + (long) i * shentsize));
        }
        String[] names = new String[shnum];
        if (shstrndx > 0 && shstrndx < shnum) {
            Section str = raw.get(shstrndx);
            for (int i = 0; i < shnum; i++) {
                names[i] = readString(str.offset, raw.get(i).nameOff);
            }
        }
        for (int i = 0; i < shnum; i++) {
            Section s = raw.get(i);
            sections.add(new Section(i, names[i], s.type, s.flags, s.addr, s.offset,
                    s.size, s.link, s.info, s.entsize));
        }
        parseContents(buf);
    }

    private Section sectionAt(ByteBuffer buf, long off) {
        int o = (int) off;
        int name;
        int type;
        long flags, addr, offset, size, entsize;
        int link, info;
        if (is64) {
            name = buf.getInt(o);
            type = buf.getInt(o + 4);
            flags = buf.getLong(o + 8);
            addr = buf.getLong(o + 16);
            offset = buf.getLong(o + 24);
            size = buf.getLong(o + 32);
            link = buf.getInt(o + 40);
            info = buf.getInt(o + 44);
            entsize = buf.getLong(o + 56);
        } else {
            name = buf.getInt(o);
            type = buf.getInt(o + 4);
            flags = unsigned(buf.getInt(o + 8));
            addr = unsigned(buf.getInt(o + 12));
            offset = unsigned(buf.getInt(o + 16));
            size = unsigned(buf.getInt(o + 20));
            link = buf.getInt(o + 24);
            info = buf.getInt(o + 28);
            entsize = unsigned(buf.getInt(o + 36));
        }
        Section s = new Section(0, "", type, flags, addr, offset, size, link, info, entsize);
        s.nameOff = name;
        return s;
    }

    private void parseContents(ByteBuffer buf) {
        for (Section s : sections) {
            if (s.type != SHT_NOBITS && s.type != 0 && s.size > 0) {
                require(s.offset, s.size);
            }
        }
        for (Section s : sections) {
            switch (s.type) {
                case SHT_SYMTAB -> readSymbols(buf, s, symtab);
                case SHT_DYNSYM -> readSymbols(buf, s, dynsym);
                case SHT_GNU_VERDEF -> readVerdef(buf, s);
                case SHT_GNU_VERNEED -> readVerneed(buf, s);
                case SHT_GNU_VERSYM -> readVersym(buf, s);
                case SHT_GROUP -> readGroup(buf, s);
                case SHT_RELA, SHT_REL -> readRelocs(buf, s);
                default -> { }
            }
        }
    }

    private void readSymbols(ByteBuffer buf, Section s, List<Symbol> into) {
        long minEnt = is64 ? 24 : 16;
        long ent = s.entsize == 0 ? minEnt : s.entsize;
        if (ent < minEnt) {
            throw new ElfParseException("symbol entsize too small in section " + s.name);
        }
        if (s.link <= 0 || s.link >= sections.size()) {
            throw new ElfParseException("symtab sh_link invalid in section " + s.name);
        }
        Section str = sections.get(s.link);
        require(str.offset, str.size);
        long count = s.size / ent;
        if (count > 1_000_000) {
            throw new ElfParseException("implausible symbol count " + count);
        }
        for (int i = 0; i < count; i++) {
            long off = s.offset + i * ent;
            int nameOff;
            int info;
            int other;
            int shndx;
            long value;
            long size;
            if (is64) {
                nameOff = buf.getInt((int) off);
                info = buf.get((int) off + 4) & 0xff;
                other = buf.get((int) off + 5) & 0xff;
                shndx = buf.getShort((int) off + 6) & 0xffff;
                value = buf.getLong((int) off + 8);
                size = buf.getLong((int) off + 16);
            } else {
                nameOff = buf.getInt((int) off);
                value = unsigned(buf.getInt((int) off + 4));
                size = unsigned(buf.getInt((int) off + 8));
                info = buf.get((int) off + 12) & 0xff;
                other = buf.get((int) off + 13) & 0xff;
                shndx = buf.getShort((int) off + 14) & 0xffff;
            }
            String name = readString(str.offset, nameOff);
            into.add(new Symbol(i, name, info, other, shndx, value, size));
        }
    }

    private void readVerdef(ByteBuffer buf, Section s) {
        if (s.link <= 0 || s.link >= sections.size()) {
            throw new ElfParseException("verdef sh_link invalid");
        }
        Section str = sections.get(s.link);
        require(str.offset, str.size);
        long off = s.offset;
        long end = s.offset + s.size;
        int guard = 0;
        while (off > 0 && off + 20 <= end && guard++ < 10000) {
            int ndx = buf.getShort((int) off + 6) & 0xffff;
            int cnt = buf.getShort((int) off + 8) & 0xffff;
            int aux = buf.getInt((int) off + 12);
            int next = buf.getInt((int) off + 16);
            long auxOff = off + aux;
            if (cnt > 0 && auxOff + 8 <= end) {
                int nameOff = buf.getInt((int) auxOff);
                versionDefs.add(new VersionDef(ndx, readString(str.offset, nameOff)));
            }
            if (next == 0) {
                break;
            }
            off += next;
        }
    }

    private void readVerneed(ByteBuffer buf, Section s) {
        if (s.link <= 0 || s.link >= sections.size()) {
            throw new ElfParseException("verneed sh_link invalid");
        }
        Section str = sections.get(s.link);
        require(str.offset, str.size);
        long off = s.offset;
        long end = s.offset + s.size;
        int guard = 0;
        while (off > 0 && off + 16 <= end && guard++ < 10000) {
            int cnt = buf.getShort((int) off + 2) & 0xffff;
            int fileOff = buf.getInt((int) off + 4);
            int aux = buf.getInt((int) off + 8);
            int next = buf.getInt((int) off + 12);
            String file = readString(str.offset, fileOff);
            long aoff = off + aux;
            int aguard = 0;
            for (int i = 0; i < cnt && aoff + 16 <= end && aguard++ < 10000; i++) {
                int other = buf.getShort((int) aoff + 4) & 0xffff;
                int nameOff = buf.getInt((int) aoff + 8);
                int anext = buf.getInt((int) aoff + 12);
                versionNeeds.add(new VersionNeedEntry(file, readString(str.offset, nameOff), other));
                if (anext == 0) {
                    break;
                }
                aoff += anext;
            }
            if (next == 0) {
                break;
            }
            off += next;
        }
    }

    private void readVersym(ByteBuffer buf, Section s) {
        require(s.offset, s.size);
        int count = (int) (s.size / 2);
        versym = new int[count];
        for (int i = 0; i < count; i++) {
            versym[i] = buf.getShort((int) s.offset + i * 2) & 0xffff;
        }
    }

    private void readGroup(ByteBuffer buf, Section s) {
        require(s.offset, s.size);
        if (s.size < 4) {
            throw new ElfParseException("group section too small");
        }
        long flags = unsigned(buf.getInt((int) s.offset));
        List<Integer> members = new ArrayList<>();
        for (long off = s.offset + 4; off + 4 <= s.offset + s.size; off += 4) {
            long idx = unsigned(buf.getInt((int) off));
            if (idx >= sections.size()) {
                throw new ElfParseException("group member section index out of range: " + idx);
            }
            members.add((int) idx);
        }
        String signature = "";
        if (s.link > 0 && s.link < sections.size() && s.info >= 0) {
            List<Symbol> tab = sections.get(s.link).type == SHT_DYNSYM ? dynsym : symtab;
            if (s.info < tab.size()) {
                signature = tab.get(s.info).name;
            }
        }
        groups.add(new Group(signature, (flags & GRP_COMDAT) != 0, members));
    }

    private void readRelocs(ByteBuffer buf, Section s) {
        boolean rela = s.type == SHT_RELA;
        long minEnt = is64 ? (rela ? 24 : 16) : (rela ? 12 : 8);
        long ent = s.entsize == 0 ? minEnt : s.entsize;
        if (ent < minEnt) {
            throw new ElfParseException("relocation entsize too small in section " + s.name);
        }
        String secName = s.info >= 0 && s.info < sections.size() ? sections.get(s.info).name : s.name;
        long count = s.size / ent;
        if (count > 1_000_000) {
            throw new ElfParseException("implausible relocation count " + count);
        }
        for (int i = 0; i < count; i++) {
            long off = s.offset + i * ent;
            long offset;
            long info;
            long addend = 0;
            if (is64) {
                offset = buf.getLong((int) off);
                info = buf.getLong((int) off + 8);
                if (rela) {
                    addend = buf.getLong((int) off + 16);
                }
            } else {
                offset = unsigned(buf.getInt((int) off));
                info = unsigned(buf.getInt((int) off + 4));
                if (rela) {
                    addend = buf.getInt((int) off + 8);
                }
            }
            int sym = is64 ? (int) (info >>> 32) : (int) (info >>> 8);
            int type = is64 ? (int) (info & 0xffffffffL) : (int) (info & 0xff);
            relocs.add(new Reloc(offset, sym, type, addend, secName));
        }
    }

    private String readString(long strOff, long nameOff) {
        if (nameOff < 0 || strOff + nameOff >= data.length) {
            return "";
        }
        int start = (int) (strOff + nameOff);
        int end = start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static long unsigned(int v) {
        return v & 0xffffffffL;
    }

    /** Version attached to a dynsym entry, or null when unversioned. */
    public VersionRef dynsymVersion(int dynsymIndex) {
        if (dynsymIndex < 0 || dynsymIndex >= versym.length) {
            return null;
        }
        int raw = versym[dynsymIndex];
        boolean hidden = (raw & VERSYM_HIDDEN) != 0;
        int idx = raw & 0x7fff;
        if (idx <= 1) {
            return null;
        }
        for (VersionDef d : versionDefs) {
            if (d.index == idx) {
                return new VersionRef(d.name, hidden);
            }
        }
        for (VersionNeedEntry n : versionNeeds) {
            if (n.index == idx) {
                return new VersionRef(n.name, hidden);
            }
        }
        return new VersionRef("<bad-index-" + idx + ">", hidden);
    }

    public byte[] data() { return data; }
    public boolean is64() { return is64; }
    public int elfType() { return elfType; }
    public List<Section> sections() { return sections; }
    public List<Symbol> symtab() { return symtab; }
    public List<Symbol> dynsym() { return dynsym; }
    public List<Reloc> relocs() { return relocs; }
    public List<VersionDef> versionDefs() { return versionDefs; }
    public List<VersionNeedEntry> versionNeeds() { return versionNeeds; }
    public List<Group> groups() { return groups; }
}
