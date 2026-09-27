package io.symbolcourt.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java ELF parser. Supports ELF{32,64} LE/BE, ET_REL and ET_DYN/EXEC,
 * static and dynamic symbol tables, GNU symbol versioning (.gnu.version*,
 * PT_DYNAMIC fallback), section groups (COMDAT), and REL/RELA relocations.
 *
 * No system linker is invoked. All structural reads are bounds checked; a
 * malformed image raises {@link ElfException} that the importer isolates to
 * the single object.
 */
public final class ElfParser {

    // section types
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_RELA = 4;
    private static final int SHT_HASH = 5;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_REL = 9;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GROUP = 17;
    private static final long SHT_GNU_VERDEF = 0x6ffffffd;
    private static final long SHT_GNU_VERNEED = 0x6ffffffe;
    private static final long SHT_GNU_VERSYM = 0x6fffffff;

    // dynamic tags
    private static final long DT_NULL = 0;
    private static final long DT_NEEDED = 1;
    private static final long DT_SONAME = 14;
    private static final long DT_STRTAB = 5;
    private static final long DT_SYMTAB = 6;
    private static final long DT_STRSZ = 10;
    private static final long DT_SYMENT = 11;
    private static final long DT_VERSYM = 0x6ffffff0;
    private static final long DT_VERDEF = 0x6ffffffc;
    private static final long DT_VERDEFNUM = 0x6ffffffd;
    private static final long DT_VERNEED = 0x6ffffffe;
    private static final long DT_VERNEEDNUM = 0x6fffffff;

    private ElfParser() {
    }

    public static ElfImage parse(byte[] data) {
        if (data == null || data.length < 16) {
            throw new ElfException("file too small to be ELF");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfException("bad ELF magic");
        }
        int eiClass = data[4] & 0xff;
        if (eiClass != 1 && eiClass != 2) {
            throw new ElfException("unsupported ELF class " + eiClass);
        }
        boolean elf64 = eiClass == 2;
        int eiData = data[5] & 0xff;
        if (eiData != 1 && eiData != 2) {
            throw new ElfException("unsupported ELF data encoding " + eiData);
        }
        boolean le = eiData == 1;
        ByteReader r = new ByteReader(data, le);
        // version byte
        if ((data[6] & 0xff) != 1) {
            throw new ElfException("unsupported EI_VERSION");
        }
        ElfImage img = new ElfImage();
        img.elf64 = elf64;
        img.littleEndian = le;
        parseHeader(img, r);
        parseSections(img, r);
        parseSymbolTables(img, r);
        parseVersionSections(img, r);
        parseGroups(img, r);
        parseRelocations(img, r);
        parseDynamicFallback(img, r);
        resolveVersionNames(img);
        return img;
    }

    private static void parseHeader(ElfImage img, ByteReader r) {
        if (img.elf64) {
            r.require(0, 64);
        } else {
            r.require(0, 52);
        }
        img.elfType = r.u16(16);
        img.machine = r.u16(18);
        img.machineName = machineName(img.machine);
        if (img.elf64) {
            img.entry = r.u64(24);
            img.flags = r.u32(48);
        } else {
            img.entry = r.u32(24);
            img.flags = r.u32(36);
        }
        if (img.elfType != 1 && img.elfType != 2 && img.elfType != 3) {
            throw new ElfException("unsupported e_type " + img.elfType);
        }
    }

    private static String machineName(int m) {
        return switch (m) {
            case 3 -> "EM_386";
            case 8 -> "EM_MIPS";
            case 20 -> "EM_PPC";
            case 40 -> "EM_ARM";
            case 62 -> "EM_X86_64";
            case 183 -> "EM_AARCH64";
            case 243 -> "EM_RISCV";
            default -> "EM_" + m;
        };
    }

    private static int[] headerTable(ElfImage img, ByteReader r) {
        int e_shoff, e_shentsize, e_shnum, e_shstrndx;
        if (img.elf64) {
            e_shoff = (int) r.u64(40);
            e_shentsize = r.u16(58);
            e_shnum = r.u16(60);
            e_shstrndx = r.u16(62);
        } else {
            e_shoff = (int) r.u32(32);
            e_shentsize = r.u16(46);
            e_shnum = r.u16(48);
            e_shstrndx = r.u16(50);
        }
        return new int[]{e_shoff, e_shentsize, e_shnum, e_shstrndx};
    }

    private static void parseSections(ElfImage img, ByteReader r) {
        int[] h = headerTable(img, r);
        int shoff = h[0], shentsize = h[1], shnum = h[2], shstrndx = h[3];
        if (shoff == 0) {
            return; // section headers stripped; dynamic fallback handles DSOs
        }
        int minEnt = img.elf64 ? 64 : 40;
        if (shentsize < minEnt) {
            throw new ElfException("e_shentsize too small: " + shentsize);
        }
        if (shnum == 0) {
            // real count lives in section 0 sh_size
            ElfSection zero = readSection(img, r, shoff, 0, shentsize);
            shnum = (int) zero.size;
            if (shstrndx == 0xffff) {
                shstrndx = zero.link;
            }
        }
        if ((long) shoff + (long) shnum * shentsize > r.length()) {
            throw new ElfException("section header table runs past EOF");
        }
        for (int i = 0; i < shnum; i++) {
            ElfSection s = readSection(img, r, shoff + i * shentsize, i, shentsize);
            img.sections.add(s);
        }
        ElfSection shstr = img.section(shstrndx);
        if (shstr != null && shstr.isStringTable()) {
            for (ElfSection s : img.sections) {
                if (s.index == 0) {
                    continue;
                }
                s.name = safeString(r, shstr, (int) s.headerNameOffset);
            }
        }
    }

    private static ElfSection readSection(ElfImage img, ByteReader r, int off, int idx, int entsize) {
        ElfSection s = new ElfSection();
        s.index = idx;
        if (img.elf64) {
            s.headerNameOffset = r.u32(off);
            s.type = r.u32(off + 4);
            s.flags = r.u64(off + 8);
            s.addr = r.u64(off + 16);
            s.offset = r.u64(off + 24);
            s.size = r.u64(off + 32);
            s.link = (int) r.u32(off + 40);
            s.info = (int) r.u32(off + 44);
            s.addralign = r.u64(off + 48);
            s.entsize = r.u64(off + 56);
        } else {
            s.headerNameOffset = r.u32(off);
            s.type = r.u32(off + 4);
            s.flags = r.u32(off + 8);
            s.addr = r.u32(off + 12);
            s.offset = r.u32(off + 16);
            s.size = r.u32(off + 20);
            s.link = (int) r.u32(off + 24);
            s.info = (int) r.u32(off + 28);
            s.addralign = r.u32(off + 32);
            s.entsize = r.u32(off + 36);
        }
        return s;
    }

    private static String safeString(ByteReader r, ElfSection str, int index) {
        try {
            return r.strtabString((int) str.offset, (int) str.size, index);
        } catch (ElfException e) {
            return "";
        }
    }

    private static void parseSymbolTables(ElfImage img, ByteReader r) {
        for (ElfSection s : img.sections) {
            if (!s.isSymbolTable()) {
                continue;
            }
            ElfSection str = img.section(s.link);
            if (str == null || !str.isStringTable()) {
                throw new ElfException(s.name + " has no linked string table");
            }
            int ent = s.entsize > 0 ? (int) s.entsize : (img.elf64 ? 24 : 16);
            if (ent < (img.elf64 ? 24 : 16)) {
                throw new ElfException(s.name + " sh_entsize too small");
            }
            long count = s.size / ent;
            if (s.offset + count * ent > r.length()) {
                throw new ElfException(s.name + " runs past EOF");
            }
            List<ElfSymbol> target = s.isDynamicSymbolTable() ? img.dynamicSymbols : img.symbols;
            boolean dynamic = s.isDynamicSymbolTable();
            for (int i = 0; i < count; i++) {
                int base = (int) (s.offset + (long) i * ent);
                ElfSymbol sym = readSymbol(img, r, base, i, dynamic);
                sym.name = safeString(r, str, (int) sym.nameOffset);
                target.add(sym);
            }
        }
        // version indices are attached later in resolveVersionNames
    }

    private static ElfSymbol readSymbol(ElfImage img, ByteReader r, int off, int idx, boolean dynamic) {
        ElfSymbol s = new ElfSymbol();
        s.index = idx;
        s.dynamic = dynamic;
        s.versionIndex = -1;
        if (img.elf64) {
            s.nameOffset = r.u32(off);
            int info = r.u8(off + 4) & 0xff;
            s.rawInfo = info;
            s.binding = info >> 4;
            s.type = info & 0xf;
            s.visibility = r.u8(off + 5) & 0x3;
            s.sectionIndex = (short) r.u16(off + 6);
            s.value = r.u64(off + 8);
            s.size = r.u64(off + 16);
        } else {
            s.nameOffset = r.u32(off);
            s.value = r.u32(off + 4);
            s.size = r.u32(off + 8);
            int info = r.u8(off + 12) & 0xff;
            s.rawInfo = info;
            s.binding = info >> 4;
            s.type = info & 0xf;
            s.visibility = r.u8(off + 13) & 0x3;
            s.sectionIndex = (short) r.u16(off + 14);
        }
        return s;
    }

    private static void parseVersionSections(ElfImage img, ByteReader r) {
        ElfSection verdef = sectionByType(img, SHT_GNU_VERDEF);
        if (verdef != null) {
            parseVerdef(img, r, verdef);
        }
        ElfSection verneed = sectionByType(img, SHT_GNU_VERNEED);
        if (verneed != null) {
            parseVerneed(img, r, verneed);
        }
    }

    private static ElfSection sectionByType(ElfImage img, long type) {
        for (ElfSection s : img.sections) {
            if (s.type == type) {
                return s;
            }
        }
        return null;
    }

    private static ElfSection sectionByName(ElfImage img, String name) {
        for (ElfSection s : img.sections) {
            if (name.equals(s.name)) {
                return s;
            }
        }
        return null;
    }

    private static void parseVerdef(ElfImage img, ByteReader r, ElfSection sec) {
        ElfSection str = img.section(sec.link);
        if (str == null) {
            throw new ElfException(".gnu.version_d has no linked string table");
        }
        int end = (int) sec.offset + (int) sec.size;
        int cur = (int) sec.offset;
        int ordinal = 0;
        while (cur < end) {
            int vd_next = (int) r.u32(cur);
            int vd_flags = (int) r.u16(cur + 4);
            int vd_ndx = (int) r.u16(cur + 6);
            int vd_cnt = (int) r.u16(cur + 8);
            int vd_aux = (int) r.u32(cur + 12);
            VersionDef def = new VersionDef();
            def.index = vd_ndx;
            def.nonDefault = (vd_flags & 0x1) != 0; // VER_FLG_BASE
            int auxp = cur + vd_aux;
            for (int a = 0; a < vd_cnt; a++) {
                int vda_name = (int) r.u32(auxp);
                int vda_next = (int) r.u32(auxp + 4);
                String nm = safeString(r, str, vda_name);
                if (a == 0) {
                    def.name = nm;
                } else {
                    def.parentNames.add(nm);
                }
                if (vda_next == 0) {
                    break;
                }
                auxp += vda_next;
            }
            ordinal++;
            def.base = ordinal == 1;
            img.versionDefs.add(def);
            img.versionDefByIndex.put(def.index, def);
            if (vd_next == 0) {
                break;
            }
            cur += vd_next;
        }
    }

    private static void parseVerneed(ElfImage img, ByteReader r, ElfSection sec) {
        ElfSection str = img.section(sec.link);
        if (str == null) {
            throw new ElfException(".gnu.version_r has no linked string table");
        }
        int end = (int) sec.offset + (int) sec.size;
        int cur = (int) sec.offset;
        while (cur < end) {
            int vn_next = (int) r.u32(cur);
            int vn_cnt = (int) r.u16(cur + 8);
            int vn_file = (int) r.u32(cur + 12);
            int vn_aux = (int) r.u32(cur + 16);
            VersionNeed need = new VersionNeed();
            need.file = safeString(r, str, vn_file);
            int auxp = cur + vn_aux;
            for (int a = 0; a < vn_cnt; a++) {
                int vna_hash = (int) r.u32(auxp);
                int vna_flags = (int) r.u16(auxp + 4);
                int vna_other = (int) r.u16(auxp + 6);
                int vna_name = (int) r.u32(auxp + 8);
                int vna_next = (int) r.u32(auxp + 12);
                VersionNeed.Aux ax = new VersionNeed.Aux();
                ax.index = vna_other;
                ax.name = safeString(r, str, vna_name);
                ax.weak = (vna_flags & 0x2) != 0;
                ax.hidden = (vna_flags & 0x1) != 0;
                need.aux.add(ax);
                img.versionNeedByIndex.put(ax.index, ax);
                if (vna_next == 0) {
                    break;
                }
                auxp += vna_next;
            }
            img.versionNeeds.add(need);
            if (vn_next == 0) {
                break;
            }
            cur += vn_next;
        }
    }
