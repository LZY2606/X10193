package court.elf;

import java.util.List;

/**
 * Minimal, defensive ELF decoder for the symbol court. Interprets only the structures
 * the engine needs: header, section headers, symtab/dynsym, rel/rela, .dynamic and the
 * GNU version sections (.gnu.version[_r|_d]) plus section groups.
 *
 * Every read is length and index checked. A malformed object is flagged corrupt and
 * isolated by the caller; it never aborts loading of sibling objects.
 */
public final class ElfReader {
    private ElfReader() {}

    private static final byte[] MAGIC = {0x7f, 'E', 'L', 'F'};

    public static ElfImage read(byte[] data) {
        ElfImage img = new ElfImage();
        try {
            parse(data, img);
        } catch (Throwable t) {
            img.corrupt = true;
            img.error = t.getClass().getSimpleName() + ": " + t.getMessage();
            img.sections.clear();
            img.symbols.clear();
            img.dynSymbols.clear();
            img.relocations.clear();
            img.versionDefs.clear();
            img.versionNeeds.clear();
        }
        return img;
    }

    private static final class In {
        final byte[] d;
        final boolean le;
        int p;

        In(byte[] d, boolean le) { this.d = d; this.le = le; }

        void need(int n) {
            if (p < 0 || (long) p + n > d.length) {
                throw new IllegalArgumentException("read past end of file at offset " + p);
            }
        }

        int u8() { need(1); return d[p++] & 0xff; }

        int u16() {
            need(2);
            int a = d[p] & 0xff, b = d[p + 1] & 0xff;
            p += 2;
            return le ? (a | (b << 8)) : ((a << 8) | b);
        }

        long u32() {
            need(4);
            long v;
            if (le) {
                v = (d[p] & 0xffL) | ((d[p + 1] & 0xffL) << 8)
                        | ((d[p + 2] & 0xffL) << 16) | ((d[p + 3] & 0xffL) << 24);
            } else {
                v = ((d[p] & 0xffL) << 24) | ((d[p + 1] & 0xffL) << 16)
                        | ((d[p + 2] & 0xffL) << 8) | (d[p + 3] & 0xffL);
            }
            p += 4;
            return v;
        }

        long u64() {
            need(8);
            long v = 0;
            if (le) {
                for (int i = 7; i >= 0; i--) v = (v << 8) | (d[p + i] & 0xff);
            } else {
                for (int i = 0; i < 8; i++) v = (v << 8) | (d[p + i] & 0xff);
            }
            p += 8;
            return v;
        }

        String strtab(long base, long index) {
            if (base < 0 || base > d.length) throw new IllegalArgumentException("strtab base out of range");
            if (index < 0) throw new IllegalArgumentException("negative strtab index");
            long start = base + index;
            if (start > d.length) throw new IllegalArgumentException("strtab index out of range");
            int s = (int) start, e = s;
            while (e < d.length && d[e] != 0) e++;
            return new String(d, s, e - s, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void parse(byte[] data, ElfImage img) {
        if (data == null || data.length < 16) throw new IllegalArgumentException("file shorter than ELF ident");
        for (int i = 0; i < 4; i++) {
            if ((data[i] & 0xff) != (MAGIC[i] & 0xff)) throw new IllegalArgumentException("bad ELF magic");
        }
        img.elfClass = data[4];
        img.dataEncoding = data[5];
        if (img.elfClass != Const.ELFCLASS32 && img.elfClass != Const.ELFCLASS64) {
            throw new IllegalArgumentException("unsupported ELF class " + img.elfClass);
        }
        if (img.dataEncoding != Const.ELFDATA2LSB && img.dataEncoding != Const.ELFDATA2MSB) {
            throw new IllegalArgumentException("unsupported ELF data encoding");
        }
        img.is64 = img.elfClass == Const.ELFCLASS64;
        img.le = img.dataEncoding == Const.ELFDATA2LSB;
        In in = new In(data, img.le);

        in.p = 16;
        img.type = in.u16();
        img.machine = in.u16();
        in.u32();

        long shoff;
        int shnum, shstrndx;
        if (img.is64) {
            img.entry = in.u64();
            in.u64();
            shoff = in.u64();
            in.u32();
            in.u16(); in.u16(); in.u16();
            in.u16();
            shnum = in.u16();
            shstrndx = in.u16();
            readSections(in, img, shoff, shnum, shstrndx, 64);
        } else {
            img.entry = in.u32();
            in.u32();
            shoff = in.u32();
            in.u32();
            in.u16(); in.u16(); in.u16();
            in.u16();
            shnum = in.u16();
            shstrndx = in.u16();
            readSections(in, img, shoff, shnum, shstrndx, 40);
        }
        postProcess(in, img);
    }

    private static void readSections(In in, ElfImage img, long shoff, int shnumIn, int shstrndxIn, int entsize) {
        int shnum = shnumIn, shstrndx = shstrndxIn;
        if (shoff != 0) {
            if (shoff < 0 || shoff > in.d.length) throw new IllegalArgumentException("section offset out of range");
            if (shnumIn == 0) {
                // extended numbering: real count in section header 0 sh_size, strtab index in sh_link
                if (shoff + entsize > in.d.length) throw new IllegalArgumentException("section 0 header truncated");
                int linkPos = (int) shoff + (entsize == 64 ? 24 : 16);
                in.p = linkPos;
                int link = in.u16();
                in.p = (int) shoff + (entsize == 64 ? 32 : 20);
                long count = entsize == 64 ? in.u64() : in.u32();
                if (count <= 0 || count > 1_000_000) throw new IllegalArgumentException("insane section count");
                shnum = (int) count;
                shstrndx = shstrndxIn == Const.SHN_XINDEX ? link : shstrndxIn;
            }
            long end = shoff + (long) shnum * entsize;
            if (end > in.d.length) throw new IllegalArgumentException("section header table past end of file");
        }
        int[] nameOffs = new int[shnum];
        for (int i = 0; i < shnum; i++) {
            in.p = (int) (shoff + (long) i * entsize);
            ElfSection s = new ElfSection();
            s.index = i;
            int nameOff = (int) in.u32();
            nameOffs[i] = nameOff;
            s.type = (int) in.u32();
            if (entsize == 64) {
                s.flags = in.u64();   // Elf64: sh_flags starts at offset 8
                s.addr = in.u64();
                s.offset = in.u64();
                s.size = in.u64();
                s.link = (int) in.u32();
                s.info = (int) in.u32();
                s.addralign = in.u64();
                s.entsize = in.u64();
            } else {
                s.flags = in.u32();
                s.addr = in.u32();
                s.offset = in.u32();
                s.size = in.u32();
                s.link = (int) in.u32();
                s.info = (int) in.u32();
                s.addralign = in.u32();
                s.entsize = in.u32();
            }
            img.sections.add(s);
        }
        ElfSection shstr = img.sectionByIndex(shstrndx);
        for (int i = 0; i < img.sections.size(); i++) {
            ElfSection s = img.sections.get(i);
            if (shstr != null && s.type != Const.SHT_NULL) {
                s.name = in.strtab(shstr.offset, nameOffs[i]);
            } else if (s.type == Const.SHT_NULL) {
                s.name = "";
            } else {
                s.name = "sh" + i;
            }
            if (s.type != Const.SHT_NOBITS && s.type != Const.SHT_NULL) {
                if (s.offset > in.d.length) throw new IllegalArgumentException("section " + i + " offset out of range");
                if ((long) s.offset + s.size > in.d.length)
                    throw new IllegalArgumentException("section " + i + " extends past end of file");
                if (s.link != 0 && s.link >= img.sections.size())
                    throw new IllegalArgumentException("section " + i + " sh_link out of range: " + s.link);
            }
            if (s.size < 0) throw new IllegalArgumentException("section " + i + " negative size");
        }
    }

    private static void postProcess(In in, ElfImage img) {
        readGroups(in, img);
        readSymbols(in, img);
        readDynamic(in, img);
        readVersions(in, img);
        readRelocations(in, img);
    }

    // ---- groups -----------------------------------------------------------

    private static void readGroups(In in, ElfImage img) {
        for (ElfSection s : img.sections) {
            if (s.type != Const.SHT_GROUP) continue;
            in.p = (int) s.offset;
            if (s.size < 4) throw new IllegalArgumentException("empty group section");
            s.groupFlag = (int) in.u32();
            int n = (int) ((s.size - 4) / 4);
            java.util.List<Integer> members = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = (int) in.u32();
                if (idx < 0 || idx >= img.sections.size())
                    throw new IllegalArgumentException("group member section index out of range");
                members.add(idx);
            }
            s.groupMembers = members;
        }
    }

    // ---- symbols ----------------------------------------------------------

    private static void readSymbols(In in, ElfImage img) {
        for (ElfSection s : img.sections) {
            if (s.type != Const.SHT_SYMTAB && s.type != Const.SHT_DYNSYM) continue;
            ElfSection str = img.sectionByIndex(s.link);
            if (str == null) throw new IllegalArgumentException(s.name + " has no linked string table");
            int ent = (int) s.entsize;
            if (ent <= 0) ent = img.is64 ? 24 : 16;
            long count = s.size / ent;
            if (count > 10_000_000) throw new IllegalArgumentException("insane symbol count");
            for (long i = 0; i < count; i++) {
                in.p = (int) (s.offset + i * ent);
                long nameOff = in.u32();
                ElfSymbol sym = img.is64 ? readSym64(in) : readSym32(in);
                sym.index = (int) i;
                sym.dynamic = s.type == Const.SHT_DYNSYM;
                sym.name = in.strtab(str.offset, nameOff);
                if (s.type == Const.SHT_SYMTAB) img.symbols.add(sym);
                else img.dynSymbols.add(sym);
            }
        }
    }

    private static ElfSymbol readSym64(In in) {
        ElfSymbol s = new ElfSymbol();
        s.info = in.u8();
        s.other = in.u8();
        s.shndx = in.u16();
        s.value = in.u64();
        s.size = in.u64();
        return s;
    }

    private static ElfSymbol readSym32(In in) {
        ElfSymbol s = new ElfSymbol();
        s.value = in.u32();
        s.size = in.u32();
        s.info = in.u8();
        s.other = in.u8();
        s.shndx = in.u16();
        return s;
    }

    // ---- dynamic ----------------------------------------------------------

    private static void readDynamic(In in, ElfImage img) {
        ElfSection dyn = img.sectionByName(".dynamic");
        if (dyn == null) return;
        ElfSection dynstr = img.sectionByName(".dynstr");
        long stride = img.is64 ? 16 : 8;
        long n = dyn.size / stride;
        for (long i = 0; i < n; i++) {
            in.p = (int) (dyn.offset + i * stride);
            long tag, val;
            if (img.is64) { tag = in.u64(); val = in.u64(); }
            else { tag = in.u32(); val = in.u32(); }
            if (tag == Const.DT_NULL) break;
            if (tag == Const.DT_NEEDED && dynstr != null) img.needed.add(in.strtab(dynstr.offset, val));
            else if (tag == Const.DT_SONAME && dynstr != null) img.soname = in.strtab(dynstr.offset, val);
        }
    }

    // ---- version sections -------------------------------------------------

    private static void readVersions(In in, ElfImage img) {
        ElfSection versym = img.sectionByName(".gnu.version");
        ElfSection verdef = img.sectionByName(".gnu.version_d");
        ElfSection verneed = img.sectionByName(".gnu.version_r");
        ElfSection dynsym = img.sectionByName(".dynsym");

        if (verdef != null) readVerdef(in, img, verdef);
        if (verneed != null) readVerneed(in, img, verneed);

        if (versym != null && dynsym != null) {
            long n = versym.size / 2;
            long symCount = img.dynSymbols.size();
            if (n > symCount + 1) throw new IllegalArgumentException(".gnu.version longer than .dynsym");
            for (long i = 0; i < n && i < symCount; i++) {
                in.p = (int) (versym.offset + i * 2);
                int idx = in.u16();
                img.dynSymbols.get((int) i).versionIndex = idx;
            }
        }
    }

    private static void readVerdef(In in, ElfImage img, ElfSection sec) {
        ElfSection str = img.sectionByIndex(sec.link);
        if (str == null) throw new IllegalArgumentException(".gnu.version_d has no linked strings");
        long off = sec.offset;
        long end = sec.offset + sec.size;
        int fallbackIndex = 2;
        while (off + 20 <= end) {
            in.p = (int) off;
            in.u16();                       // vd_version
            int flags = in.u16();           // vd_flags
            int vdndx = in.u16();           // vd_ndx
            int cnt = in.u16();             // vd_cnt
            in.u32();                       // vd_hash
            long aux = in.u32();
            long next = in.u32();
            VersionDef vd = new VersionDef();
            vd.flags = flags;
            vd.index = vdndx > 1 ? vdndx : fallbackIndex;
            java.util.List<String> names = new java.util.ArrayList<>();
            long aoff = off + aux;
            for (int k = 0; k < cnt; k++) {
                if (aoff + 8 > end) throw new IllegalArgumentException("truncated Verdaux");
                in.p = (int) aoff;
                long nameIdx = in.u32();
                long aNext = in.u32();
                names.add(in.strtab(str.offset, nameIdx));
                if (aNext == 0) break;
                aoff += aNext;
            }
            if (names.isEmpty()) throw new IllegalArgumentException("Verdef with no version name");
            vd.name = names.get(0);
            vd.aliases = names.size() > 1 ? names.subList(1, names.size()) : java.util.List.of();
            img.versionDefs.add(vd);
            fallbackIndex++;
            if (next == 0) break;
            off += next;
        }
    }

    private static void readVerneed(In in, ElfImage img, ElfSection sec) {
        ElfSection str = img.sectionByIndex(sec.link);
        if (str == null) throw new IllegalArgumentException(".gnu.version_r has no linked strings");
        long off = sec.offset;
        long end = sec.offset + sec.size;
        while (off + 16 <= end) {
            in.p = (int) off;
            in.u16(); // vn_version
            in.u16(); // vn_cnt
            long fileIdx = in.u32();
            in.u32(); // vn_aux
            long next = in.u32();
            VersionNeed vn = new VersionNeed();
            vn.file = in.strtab(str.offset, fileIdx);

            // aux records follow; compute from vn_aux relative offset
            in.p = (int) off + 8;
            long auxRel = in.u32();
            long aoff = off + auxRel;
            int cnt = 0;
            in.p = (int) off + 2;
            cnt = in.u16();
            for (int k = 0; k < cnt; k++) {
                if (aoff + 16 > end) throw new IllegalArgumentException("truncated Vernaux");
                in.p = (int) aoff;
                long hash = in.u32();
                int flags = in.u16();
                int other = in.u16();
                long nameIdx = in.u32();
                long aNext = in.u32();
                VersionNeed.Entry e = new VersionNeed.Entry();
                e.flags = flags;
                e.index = other;
                e.name = in.strtab(str.offset, nameIdx);
                e.weak = (flags & Const.VER_FLG_WEAK) != 0;
                vn.entries.add(e);
                if (aNext == 0) break;
                aoff += aNext;
            }
            img.versionNeeds.add(vn);
            if (next == 0) break;
            off += next;
        }
    }

    // ---- relocations ------------------------------------------------------

    private static void readRelocations(In in, ElfImage img) {
        RelTypes.Policy policy = RelTypes.policy(img.machine);
        for (ElfSection s : img.sections) {
            if (s.type != Const.SHT_REL && s.type != Const.SHT_RELA) continue;
            ElfSection symtab = img.sectionByIndex(s.link);
            List<ElfSymbol> syms;
            if (symtab != null && symtab.type == Const.SHT_DYNSYM) syms = img.dynSymbols;
            else if (symtab != null && symtab.type == Const.SHT_SYMTAB) syms = img.symbols;
            else continue;
            ElfSection target = img.sectionByIndex(s.info);
            boolean writeTarget = target != null && (target.flags & Const.SHF_WRITE) != 0;
            boolean rela = s.type == Const.SHT_RELA;
            int ent = img.is64 ? (rela ? 24 : 16) : (rela ? 12 : 8);
            long n = s.size / ent;
            for (long i = 0; i < n; i++) {
                in.p = (int) (s.offset + i * ent);
                ElfRelocation r = new ElfRelocation();
                r.index = (int) i;
                r.rela = rela;
                r.sectionName = s.name;
                r.writeTarget = writeTarget;
                long info;
                if (img.is64) {
                    r.offset = in.u64();
                    info = in.u64();
                    long sym = info >>> 32;
                    long type = info & 0xffffffffL;
                    r.symbolIndex = (int) sym;
                    r.type = type;
                    if (rela) r.addend = in.u64();
                } else {
                    r.offset = in.u32();
                    info = in.u32();
                    r.symbolIndex = (int) (info >>> 8);
                    r.type = info & 0xff;
                    if (rela) r.addend = (int) in.u32();
                }
                r.computedType = RelTypes.name(img.machine, r.type);
                if (r.symbolIndex < 0 || r.symbolIndex >= syms.size()) {
                    throw new IllegalArgumentException("relocation " + i + " symbol index " + r.symbolIndex
                            + " out of range for " + (symtab == null ? "?" : symtab.name));
                }
                img.relocations.add(r);
            }
        }
    }
}
