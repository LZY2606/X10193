package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java ELF parser. Supports ELFCLASS32/64, both endians, ET_REL/ET_DYN/
 * ET_EXEC. No system linker is invoked. All accesses go through ByteReader so a
 * truncated or corrupt object raises ElfException and can be isolated.
 */
public final class ElfParser {

    // section types
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_RELA = 4;
    private static final int SHT_HASH = 5;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GROUP = 17;
    private static final int SHT_GNU_HASH = 0x6ffffff6;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;

    // dynamic tags
    private static final long DT_NULL = 0;
    private static final long DT_NEEDED = 1;
    private static final long DT_SONAME = 14;
    private static final long DT_VERNEEDNUM = 0x6fffffff;
    private static final long DT_VERDEFNUM = 0x6ffffffc;

    private static final int SHF_ALLOC = 2;

    private final byte[] data;
    private ByteReader r;
    private boolean elf64;
    private int machine;

    public ElfParser(byte[] data) {
        this.data = data;
    }

    public ElfFile parse() throws ElfException {
        if (data == null || data.length < 16) {
            throw new ElfException("too short to be ELF");
        }
        if (!(data[0] == 0x7f && data[1] == 'E' && data[2] == 'L' && data[3] == 'F')) {
            throw new ElfException("bad ELF magic");
        }
        int identClass = data[4] & 0xff;
        if (identClass != 1 && identClass != 2) {
            throw new ElfException("unsupported EI_CLASS " + identClass);
        }
        elf64 = identClass == 2;
        int identData = data[5] & 0xff;
        if (identData != 1 && identData != 2) {
            throw new ElfException("unsupported EI_DATA " + identData);
        }
        boolean le = identData == 1;
        r = new ByteReader(data, le);
        ElfFile f = new ElfFile();
        f.elf64 = elf64;
        f.littleEndian = le;

        long ehSize = elf64 ? 64 : 52;
        r.require(0, ehSize);
        int type = r.u16(16);
        f.kind = mapKind(type);
        machine = r.u16(18);
        f.machine = machine;
        f.machineName = mapMachine(machine);

        long shOff = elf64 ? r.u64(40) : r.u32(32);
        int shEntSize = elf64 ? r.u16(58) : r.u16(46);
        int shNum = elf64 ? r.u16(60) : r.u16(48);
        int shStrNdx = elf64 ? r.u16(62) : r.u16(50);

        parseSections(f, shOff, shEntSize, shNum, shStrNdx);
        parseSymbols(f);
        parseDynamic(f);
        parseVersions(f);
        parseGroups(f);
        parseRelocations(f);
        assignVersionNames(f);
        return f;
    }

    private Kind mapKind(int t) throws ElfException {
        return switch (t) {
            case 1 -> Kind.RELOCATABLE;
            case 2 -> Kind.EXECUTABLE;
            case 3 -> Kind.SHARED;
            case 4 -> Kind.CORE;
            default -> throw new ElfException("unsupported e_type " + t);
        };
    }

    private String mapMachine(int m) {
        return switch (m) {
            case 3 -> "x86";
            case 62 -> "x86_64";
            case 183 -> "aarch64";
            case 40 -> "arm";
            case 243 -> "riscv";
            case 0 -> "none";
            default -> "machine(" + m + ")";
        };
    }

    // ---------------------------------------------------------------- sections

    private void parseSections(ElfFile f, long shOff, int shEntSize, int shNum, int shStrNdx)
            throws ElfException {
        int ent = shEntSize > 0 ? shEntSize : (elf64 ? 64 : 40);
        if (shNum == 0) {
            return; // section headers may be stripped
        }
        r.require(shOff, (long) ent * shNum);
        for (int i = 0; i < shNum; i++) {
            long base = shOff + (long) i * ent;
            long nameOff = r.u32(base);
            int type = (int) r.u32(base + 4);
            long flags, addr, off, size;
            int link, info;
            long align, entsize;
            if (elf64) {
                flags = r.u64(base + 8);
                addr = r.u64(base + 16);
                off = r.u64(base + 24);
                size = r.u64(base + 32);
                link = (int) r.u32(base + 40);
                info = (int) r.u32(base + 44);
                align = r.u64(base + 48);
                entsize = r.u64(base + 56);
            } else {
                flags = r.u32(base + 8);
                addr = r.u32(base + 12);
                size = r.u32(base + 16);
                off = r.u32(base + 20);
                link = (int) r.u32(base + 24);
                info = (int) r.u32(base + 28);
                align = r.u32(base + 32);
                entsize = r.u32(base + 36);
            }
            f.sections.add(new Section(nameOff, type, flags, addr, off, size, link, info,
                    align, entsize, i));
        }
        // resolve names
        if (shStrNdx < f.sections.size()) {
            Section str = f.sections.get(shStrNdx);
            for (Section s : f.sections) {
                long off = str.offset;
                r.require(off, str.size);
                s.name = r.cstr(off, s.nameOffset);
            }
        }
    }

    // ----------------------------------------------------------------- symbols

    private void parseSymbols(ElfFile f) throws ElfException {
        for (Section s : f.sections) {
            if (s.type != SHT_SYMTAB && s.type != SHT_DYNSYM) {
                continue;
            }
            boolean dyn = s.type == SHT_DYNSYM;
            List<Symbol> target = dyn ? f.dynamicSymbols : f.staticSymbols;
            int strSection = s.link;
            if (strSection < 0 || strSection >= f.sections.size()) {
                throw new ElfException("symbol table " + s.name + " has invalid sh_link");
            }
            Section str = f.sections.get(strSection);
            r.require(s.offset, s.size);
            long ent = s.entrySize > 0 ? s.entrySize : (elf64 ? 24 : 16);
            if (s.size % ent != 0) {
                throw new ElfException("symbol table size not a multiple of entry size");
            }
            long count = s.size / ent;
            for (int i = 0; i < count; i++) {
                long base = s.offset + i * ent;
                long nameIdx, value, size2;
                long infoByte, other, shndx;
                if (elf64) {
                    nameIdx = r.u32(base);
                    infoByte = r.u8(base + 4) & 0xffL;
                    other = r.u8(base + 5) & 0xffL;
                    shndx = r.u16(base + 6) & 0xffffL;
                    value = r.u64(base + 8);
                    size2 = r.u64(base + 16);
                } else {
                    nameIdx = r.u32(base);
                    value = r.u32(base + 4);
                    size2 = r.u32(base + 8);
                    infoByte = r.u8(base + 12) & 0xffL;
                    other = r.u8(base + 13) & 0xffL;
                    shndx = r.u16(base + 14) & 0xffffL;
                }
                String nm = r.cstr(str.offset, nameIdx);
                int bindCode = (int) (infoByte >> 4);
                int typeCode = (int) (infoByte & 0xf);
                int visCode = (int) (other & 0x3);
                Symbol sym = new Symbol(nm, nameIdx, mapBinding(bindCode), mapType(typeCode),
                        mapVisibility(visCode), shndx, value, size2, dyn, i, 0);
                target.add(sym);
            }
        }
    }

    private SymbolBinding mapBinding(int b) throws ElfException {
        return switch (b) {
            case 0 -> SymbolBinding.LOCAL;
            case 1 -> SymbolBinding.GLOBAL;
            case 2 -> SymbolBinding.WEAK;
            default -> throw new ElfException("unsupported symbol binding " + b);
        };
    }

    private SymbolType mapType(int t) {
        return switch (t) {
            case 0 -> SymbolType.NOTYPE;
            case 1 -> SymbolType.OBJECT;
            case 2 -> SymbolType.FUNC;
            case 3 -> SymbolType.SECTION;
            case 4 -> SymbolType.FILE;
            case 5 -> SymbolType.COMMON;
            case 6 -> SymbolType.TLS;
            default -> SymbolType.NOTYPE;
        };
    }

    private Visibility mapVisibility(int v) throws ElfException {
        return switch (v) {
            case 0 -> Visibility.DEFAULT;
            case 1 -> Visibility.INTERNAL;
            case 2 -> Visibility.HIDDEN;
            case 3 -> Visibility.PROTECTED;
            default -> throw new ElfException("unsupported visibility " + v);
        };
    }

    // ----------------------------------------------------------------- dynamic

    private void parseDynamic(ElfFile f) throws ElfException {
        for (Section s : f.sections) {
            if (s.type != SHT_DYNAMIC) {
                continue;
            }
            int strIdx = s.link;
            if (strIdx < 0 || strIdx >= f.sections.size()) {
                throw new ElfException(".dynamic has invalid sh_link");
            }
            Section str = f.sections.get(strIdx);
            r.require(s.offset, s.size);
            long ent = elf64 ? 16 : 8;
            if (s.size % ent != 0) {
                throw new ElfException(".dynamic size invalid");
            }
            long n = s.size / ent;
            for (long i = 0; i < n; i++) {
                long base = s.offset + i * ent;
                long tag = elf64 ? r.u64(base) : r.u32(base);
                long val = elf64 ? r.u64(base + 8) : r.u32(base + 4);
                if (tag == DT_NULL) {
                    break;
                } else if (tag == DT_NEEDED) {
                    f.needed.add(r.cstr(str.offset, val));
                } else if (tag == DT_SONAME) {
                    f.soname = r.cstr(str.offset, val);
                }
            }
        }
    }

    // ---------------------------------------------------------------- versions

    private void parseVersions(ElfFile f) throws ElfException {
        for (Section s : f.sections) {
            if (s.type == SHT_GNU_VERDEF) {
                parseVerdef(f, s);
            } else if (s.type == SHT_GNU_VERNEED) {
                parseVerneed(f, s);
            } else if (s.type == SHT_GNU_VERSYM) {
                parseVersym(f, s);
            }
        }
    }

    private void parseVerdef(ElfFile f, Section s) throws ElfException {
        int strIdx = s.link;
        if (strIdx < 0 || strIdx >= f.sections.size()) {
            throw new ElfException(".gnu.version_d has invalid sh_link");
        }
        Section str = f.sections.get(strIdx);
        r.require(s.offset, s.size);
        long cursor = s.offset;
        long end = s.offset + s.size;
        int guard = 0;
        while (cursor + 20 <= end && guard++ < 100000) {
            long vdVersion = r.u16(cursor);
            long vdFlags = r.u16(cursor + 2);
            int vdNdx = r.u16(cursor + 4);
            int vdCnt = r.u16(cursor + 6);
            long vdAux = r.u32(cursor + 12);
            long vdNext = r.u32(cursor + 16);
            List<String> names = new ArrayList<>();
            long aux = cursor + vdAux;
            for (int k = 0; k < vdCnt; k++) {
                if (aux + 8 > end) {
                    throw new ElfException("truncated verdaux");
                }
                long nameOff = r.u32(aux);
                long auxNext = r.u32(aux + 4);
                names.add(r.cstr(str.offset, nameOff));
                if (auxNext == 0) {
                    break;
                }
                aux += auxNext;
            }
            if (names.isEmpty()) {
                throw new ElfException("verdef with no names");
            }
            boolean base = vdFlags == 1;
            boolean weak = vdFlags == 2;
            f.versionDefs.add(new VersionDef(vdNdx, names.get(0),
                    names.subList(1, names.size()), base, weak));
            if (vdVersion < 1) {
                throw new ElfException("bad verdef version");
            }
            if (vdNext == 0) {
                break;
            }
            cursor += vdNext;
        }
    }

    private void parseVerneed(ElfFile f, Section s) throws ElfException {
        int strIdx = s.link;
        if (strIdx < 0 || strIdx >= f.sections.size()) {
            throw new ElfException(".gnu.version_r has invalid sh_link");
        }
        Section str = f.sections.get(strIdx);
        r.require(s.offset, s.size);
        long cursor = s.offset;
        long end = s.offset + s.size;
        int guard = 0;
        while (cursor + 16 <= end && guard++ < 100000) {
            long vnVersion = r.u16(cursor);
            int vnCnt = r.u16(cursor + 2);
            long vnFile = r.u32(cursor + 4);
            long vnAux = r.u32(cursor + 8);
            long vnNext = r.u32(cursor + 12);
            String fileName = r.cstr(str.offset, vnFile);
            List<VersionNeed.Entry> entries = new ArrayList<>();
            long aux = cursor + vnAux;
            for (int k = 0; k < vnCnt; k++) {
                if (aux + 16 > end) {
                    throw new ElfException("truncated vernaux");
                }
                long vnaFlags = r.u16(aux + 4);
                int vnaOther = r.u16(aux + 6);
                long vnaName = r.u32(aux + 8);
                long vnaNext = r.u32(aux + 12);
                entries.add(new VersionNeed.Entry(vnaOther, r.cstr(str.offset, vnaName),
                        (vnaFlags & 2) != 0));
                if (vnaNext == 0) {
                    break;
                }
                aux += vnaNext;
            }
            f.versionNeeds.add(new VersionNeed(fileName, entries));
            if (vnVersion < 1) {
                throw new ElfException("bad verneed version");
            }
            if (vnNext == 0) {
                break;
            }
            cursor += vnNext;
        }
    }

    private void parseVersym(ElfFile f, Section s) throws ElfException {
        r.require(s.offset, s.size);
        long n = s.size / 2;
        long dynCount = f.dynamicSymbols.size();
        for (int i = 0; i < n; i++) {
            int val = r.u16(s.offset + i * 2);
            if (i < dynCount) {
                f.dynVersionBySymbol.put(i, val);
            }
        }
    }

    private void assignVersionNames(ElfFile f) {
        Map<Integer, String> def = new LinkedHashMap<>();
        for (VersionDef vd : f.versionDefs) {
            def.put(vd.index, vd.name);
        }
        Map<Integer, String> need = new LinkedHashMap<>();
        Map<Integer, Boolean> weak = new LinkedHashMap<>();
        for (VersionNeed vn : f.versionNeeds) {
            for (VersionNeed.Entry e : vn.entries) {
                need.put(e.index, e.name);
                weak.put(e.index, e.weak);
            }
        }
        for (Map.Entry<Integer, Integer> e : f.dynVersionBySymbol.entrySet()) {
            int raw = e.getValue();
            int ndx = raw & 0x7fff;
            boolean hidden = (raw & 0x8000) != 0;
            String name;
            if (ndx == 0) {
                name = "LOCAL";
            } else if (ndx == 1) {
                name = null; // VER_NDX_GLOBAL, unversioned
            } else if (def.containsKey(ndx)) {
                name = def.get(ndx);
            } else if (need.containsKey(ndx)) {
                name = need.get(ndx);
            } else {
                name = "VER_" + ndx;
            }
            f.versionIndexToName.put(ndx, name);
            f.versionIndexHidden.put(ndx, hidden);
            if (e.getKey() < f.dynamicSymbols.size()) {
                Symbol sym = f.dynamicSymbols.get(e.getKey());
                sym.versionName = name;
                sym.versionHidden = hidden;
            }
        }
    }

    // ------------------------------------------------------------------ groups

    private static final long GRP_COMDAT = 0x1L;

    private void parseGroups(ElfFile f) throws ElfException {
        for (Section s : f.sections) {
            if (!s.isGroup()) {
                continue;
            }
            int signatureSymIdx = s.info;
            String signature = "";
            // group's sh_link selects the symbol table (typically .symtab)
            int symTabIdx = s.link;
            if (symTabIdx >= 0 && symTabIdx < f.sections.size()
                    && f.sections.get(symTabIdx).type == SHT_SYMTAB
                    && signatureSymIdx < f.staticSymbols.size()) {
                signature = f.staticSymbols.get(signatureSymIdx).name;
            }
            r.require(s.offset, s.size);
            if (s.size < 4 || s.size % 4 != 0) {
                throw new ElfException("SHT_GROUP size invalid");
            }
            boolean comdat = (s.flags & GRP_COMDAT) != 0;
            List<Integer> members = new ArrayList<>();
            long n = s.size / 4;
            for (long i = 1; i < n; i++) {
                members.add((int) r.u32(s.offset + i * 4));
            }
            f.groups.add(new ComdatGroup(signature, comdat, members, s.index));
        }
    }

    // ------------------------------------------------------------- relocations

    private void parseRelocations(ElfFile f) throws ElfException {
        for (Section s : f.sections) {
            if (s.type != SHT_RELA && s.type != 9 /* SHT_REL */) {
                continue;
            }
            boolean rela = s.type == SHT_RELA;
            int targetIdx = s.info;
            String targetName = targetIdx >= 0 && targetIdx < f.sections.size()
                    ? f.sections.get(targetIdx).name : "?";
            int symTabIdx = s.link;
            List<Symbol> syms = null;
            if (symTabIdx >= 0 && symTabIdx < f.sections.size()) {
                syms = f.sections.get(symTabIdx).type == SHT_DYNSYM
                        ? f.dynamicSymbols : f.staticSymbols;
            }
            r.require(s.offset, s.size);
            long ent = rela ? (elf64 ? 24 : 12) : (elf64 ? 16 : 8);
            if (s.size % ent != 0) {
                throw new ElfException("relocation section size invalid");
            }
            long n = s.size / ent;
            for (long i = 0; i < n; i++) {
                long base = s.offset + i * ent;
                long off, info, addend = 0;
                if (elf64) {
                    off = r.u64(base);
                    info = r.u64(base + 8);
                    if (rela) {
                        addend = r.u64(base + 16);
                    }
                } else {
                    off = r.u32(base);
                    info = r.u32(base + 4);
                    if (rela) {
                        addend = r.u32(base + 8);
                    }
                }
                int type = elf64 ? (int) (info & 0xffffffffL) : (int) (info & 0xff);
                long symIdx = elf64 ? info >>> 32 : info >>> 8;
                String symName = "";
                if (syms != null && symIdx >= 0 && symIdx < syms.size()) {
                    symName = syms.get((int) symIdx).name;
                } else if (symIdx != 0) {
                    throw new ElfException("relocation symbol index out of range");
                }
                String typeName = relocTypeName(machine, type);
                boolean relative = typeName.contains("RELATIVE");
                boolean copy = typeName.contains("COPY") || typeName.contains("IRELATIVE");
                f.relocations.add(new Relocation(off, type, typeName, symIdx, symName,
                        targetName, addend, relative, copy));
            }
        }
    }

    static String relocTypeName(int mach, int type) {
        if (mach == 62) { // x86_64
            return switch (type) {
                case 0 -> "R_X86_64_NONE";
                case 1 -> "R_X86_64_64";
                case 2 -> "R_X86_64_PC32";
                case 5 -> "R_X86_64_COPY";
                case 6 -> "R_X86_64_GLOB_DAT";
                case 7 -> "R_X86_64_JUMP_SLOT";
                case 8 -> "R_X86_64_RELATIVE";
                case 9 -> "R_X86_64_GOTPCREL";
                case 10 -> "R_X86_64_32";
                case 11 -> "R_X86_64_32S";
                case 37 -> "R_X86_64_IRELATIVE";
                default -> "R_X86_64_" + type;
            };
        }
        if (mach == 183) { // aarch64
            return switch (type) {
                case 0 -> "R_AARCH64_NONE";
                case 257 -> "R_AARCH64_ABS64";
                case 1024 -> "R_AARCH64_COPY";
                case 1025 -> "R_AARCH64_GLOB_DAT";
                case 1026 -> "R_AARCH64_JUMP_SLOT";
                case 1027 -> "R_AARCH64_RELATIVE";
                case 1028 -> "R_AARCH64_TLS_DTPREL64";
                case 1030 -> "R_AARCH64_IRELATIVE";
                default -> "R_AARCH64_" + type;
            };
        }
        if (mach == 3) { // x86
            return switch (type) {
                case 0 -> "R_386_NONE";
                case 1 -> "R_386_32";
                case 2 -> "R_386_PC32";
                case 6 -> "R_386_GLOB_DAT";
                case 7 -> "R_386_JMP_SLOT";
                case 8 -> "R_386_RELATIVE";
                case 9 -> "R_386_TLS_TPOFF";
                default -> "R_386_" + type;
            };
        }
        return "R_" + type;
    }

    public static boolean isCopyRelocType(int mach, int type) {
        if (mach == 62) {
            return type == 5;
        }
        return mach == 183 && type == 1024;
    }
}
