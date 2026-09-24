package arb.elf;

import arb.model.ElfObject;
import arb.model.Reloc;
import arb.model.SectionGroup;
import arb.model.SymbolDef;
import arb.model.VersionInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElfParser {

    private static final int SHT_STRTAB = 3;
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GROUP = 17;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_REL = 9;
    private static final int SHT_RELA = 4;
    private static final int SHT_GNU_verdef = 0x6ffffffd;
    private static final int SHT_GNU_verneed = 0x6ffffffe;
    private static final int SHT_GNU_versym = 0x6fffffff;

    private static final int SHN_UNDEF = 0;
    private static final int SHN_COMMON = 0xfff2;
    private static final int SHN_XINDEX = 0xffff;

    private static final int DT_NEEDED = 1;
    private static final int DT_SONAME = 14;

    private final byte[] data;
    private ByteReader r;
    private boolean elf64;
    private boolean little;
    private long shoff;
    private int shentsize;
    private int shnum;
    private int shstrndx;
    private int[] shType;
    private long[] shOffset;
    private long[] shSize;
    private int[] shLink;
    private long[] shEntSize;
    private int[] shNameOff;
    private String[] shName;
    private Map<Integer, String> sectionNameMap = new HashMap<>();

    public ElfParser(byte[] data) {
        this.data = data;
    }

    public ElfObject parse() throws ElfException {
        if (data == null || data.length < 16) {
            throw new ElfException("file too small for ELF header");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfException("bad ELF magic");
        }
        int cls = data[4] & 0xff;
        if (cls != 1 && cls != 2) {
            throw new ElfException("unsupported ELF class " + cls);
        }
        elf64 = cls == 2;
        int enc = data[5] & 0xff;
        if (enc != 1 && enc != 2) {
            throw new ElfException("unsupported endianness " + enc);
        }
        little = enc == 1;
        r = new ByteReader(data, little);

        ElfObject obj = new ElfObject();
        obj.setElf64(elf64);
        obj.setLittleEndian(little);
        obj.setType(r.u16(16));
        obj.setMachine(r.u16(18));
        if (r.u32(20) != 1) {
            throw new ElfException("unexpected ELF version");
        }

        parseHeader();
        if (shnum == 0) {
            obj.setSectionNames(List.of());
            return obj;
        }
        readSectionHeaders();
        readSectionNames(obj);
        parseAll(obj);
        return obj;
    }

    private void parseHeader() throws ElfException {
        if (elf64) {
            if (data.length < 64) {
                throw new ElfException("ELF64 header truncated");
            }
            shoff = r.u64(40);
            shentsize = r.u16(58);
            shnum = r.u16(60);
            shstrndx = r.u16(62);
        } else {
            if (data.length < 52) {
                throw new ElfException("ELF32 header truncated");
            }
            shoff = r.u32(32);
            shentsize = r.u16(46);
            shnum = r.u16(48);
            shstrndx = r.u16(50);
        }
        if (shentsize == 0 && shnum != 0) {
            throw new ElfException("zero section header entry size");
        }
        if (shnum == 0 && shoff != 0) {
            if (shoff > data.length) {
                throw new ElfException("extended section header out of file");
            }
            if (elf64) {
                shnum = (int) r.u64(shoff + 60);
                int x = (int) r.u32(shoff + 62);
                if (x != SHN_XINDEX) {
                    shstrndx = x;
                }
            } else {
                shnum = (int) r.u32(shoff + 32);
                int x = (int) r.u32(shoff + 34);
                if (x != SHN_XINDEX) {
                    shstrndx = x;
                }
            }
        }
    }

    private void readSectionHeaders() throws ElfException {
        r.check(shoff, (long) shentsize * shnum);
        shType = new int[shnum];
        shOffset = new long[shnum];
        shSize = new long[shnum];
        shLink = new int[shnum];
        shEntSize = new long[shnum];
        shNameOff = new int[shnum];
        shName = new String[shnum];
        for (int i = 0; i < shnum; i++) {
            long base = shoff + (long) i * shentsize;
            shNameOff[i] = (int) r.u32(base + (elf64 ? 0 : 0));
            if (elf64) {
                shType[i] = (int) r.u32(base + 4);
                shOffset[i] = r.u64(base + 24);
                shSize[i] = r.u64(base + 32);
                shLink[i] = (int) r.u32(base + 40);
                shEntSize[i] = r.u64(base + 56);
            } else {
                shType[i] = (int) r.u32(base + 4);
                shOffset[i] = r.u32(base + 16);
                shSize[i] = r.u32(base + 20);
                shLink[i] = (int) r.u32(base + 24);
                shEntSize[i] = r.u32(base + 36);
            }
        }
        if (shstrndx == SHN_XINDEX || shstrndx < 0 || shstrndx >= shnum) {
            if (shstrndx == SHN_XINDEX) {
                shstrndx = (int) (elf64 ? r.u32(shoff + 62) : r.u32(shoff + 34));
            }
        }
    }

    private void readSectionNames(ElfObject obj) throws ElfException {
        String[] names = new String[shnum];
        if (shstrndx >= 0 && shstrndx < shnum) {
            long strOff = shOffset[shstrndx];
            long strSize = shSize[shstrndx];
            for (int i = 0; i < shnum; i++) {
                names[i] = safeString(strOff, strSize, shNameOff[i]);
            }
        } else {
            for (int i = 0; i < shnum; i++) {
                names[i] = "";
            }
        }
        shName = names;
        List<String> list = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            list.add(names[i]);
            sectionNameMap.put(i, names[i]);
        }
        obj.setSectionNames(list);
    }

    private String safeString(long strOff, long strSize, int nameOff) throws ElfException {
        long off = strOff + (nameOff & 0xffffffffL);
        if (nameOff < 0 || off >= data.length || off < strOff
                || off >= strOff + strSize) {
            return "";
        }
        int max = (int) Math.min(strSize - (nameOff & 0xffffffffL), data.length - off);
        return r.cstring(off, max);
    }

    private void parseAll(ElfObject obj) throws ElfException {
        int dynsymIdx = -1;
        int symtabIdx = -1;
        int versymIdx = -1;
        for (int i = 0; i < shnum; i++) {
            switch (shType[i]) {
                case SHT_GNU_verdef -> parseVerdef(i, obj);
                case SHT_GNU_verneed -> parseVerneed(i, obj);
                case SHT_GNU_versym -> versymIdx = i;
                case SHT_GROUP -> parseGroup(i, obj);
                case SHT_DYNAMIC -> parseDynamic(i, obj);
                case SHT_SYMTAB -> symtabIdx = i;
                case SHT_DYNSYM -> dynsymIdx = i;
                default -> {
                }
            }
        }
        if (symtabIdx >= 0) {
            obj.setSymbols(parseSymtab(symtabIdx, false));
        }
        if (dynsymIdx >= 0) {
            int[] vers = versymIdx >= 0 ? parseVersym(versymIdx, obj) : null;
            obj.setDynamicSymbols(parseSymtab(dynsymIdx, true, vers, obj));
        }
        parseRelocations(obj);
    }

    private long[] strtabOf(int link) {
        return null;
    }

    private List<SymbolDef> parseSymtab(int idx, boolean dyn) throws ElfException {
        return parseSymtab(idx, dyn, null, null);
    }

    private List<SymbolDef> parseSymtab(int idx, boolean dyn, int[] vers, ElfObject obj)
            throws ElfException {
        int link = shLink[idx];
        if (link < 0 || link >= shnum) {
            throw new ElfException("symbol table link out of range");
        }
        long strOff = shOffset[link];
        long strSize = shSize[link];
        long entSize = shEntSize[idx] != 0
                ? shEntSize[idx]
                : (elf64 ? 24 : 16);
        long count = shSize[idx] / entSize;
        if (count > 1_000_000) {
            throw new ElfException("symbol count implausible: " + count);
        }
        List<SymbolDef> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long base = shOffset[idx] + i * entSize;
            int nameOff;
            long value;
            long size;
            int info;
            int other;
            int shndx;
            if (elf64) {
                nameOff = (int) r.u32(base);
                info = r.u8(base + 4);
                other = r.u8(base + 5);
                shndx = r.u16(base + 6);
                value = r.u64(base + 8);
                size = r.u64(base + 16);
            } else {
                nameOff = (int) r.u32(base);
                value = r.u32(base + 4);
                size = r.u32(base + 8);
                info = r.u8(base + 12);
                other = r.u8(base + 13);
                shndx = r.u16(base + 14);
            }
            int bindRaw = info >> 4;
            int typeRaw = info & 0xf;
            int visRaw = other & 0x3;
            String name = safeString(strOff, strSize, nameOff);
            int resolvedShndx = resolveShndx(shndx, idx, i);
            int verIdx = vers != null && i < vers.length ? vers[i] : 0;
            String version = dyn && obj != null ? resolveVersion(verIdx, obj)
                    : (verIdx != 0 ? String.valueOf(verIdx) : null);
            out.add(new SymbolDef(i, name, value, size,
                    arb.model.Enums.Binding.from(bindRaw),
                    arb.model.Enums.SymbolType.from(typeRaw),
                    arb.model.Enums.Visibility.from(visRaw),
                    resolvedShndx, dyn, verIdx & 0xffff, version));
        }
        return out;
    }

    private int resolveShndx(int shndx, int symIdx, int symNo) throws ElfException {
        if (shndx != SHN_XINDEX) {
            return shndx;
        }
        int xlink = shLink[symIdx];
        if (xlink < 0 || xlink >= shnum || shType[xlink] != SHT_SYMTAB_SHNDX) {
            return shndx;
        }
        long ent = elf64 ? 4 : 4;
        return (int) r.u32(shOffset[xlink] + symNo * ent);
    }

    private static final int SHT_SYMTAB_SHNDX = 18;

    private int[] parseVersym(int idx, ElfObject obj) throws ElfException {
        long count = shSize[idx] / 2;
        if (count > 5_000_000) {
            throw new ElfException("versym count implausible");
        }
        int[] out = new int[(int) count];
        for (int i = 0; i < count; i++) {
            out[i] = r.u16(shOffset[idx] + i * 2L);
        }
        return out;
    }

    private String resolveVersion(int verIdxRaw, ElfObject obj) {
        int idx = verIdxRaw & 0x7fff;
        if (idx == 0) {
            return null;
        }
        if (idx == 1) {
            return verIdxRaw == 1 ? null : "*local*";
        }
        for (VersionInfo.Def d : obj.getVersionDefs()) {
            if (d.index() == idx) {
                return d.name();
            }
        }
        for (VersionInfo.Need n : obj.getVersionNeeds()) {
            if (n.index() == idx) {
                return n.name();
            }
        }
        return null;
    }

    private void parseVerdef(int idx, ElfObject obj) throws ElfException {
        int link = shLink[idx];
        long strOff = link >= 0 && link < shnum ? shOffset[link] : 0;
        long strSize = link >= 0 && link < shnum ? shSize[link] : 0;
        long p = shOffset[idx];
        long end = p + shSize[idx];
        for (int vdCount = 0; p + (elf64 ? 20 : 20) <= end && vdCount < 100000; vdCount++) {
            long vdVersion = elf64 ? r.u32(p) : r.u16(p);
            if (vdVersion != 1) {
                break;
            }
            int flags = (int) r.u32(p + (elf64 ? 4 : 4));
            int ndx = (int) r.u16(p + (elf64 ? 8 : 8));
            long auxOffset = r.u32(p + (elf64 ? 12 : 12));
            long nextOffset = r.u32(p + (elf64 ? 16 : 16));
            String firstName = null;
            List<String> aliases = new ArrayList<>();
            long ap = p + auxOffset;
            int vdaNameOff;
            long vdaNext;
            if (auxOffset != 0 && ap < end) {
                int nameOff = (int) r.u32(ap + 4);
                vdaNext = r.u32(ap + 8);
                firstName = safeString(strOff, strSize, nameOff);
                long cur = vdaNext;
                while (cur != 0 && ap + cur + 12 <= end) {
                    long np = ap + cur;
                    int no2 = (int) r.u32(np + 4);
                    aliases.add(safeString(strOff, strSize, no2));
                    cur = r.u32(np + 8);
                }
            }
            boolean hidden = (flags & 0x8000) != 0;
            boolean base = (flags & 0x1) != 0;
            obj.getVersionDefs().add(new VersionInfo.Def(ndx, firstName, null,
                    base, hidden, aliases));
            if (nextOffset == 0) {
                break;
            }
            p += nextOffset;
        }
    }

    private void parseVerneed(int idx, ElfObject obj) throws ElfException {
        int link = shLink[idx];
        long strOff = link >= 0 && link < shnum ? shOffset[link] : 0;
        long strSize = link >= 0 && link < shnum ? shSize[link] : 0;
        long p = shOffset[idx];
        long end = p + shSize[idx];
        for (int vnCount = 0; p + 16 <= end && vnCount < 100000; vnCount++) {
            long vnVersion = r.u16(p);
            if (vnVersion != 1) {
                break;
            }
            int fileOff = (int) r.u32(p + 4);
            long auxOffset = r.u32(p + 8);
            long nextOffset = r.u32(p + 12);
            String file = safeString(strOff, strSize, fileOff);
            long ap = p + auxOffset;
            if (auxOffset != 0 && ap + 16 <= end) {
                int vnaHash = (int) r.u32(ap);
                int vnaFlags = (int) r.u16(ap + 4);
                int vnaOther = (int) r.u16(ap + 6);
                int nameOff = (int) r.u32(ap + 8);
                String vname = safeString(strOff, strSize, nameOff);
                boolean weak = (vnaFlags & 2) != 0;
                obj.getVersionNeeds().add(new VersionInfo.Need(vnaOther, vname, file));
                long cur = r.u32(ap + 12);
                while (cur != 0 && ap + cur + 16 <= end) {
                    long np = ap + cur;
                    int other2 = (int) r.u16(np + 6);
                    int no2 = (int) r.u32(np + 8);
                    obj.getVersionNeeds().add(new VersionInfo.Need(other2,
                            safeString(strOff, strSize, no2), file));
                    cur = r.u32(np + 12);
                }
            }
            if (nextOffset == 0) {
                break;
            }
            p += nextOffset;
        }
    }

    private void parseGroup(int idx, ElfObject obj) throws ElfException {
        if (shSize[idx] < 4) {
            return;
        }
        int flags = r.u32(shOffset[idx]);
        long count = (shSize[idx] - 4) / 4;
        List<Integer> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            members.add((int) r.u32(shOffset[idx] + 4 + i * 4L));
        }
        int symLink = shLink[idx];
        String signature = "";
        if (symLink >= 0 && symLink < shnum) {
            int symIdx = (int) (shEntSize[idx] != 0 ? 0 : 0);
            signature = groupSignature(symLink, idx);
        }
        obj.getGroups().add(new SectionGroup(idx, signature, flags, members));
    }

    private String groupSignature(int symtabLink, int groupIdx) throws ElfException {
        int symIndex = (int) shEntSize[groupIdx] == 0
                ? 0
                : (int) (shEntSize[groupIdx] & 0xffffffffL);
        // sh_info holds the associated symbol index
        long infoOff = shoff + (long) groupIdx * shentsize;
        int associatedSym = (int) r.u32(infoOff + (elf64 ? 44 : 28));
        long entSize = shEntSize[symtabLink] != 0
                ? shEntSize[symtabLink]
                : (elf64 ? 24 : 16);
        int strLink = shLink[symtabLink];
        long strOff = shOffset[strLink];
        long strSize = shSize[strLink];
        long base = shOffset[symtabLink] + associatedSym * entSize;
        int nameOff = (int) r.u32(base);
        return safeString(strOff, strSize, nameOff);
    }

    private static boolean isCopyRelocType(long type, int machine) {
        return switch (machine) {
            case 62 -> type == 5;        // R_X86_64_COPY
            case 3 -> type == 10;        // R_386_COPY
            case 183 -> type == 1024;    // R_AARCH64_COPY
            case 243 -> type == 26;      // R_RISCV_COPY
            default -> false;
        };
    }

    private void parseRelocations(ElfObject obj) throws ElfException {
        for (int i = 0; i < shnum; i++) {
            if (shType[i] != SHT_REL && shType[i] != SHT_RELA) {
                continue;
            }
            boolean rela = shType[i] == SHT_RELA;
            int symLink = shLink[i];
            List<SymbolDef> syms;
            long strOff = 0;
            long strSize = 0;
            if (symLink >= 0 && symLink < shnum) {
                if (shType[symLink] == SHT_DYNSYM) {
                    syms = obj.getDynamicSymbols();
                } else {
                    syms = obj.getSymbols();
                }
                int strLink = shLink[symLink];
                if (strLink >= 0 && strLink < shnum) {
                    strOff = shOffset[strLink];
                    strSize = shSize[strLink];
                }
            } else {
                syms = List.of();
            }
            int targetSection = (int) r.u32(shoff + (long) i * shentsize
                    + (elf64 ? 28 : 16));
            String targetName = targetSection >= 0 && targetSection < shnum
                    ? shName[targetSection] : "";
            long entSize = shEntSize[i] != 0
                    ? shEntSize[i]
                    : (elf64 ? (rela ? 24 : 16) : (rela ? 12 : 8));
            long count = shSize[i] / entSize;
            for (int j = 0; j < count; j++) {
                long base = shOffset[i] + j * entSize;
                long offset = elf64 ? r.u64(base) : r.u32(base);
                long info = elf64 ? r.u64(base + 8) : r.u32(base + 4);
                long symIndex = elf64 ? info >>> 32 : info >>> 8;
                long type = elf64 ? info & 0xffffffffL : info & 0xff;
                long addend = 0;
                if (rela) {
                    addend = elf64 ? r.u64(base + 16) : r.u32(base + 8);
                }
                String symName = "";
                if (symIndex < syms.size()) {
                    symName = syms.get((int) symIndex).name();
                }
                boolean copy = isCopyRelocType(type, obj.getMachine());
                obj.getRelocations().add(new Reloc(offset, type, (int) symIndex,
                        symName, addend, false, targetName, copy));
            }
        }
    }

    private void parseDynamic(int idx, ElfObject obj) throws ElfException {
        long entSize = shEntSize[idx] != 0
                ? shEntSize[idx]
                : (elf64 ? 16 : 8);
        long count = shSize[idx] / entSize;
        int strLink = shLink[idx];
        long strOff = strLink >= 0 && strLink < shnum ? shOffset[strLink] : 0;
        long strSize = strLink >= 0 && strLink < shnum ? shSize[strLink] : 0;
        for (int i = 0; i < count; i++) {
            long base = shOffset[idx] + i * entSize;
            long tag = elf64 ? r.u64(base) : r.u32(base);
            long val = elf64 ? r.u64(base + 8) : r.u32(base + 4);
            if (tag == 0) {
                break;
            }
            if (tag == DT_NEEDED) {
                obj.getNeeded().add(safeString(strOff, strSize, (int) val));
            } else if (tag == DT_SONAME) {
                obj.setSoname(safeString(strOff, strSize, (int) val));
            }
        }
    }
}
