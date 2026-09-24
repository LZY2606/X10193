package court.elf;

import java.util.ArrayList;
import java.util.List;

import static court.elf.ElfConstants.*;

/**
 * Parses the ELF subset used by the arbitration court: symbol/dynamic-symbol tables,
 * version definitions and needs, visibility, section groups (COMDAT) and relocations.
 * Supports ELF32/64 in either endianness. Every length/index is bounds-checked; a
 * malformed object raises {@link ElfFormatException} and is isolated by the importer.
 */
public final class ElfParser {

    public ElfModels.ElfFile parse(byte[] data) {
        if (data == null || data.length < 16) {
            throw new ElfFormatException("file too short for ELF magic");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfFormatException("bad ELF magic");
        }
        final ElfModels.Class elfClass = switch (data[4] & 0xff) {
            case 1 -> ElfModels.Class.ELF32;
            case 2 -> ElfModels.Class.ELF64;
            default -> throw new ElfFormatException("unknown EI_CLASS");
        };
        final boolean le = switch (data[5] & 0xff) {
            case 1 -> true;
            case 2 -> false;
            default -> throw new ElfFormatException("unknown EI_DATA");
        };
        if ((data[6] & 0xff) != 1) {
            throw new ElfFormatException("unsupported EI_VERSION");
        }
        ElfReader r = new ElfReader(data, le);
        HeaderInfo hi = readHeader(r, elfClass);
        List<ElfModels.Section> sections = readSectionHeaders(r, hi.header);
        sections = applySectionNames(r, sections, hi.header.shstrndx);

        List<ElfModels.Symbol> staticSymbols = new ArrayList<>();
        List<ElfModels.Symbol> dynSymbols = new ArrayList<>();
        List<Integer> versym = new ArrayList<>();
        List<ElfModels.VersionDef> verdefs = new ArrayList<>();
        List<ElfModels.VersionNeed> verneeds = new ArrayList<>();
        List<ElfModels.Group> groups = new ArrayList<>();
        String soname = null;
        List<String> needed = new ArrayList<>();

        for (ElfModels.Section s : sections) {
            switch (s.type) {
                case SHT_SYMTAB -> staticSymbols.addAll(readSymtab(r, hi.header, s, sections, false));
                case SHT_DYNSYM -> dynSymbols.addAll(readSymtab(r, hi.header, s, sections, true));
                case SHT_GNU_versym -> versym.addAll(readVersym(r, s));
                case SHT_GNU_verdef -> verdefs.addAll(readVerdef(r, s, sections));
                case SHT_GNU_verneed -> verneeds.addAll(readVerneed(r, s, sections));
                case SHT_GROUP -> groups.add(readGroup(r, hi.header, s, sections));
                case SHT_DYNAMIC -> {
                    DynInfo info = readDynamic(r, hi.header, s, sections);
                    soname = info.soname;
                    needed = info.needed;
                }
                default -> { }
            }
        }
        dynSymbols = attachVersions(dynSymbols, versym);
        List<ElfModels.Relocation> staticRels = readRelocations(r, hi.header, sections, false);
        List<ElfModels.Relocation> dynRels = readRelocations(r, hi.header, sections, true);
        return new ElfModels.ElfFile(hi.header, sections, staticSymbols, dynSymbols, staticRels,
                dynRels, verdefs, verneeds, groups, soname, needed);
    }

    private record HeaderInfo(ElfModels.Header header) {
    }

    private static final class DynInfo {
        String soname;
        List<String> needed = new ArrayList<>();
    }

    private HeaderInfo readHeader(ElfReader r, ElfModels.Class c) {
        int type = r.u16(16);
        if (type < ET_NONE || type > ET_CORE) {
            throw new ElfFormatException("unknown e_type " + type);
        }
        int machine = r.u16(18);
        int shentsize = c == ElfModels.Class.ELF64 ? r.u16(58) : r.u16(46);
        int shnum = c == ElfModels.Class.ELF64 ? r.u16(60) : r.u16(48);
        int shstrndx = c == ElfModels.Class.ELF64 ? r.u16(62) : r.u16(50);
        int minEnt = c == ElfModels.Class.ELF64 ? 64 : 40;
        if (shnum <= 0 || shnum > 65535) {
            throw new ElfFormatException("bad e_shnum " + shnum);
        }
        if (shentsize < minEnt) {
            throw new ElfFormatException("e_shentsize too small: " + shentsize);
        }
        if (shstrndx <= 0 || shstrndx >= shnum) {
            throw new ElfFormatException("e_shstrndx out of range: " + shstrndx);
        }
        if (c == ElfModels.Class.ELF64) {
            return new HeaderInfo(new ElfModels.Header(c, readEndian(r), type, machine, r.u64(24),
                    r.u64(32), r.u64(40), r.u16(54), r.u16(56), shentsize, shnum, shstrndx));
        }
        return new HeaderInfo(new ElfModels.Header(c, readEndian(r), type, machine, r.u32(24),
                r.u32(28), r.u32(32), r.u16(42), r.u16(44), shentsize, shnum, shstrndx));
    }

    private ElfModels.Endian readEndian(ElfReader r) {
        return r.u16(18) == 0 ? ElfModels.Endian.BIG : ElfModels.Endian.LITTLE;
    }

    private List<ElfModels.Section> readSectionHeaders(ElfReader r, ElfModels.Header h) {
        long end = h.shoff + (long) h.shnum * h.shentsize;
        if (h.shoff < 1 || end < h.shoff || end > Integer.MAX_VALUE) {
            throw new ElfFormatException("section header table out of range");
        }
        List<ElfModels.Section> out = new ArrayList<>(h.shnum);
        for (int i = 0; i < h.shnum; i++) {
            long o = h.shoff + (long) i * h.shentsize;
            if (h.elfClass == ElfModels.Class.ELF64) {
                out.add(new ElfModels.Section(i, "", r.u32(o), r.u64(o + 8), r.u64(o + 16),
                        r.u64(o + 24), r.u64(o + 32), (int) r.u32(o + 40), (int) r.u32(o + 44),
                        r.u64(o + 48), r.u64(o + 56)));
            } else {
                out.add(new ElfModels.Section(i, "", r.u32(o), r.u32(o + 8), r.u32(o + 12),
                        r.u32(o + 16), r.u32(o + 20), r.u16(o + 24), r.u16(o + 26),
                        r.u32(o + 28), r.u32(o + 32)));
            }
        }
        return out;
    }

    private List<ElfModels.Section> applySectionNames(ElfReader r,
                                                       List<ElfModels.Section> sections,
                                                       int shstrndx) {
        ElfModels.Section str = sections.get(shstrndx);
        if (str.type != SHT_STRTAB) {
            throw new ElfFormatException("e_shstrndx does not reference a string table");
        }
        List<ElfModels.Section> named = new ArrayList<>(sections.size());
        for (ElfModels.Section s : sections) {
            String name = s.index == 0 ? "" : nameIn(r, str, s.name);
            named.add(new ElfModels.Section(s.index, name, s.type, s.flags, s.addr, s.offset, s.size,
                    s.link, s.info, s.addralign, s.entsize));
        }
        return named;
    }

    private String nameIn(ElfReader r, ElfModels.Section str, long off) {
        if (off < 0 || off >= str.size) {
            throw new ElfFormatException("strtab name out of range: " + off);
        }
        return r.strtabString(str.offset, str.size, off);
    }

    private List<ElfModels.Symbol> readSymtab(ElfReader r, ElfModels.Header h, ElfModels.Section s,
                                              List<ElfModels.Section> sections, boolean dynamic) {
        if (s.link <= 0 || s.link >= sections.size()) {
            throw new ElfFormatException("symtab sh_link invalid: " + s.link);
        }
        ElfModels.Section str = sections.get(s.link);
        if (str.type != SHT_STRTAB) {
            throw new ElfFormatException("symtab sh_link is not a string table");
        }
        long entSize = s.entsize != 0
                ? s.entsize
                : (h.elfClass == ElfModels.Class.ELF64 ? 24 : 16);
        long minEnt = h.elfClass == ElfModels.Class.ELF64 ? 24 : 16;
        if (entSize < minEnt || s.size < 0 || s.size % entSize != 0) {
            throw new ElfFormatException(
                    "symtab size/entsize inconsistent: size=" + s.size + " entsize=" + entSize);
        }
        int count = (int) (s.size / entSize);
        List<ElfModels.Symbol> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long o = s.offset + (long) i * entSize;
            if (h.elfClass == ElfModels.Class.ELF64) {
                int nameOff = (int) r.u32(o);
                int info = r.u8(o + 4);
                int other = r.u8(o + 5);
                int shndx = r.u16(o + 6);
                out.add(new ElfModels.Symbol(i, symName(r, str, nameOff), r.u64(o + 8),
                        r.u64(o + 16), info >>> 4, info & 0xf, other & 0x3, shndx, dynamic, -1));
            } else {
                int nameOff = (int) r.u32(o);
                int info = r.u8(o + 12);
                int other = r.u8(o + 13);
                int shndx = r.u16(o + 14);
                out.add(new ElfModels.Symbol(i, symName(r, str, nameOff), r.u32(o + 4),
                        r.u32(o + 8), info >>> 4, info & 0xf, other & 0x3, shndx, dynamic, -1));
            }
        }
        return out;
    }

    private String symName(ElfReader r, ElfModels.Section str, int nameOff) {
        long u = Integer.toUnsignedLong(nameOff);
        if (nameOff == 0) {
            return "";
        }
        if (u >= str.size) {
            throw new ElfFormatException("symbol name offset out of range: " + u);
        }
        return r.strtabString(str.offset, str.size, u);
    }

    private List<Integer> readVersym(ElfReader r, ElfModels.Section s) {
        if (s.size < 0 || s.size % 2 != 0) {
            throw new ElfFormatException("versym size not a multiple of 2: " + s.size);
        }
        int count = (int) (s.size / 2);
        List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(r.u16(s.offset + (long) i * 2));
        }
        return out;
    }

    private List<ElfModels.Symbol> attachVersions(List<ElfModels.Symbol> syms,
                                                  List<Integer> versym) {
        List<ElfModels.Symbol> out = new ArrayList<>(syms.size());
        for (int i = 0; i < syms.size(); i++) {
            ElfModels.Symbol sym = syms.get(i);
            int raw = i < versym.size() ? versym.get(i) : VER_NDX_GLOBAL;
            out.add(new ElfModels.Symbol(sym.index, sym.name, sym.value, sym.size, sym.bind,
                    sym.type, sym.visibility, sym.sectionIndex, sym.dynamic, raw));
        }
        return out;
    }

    private List<ElfModels.VersionDef> readVerdef(ElfReader r, ElfModels.Section s,
                                                  List<ElfModels.Section> sections) {
        if (s.link <= 0 || s.link >= sections.size()) {
            throw new ElfFormatException("verdef sh_link invalid: " + s.link);
        }
        ElfModels.Section str = sections.get(s.link);
        List<ElfModels.VersionDef> out = new ArrayList<>();
        long off = 0;
        int guard = 0;
        do {
            if (guard++ > 10000) {
                throw new ElfFormatException("verdef chain cycle");
            }
            if (off < 0 || off + 20 > s.size) {
                throw new ElfFormatException("verdef entry out of range: " + off);
            }
            long base = s.offset + off;
            int flags = r.u16(base + 4);
            int ndx = r.u16(base + 10);
            int cnt = r.u16(base + 12);
            long aux = r.u32(base + 16);
            long next = r.u32(base);
            if (cnt < 1 || cnt > 256) {
                throw new ElfFormatException("verdef aux count out of range: " + cnt);
            }
            String name = null;
            List<String> parents = new ArrayList<>();
            long auxOff = aux;
            for (int i = 0; i < cnt; i++) {
                if (auxOff == 0 || auxOff + 8 > s.size) {
                    throw new ElfFormatException("verdef aux out of range");
                }
                long ab = s.offset + auxOff;
                long nameOff = r.u32(ab + 4);
                String vname = nameIn(r, str, nameOff);
                if (i == 0) {
                    name = vname;
                } else {
                    parents.add(vname);
                }
                auxOff = r.u32(ab);
                if (i + 1 < cnt && auxOff == 0) {
                    throw new ElfFormatException("verdef aux chain truncated");
                }
            }
            out.add(new ElfModels.VersionDef(ndx, name, parents, (flags & VER_FLG_BASE) != 0,
                    (flags & VER_FLG_WEAK) != 0));
            off = next;
        } while (off != 0);
        return out;
    }

    private List<ElfModels.VersionNeed> readVerneed(ElfReader r, ElfModels.Section s,
                                                    List<ElfModels.Section> sections) {
        if (s.link <= 0 || s.link >= sections.size()) {
            throw new ElfFormatException("verneed sh_link invalid: " + s.link);
        }
        ElfModels.Section str = sections.get(s.link);
        List<ElfModels.VersionNeed> out = new ArrayList<>();
        long off = 0;
        int guard = 0;
        do {
            if (guard++ > 10000) {
                throw new ElfFormatException("verneed chain cycle");
            }
            if (off < 0 || off + 16 > s.size) {
                throw new ElfFormatException("verneed entry out of range: " + off);
            }
            long base = s.offset + off;
            long fileOff = r.u32(base + 8);
            int auxCnt = r.u16(base + 12);
            long auxOff = r.u32(base + 4);
            long next = r.u32(base);
            String file = nameIn(r, str, fileOff);
            if (auxCnt < 1) {
                throw new ElfFormatException("verneed aux count zero");
            }
            long ao = auxOff;
            for (int i = 0; i < auxCnt; i++) {
                if (ao == 0 || ao + 16 > s.size) {
                    throw new ElfFormatException("vernaux out of range");
                }
                long ab = s.offset + ao;
                int flags = r.u16(base + 2);
                int other = r.u16(ab + 6);
                long nameOff = r.u32(ab + 12);
                long auxNext = r.u32(ab);
                String vname = nameIn(r, str, nameOff);
                boolean weak = (flags & VER_NEED_WEAK) != 0;
                out.add(new ElfModels.VersionNeed(file, other, vname, weak));
                ao = auxNext;
                if (i + 1 < auxCnt && ao == 0) {
                    throw new ElfFormatException("vernaux chain truncated");
                }
            }
            off = next;
        } while (off != 0);
        return out;
    }

    private ElfModels.Group readGroup(ElfReader r, ElfModels.Header header, ElfModels.Section s,
                                      List<ElfModels.Section> sections) {
        if (s.size < 8 || (s.size - 4) % 4 != 0) {
            throw new ElfFormatException("group section bad size: " + s.size);
        }
        if (s.link <= 0 || s.link >= sections.size()) {
            throw new ElfFormatException("group sh_link invalid: " + s.link);
        }
        ElfModels.Section symtab = sections.get(s.link);
        List<ElfModels.Symbol> syms = readSymtab(r, header, symtab, sections,
                symtab.type == SHT_DYNSYM);
        int flags = (int) r.u32(s.offset);
        int memberCount = (int) ((s.size - 4) / 4);
        long sigIdx = r.u32(s.offset + 4);
        if (sigIdx >= syms.size()) {
            throw new ElfFormatException("group signature symbol out of range: " + sigIdx);
        }
        String signature = syms.get((int) sigIdx).name;
        List<Integer> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            int sec = (int) r.u32(s.offset + 4 + (long) i * 4);
            if (sec < 0 || sec >= sections.size()) {
                throw new ElfFormatException("group member section out of range: " + sec);
            }
            members.add(sec);
        }
        return new ElfModels.Group(signature, flags, members);
    }
}
