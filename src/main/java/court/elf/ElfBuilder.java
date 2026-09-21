package court.elf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Little-endian ELF64 builder for fixtures and the built-in demo. Emits section
 * headers, a string table, symtab (REL) / dynsym+dynstr (DYN), RELA relocations,
 * .dynamic and the GNU version sections, plus COMDAT groups. Lets tests exercise
 * linker semantics with no toolchain present.
 */
public class ElfBuilder {

    public static final class Sym {
        final String name;
        long value, size;
        int binding, type, visibility = Const.STV_DEFAULT, shndx;
        String version = ""; // version name for dynsym; "" means unversioned/default
        Sym(String name, int binding, int type, int shndx, long value, long size) {
            this.name = name; this.binding = binding; this.type = type;
            this.shndx = shndx; this.value = value; this.size = size;
        }
    }

    public static final class Rel {
        final long offset, type, addend; final int sym; final String target;
        Rel(long offset, long type, int sym, long addend, String target) {
            this.offset = offset; this.type = type; this.sym = sym; this.addend = addend; this.target = target;
        }
    }

    private static final class VerDefSpec { final String name; final List<String> aliases;
        VerDefSpec(String n, List<String> a) { name = n; aliases = a; } }
    private static final class VerNeedSpec { final String file, ver; final boolean weak;
        VerNeedSpec(String f, String v, boolean w) { file = f; ver = v; weak = w; } }

    private int etType = Const.ET_REL;
    private int machine = RelTypes.EM_X86_64;
    private final List<Sym> syms = new ArrayList<>();
    private final List<Sym> dyn = new ArrayList<>();
    private final List<Rel> relas = new ArrayList<>();
    private final List<String> needed = new ArrayList<>();
    private String soname;
    private final List<VerDefSpec> verDefs = new ArrayList<>();
    private final List<VerNeedSpec> verNeeds = new ArrayList<>();
    private String comdatSignature;

    public static ElfBuilder rel() { ElfBuilder b = new ElfBuilder(); b.etType = Const.ET_REL; return b; }
    public static ElfBuilder dyn() { ElfBuilder b = new ElfBuilder(); b.etType = Const.ET_DYN; return b; }
    public ElfBuilder machine(int m) { this.machine = m; return this; }
    public ElfBuilder needed(String n) { needed.add(n); return this; }
    public ElfBuilder soname(String n) { soname = n; return this; }

    public ElfBuilder def(String name, int binding, int type, int shndx) {
        syms.add(new Sym(name, binding, type, shndx, 0x1000 + syms.size() * 16,
                type == Const.STT_OBJECT ? 8 : 16));
        return this;
    }
    public ElfBuilder common(String name, long size) {
        syms.add(new Sym(name, Const.STB_GLOBAL, Const.STT_OBJECT, Const.SHN_COMMON, 0, size));
        return this;
    }
    public ElfBuilder undef(String name, int binding) {
        syms.add(new Sym(name, binding, Const.STT_NOTYPE, Const.SHN_UNDEF, 0, 0));
        return this;
    }
    public ElfBuilder visibility(int v) { syms.get(syms.size() - 1).visibility = v; return this; }

    /** Set visibility of a previously added .symtab symbol by name. */
    public ElfBuilder visibilityOf(String name, int v) {
        syms.stream().filter(x -> x.name.equals(name)).findFirst().orElseThrow().visibility = v;
        return this;
    }

    public ElfBuilder dynDef(String name, int binding, int type, String version, int vis) {
        Sym s = new Sym(name, binding, type, 1, 0x2000 + dyn.size() * 16,
                type == Const.STT_OBJECT ? 8 : 16);
        s.visibility = vis;
        s.version = version == null ? "" : version;
        dyn.add(s);
        return this;
    }
    public ElfBuilder dynUndef(String name, int binding, String requiredVersion) {
        Sym s = new Sym(name, binding, Const.STT_NOTYPE, Const.SHN_UNDEF, 0, 0);
        s.version = requiredVersion == null ? "" : requiredVersion;
        dyn.add(s);
        return this;
    }
    public ElfBuilder verDef(String name, List<String> aliases) {
        verDefs.add(new VerDefSpec(name, aliases == null ? List.of() : aliases));
        return this;
    }
    public ElfBuilder verNeed(String file, String version, boolean weak) {
        verNeeds.add(new VerNeedSpec(file, version, weak));
        return this;
    }
    public ElfBuilder rela(long offset, long type, int sym, long addend, String target) {
        relas.add(new Rel(offset, type, sym, addend, target));
        return this;
    }
    public ElfBuilder comdat(String sig) { this.comdatSignature = sig; return this; }

    /** 1-based index in the .symtab (leading null entry at 0). */
    public int symIndex(String name) {
        for (int i = 0; i < syms.size(); i++) if (syms.get(i).name.equals(name)) return i + 1;
        throw new IllegalArgumentException("no sym " + name);
    }

    // ---- section layout ----------------------------------------------------
    // 0 null
    // REL: .text .data .bss [.group] .symtab .strtab [.rela.*]
    // DYN: .dynsym .dynstr .dynamic [.gnu.version .gnu.version_d .gnu.version_r] .text .data
    private static final String SEC_NULL = "";
    private static final String SEC_TEXT = ".text";
    private static final String SEC_DATA = ".data";
    private static final String SEC_BSS = ".bss";
    private static final String SEC_SYMTAB = ".symtab";
    private static final String SEC_STRTAB = ".strtab";
    private static final String SEC_DYNSYM = ".dynsym";
    private static final String SEC_DYNSTR = ".dynstr";
    private static final String SEC_DYNAMIC = ".dynamic";
    private static final String SEC_VERSYM = ".gnu.version";
    private static final String SEC_VERDEF = ".gnu.version_d";
    private static final String SEC_VERNEED = ".gnu.version_r";
    private static final String SEC_GROUP = ".group";

    public byte[] build() {
        boolean isDyn = etType == Const.ET_DYN;
        List<String> order = new ArrayList<>(List.of(SEC_NULL));
        if (isDyn) {
            order.addAll(List.of(SEC_TEXT, SEC_DATA, SEC_BSS));
            order.add(SEC_DYNSYM);
            order.add(SEC_DYNSTR);
            order.add(SEC_DYNAMIC);
            if (!verDefs.isEmpty()) order.add(SEC_VERDEF);
            if (!verNeeds.isEmpty()) order.add(SEC_VERNEED);
            if (!dyn.isEmpty()) order.add(SEC_VERSYM);
            for (Rel r : relas) {
                String rn = ".rela" + r.target;
                if (!order.contains(rn)) order.add(rn);
            }
        } else {
            order.addAll(List.of(SEC_TEXT, SEC_DATA, SEC_BSS));
            if (comdatSignature != null) order.add(SEC_GROUP);
            order.add(SEC_SYMTAB);
            order.add(SEC_STRTAB);
            for (Rel r : relas) {
                String rn = ".rela" + r.target;
                if (!order.contains(rn)) order.add(rn);
            }
        }
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) idx.put(order.get(i), i);

        // string tables
        Bytes shstr = new Bytes();
        int[] shNameOff = new int[order.size()];
        shstr.byte0();
        for (int i = 1; i < order.size(); i++) shNameOff[i] = shstr.cstr(order.get(i));

        Bytes strtab = new Bytes(); strtab.byte0();
        Map<String, Integer> strOff = new LinkedHashMap<>();
        for (Sym s : syms) if (!strOff.containsKey(s.name)) strOff.put(s.name, strtab.cstr(s.name));

        Bytes dynstr = new Bytes(); dynstr.byte0();
        Map<String, Integer> dynStrOff = new LinkedHashMap<>();
        for (Sym s : dyn) if (!dynStrOff.containsKey(s.name)) dynStrOff.put(s.name, dynstr.cstr(s.name));
        Map<String, Integer> neededOff = new LinkedHashMap<>();
        for (String n : needed) neededOff.put(n, dynstr.cstr(n));
        Integer sonameOff = soname == null ? null : dynstr.cstr(soname);
        int comdatNameOff = -1;
        if (comdatSignature != null) comdatNameOff = strtab.cstr(comdatSignature);

        // version index maps
        Map<String, Integer> defIndexByName = new LinkedHashMap<>();
        int nextDef = 2;
        for (VerDefSpec d : verDefs) {
            defIndexByName.put(d.name, nextDef);
            // aliases share the same index
            nextDef++;
        }
        Map<String, Integer> needIndexByName = new LinkedHashMap<>();
        int nextNeed = 2 + verDefs.size();
        for (VerNeedSpec n : verNeeds) if (!needIndexByName.containsKey(n.ver))
            needIndexByName.put(n.ver, nextNeed++);

        // version names live in .dynstr and must exist before encoding the version blobs
        for (VerDefSpec d : verDefs) {
            if (!dynStrOff.containsKey(d.name)) dynStrOff.put(d.name, dynstr.cstr(d.name));
            for (String a : d.aliases)
                if (!dynStrOff.containsKey(a)) dynStrOff.put(a, dynstr.cstr(a));
        }
        for (VerNeedSpec n : verNeeds) {
            if (!dynStrOff.containsKey(n.file)) dynStrOff.put(n.file, dynstr.cstr(n.file));
            if (!dynStrOff.containsKey(n.ver)) dynStrOff.put(n.ver, dynstr.cstr(n.ver));
        }
        Bytes verdefBlob = new Bytes();
        buildVerdef(verdefBlob, dynstr, defIndexByName, dynStrOff);
        Bytes verneedBlob = new Bytes();
        buildVerneed(verneedBlob, dynstr, needIndexByName, dynStrOff);

        // section content blobs
        Map<String, Bytes> content = new LinkedHashMap<>();
        content.put(SEC_TEXT, stubBytes(0x20));
        content.put(SEC_DATA, stubBytes(0x10));
        content.put(SEC_STRTAB, strtab);
        content.put(SEC_DYNSTR, dynstr);
        content.put(SEC_VERDEF, verdefBlob);
        content.put(SEC_VERNEED, verneedBlob);

        // symtab; COMDAT signature symbol appended last so user symbol indices stay stable
        Bytes symtabBlob = new Bytes();
        writeSym(symtabBlob, 0, 0, 0, 0, 0, Const.SHN_UNDEF);
        for (Sym s : syms) {
            int info = (s.binding << 4) | (s.type & 0xf);
            writeSym(symtabBlob, strOff.getOrDefault(s.name, 0), s.value, s.size, info, s.visibility, s.shndx);
        }
        int signatureSymIndex = 0;
        if (comdatSignature != null) {
            signatureSymIndex = syms.size() + 1;
            writeSym(symtabBlob, comdatNameOff, 0, 0, Const.STB_LOCAL << 4,
                    Const.STV_DEFAULT, Const.SHN_UNDEF);
        }
        content.put(SEC_SYMTAB, symtabBlob);

        // dynsym
        Bytes dynsymBlob = new Bytes();
        writeSym(dynsymBlob, 0, 0, 0, 0, 0, Const.SHN_UNDEF);
        for (Sym s : dyn) {
            int info = (s.binding << 4) | (s.type & 0xf);
            writeSym(dynsymBlob, dynStrOff.getOrDefault(s.name, 0), s.value, s.size, info, s.visibility, s.shndx);
        }
        content.put(SEC_DYNSYM, dynsymBlob);

        // versym (one u16 per dynsym incl. null)
        Bytes versym = new Bytes();
        versym.u16(0);
        for (Sym s : dyn) {
            int vi;
            if (s.shndx == Const.SHN_UNDEF) vi = s.version.isEmpty() ? Const.VER_NDX_GLOBAL
                    : needIndexByName.getOrDefault(s.version, 0);
            else vi = s.version.isEmpty() ? Const.VER_NDX_GLOBAL
                    : defIndexByName.getOrDefault(s.version, Const.VER_NDX_GLOBAL);
            versym.u16(vi);
        }
        content.put(SEC_VERSYM, versym);

        // relas
        for (Rel r : relas) {
            Bytes rb = new Bytes();
            long info = ((long) r.sym << 32) | (r.type & 0xffffffffL);
            rb.u64(r.offset); rb.u64(info); rb.u64(r.addend);
            content.put(".rela" + r.target, rb);
        }

        // group
        if (comdatSignature != null) {
            Bytes g = new Bytes();
            g.u32(Const.GRP_COMDAT);
            g.u32(idx.get(SEC_SYMTAB)); // sh_link: symbol table
            // members: .text and .data
            g.u32(idx.get(SEC_TEXT));
            g.u32(idx.get(SEC_DATA));
            content.put(SEC_GROUP, g);
        }

        // .dynamic
        Bytes dynamic = new Bytes();
        for (String n : needed) { dynamic.u64(Const.DT_NEEDED); dynamic.u64(neededOff.get(n)); }
        if (sonameOff != null) { dynamic.u64(Const.DT_SONAME); dynamic.u64(sonameOff); }
        if (verdefBlob.size() > 0) {
            dynamic.u64(Const.DT_VERDEF); dynamic.u64(0); // offsets patched after layout
            dynamic.u64(Const.DT_VERDEFNUM); dynamic.u64(verDefs.size());
        }
        if (verneedBlob.size() > 0) {
            dynamic.u64(Const.DT_VERNEED); dynamic.u64(0);
            dynamic.u64(Const.DT_VERNEEDNUM); dynamic.u64(countNeedFiles());
        }
        if (versym.size() > 0) { dynamic.u64(Const.DT_VERSYM); dynamic.u64(0); }
        dynamic.u64(Const.DT_NULL); dynamic.u64(0);
        content.put(SEC_DYNAMIC, dynamic);

        return assemble(order, idx, content, shstr, shNameOff, dynamic,
                verdefBlob, verneedBlob, versym);
    }


    private byte[] assemble(List<String> order, Map<String, Integer> idx, Map<String, Bytes> content,
                            Bytes shstr, int[] shNameOff, Bytes dynamic,
                            Bytes verdefBlob, Bytes verneedBlob, Bytes versym) {
        int comdatNameOff = comdatSignature == null ? -1 : 1;
        boolean isDyn = etType == Const.ET_DYN;
        int shnum = order.size() + 1; // + trailing .shstrtab
        int shstrIndex = order.size();

        // Phase 1: file content (sections). body uses absolute file offsets, so reserve the header.
        Bytes body = new Bytes();
        body.resize(64);
        long cur = 64;
        long[] offsets = new long[shnum];
        long[] sizes = new long[shnum];
        for (int i = 1; i < order.size(); i++) {
            String name = order.get(i);
            if (name.equals(SEC_BSS)) {
                offsets[i] = cur;
                sizes[i] = 0x10;
                continue;
            }
            while (cur % 8 != 0) { body.u8(0); cur++; }
            offsets[i] = cur;
            Bytes b = content.getOrDefault(name, new Bytes());
            sizes[i] = b.size();
            body.bytes(b.toArray());
            cur += b.size();
        }

        // Phase 2: patch .dynamic value fields with final offsets.
        if (isDyn) {
            patchDynamic(dynamic, offsets, idx, verdefBlob.size() > 0,
                    verneedBlob.size() > 0, versym.size() > 0);
        }

        // Phase 3: reserve the section header table region in the body.
        while (cur % 8 != 0) { body.u8(0); cur++; }
        long shoff = cur;
        for (int k = 0; k < shnum * 64; k++) body.u8(0);
        long shdrEnd = shoff + (long) shnum * 64;
        cur = shdrEnd;

        // Phase 4: trailing .shstrtab data after the headers.
        while (cur % 8 != 0) { body.u8(0); cur++; }
        long shstrOff = cur;
        body.bytes(shstr.toArray());
        cur += shstr.size();
        sizes[shstrIndex] = shstr.size();
        offsets[shstrIndex] = shstrOff;

        // ELF header sits in the reserved first 64 bytes of body.
        Bytes out = body;
        byte[] ident = {0x7f,'E','L','F', Const.ELFCLASS64, Const.ELFDATA2LSB, 1, 0};
        for (int k = 0; k < ident.length; k++) out.a[k] = ident[k];
        int h = 16;
        putU16(out.a, h, etType); h += 2;
        putU16(out.a, h, machine); h += 2;
        putU32(out.a, h, 1); h += 4;
        putU64(out.a, h, 0); h += 8;
        putU64(out.a, h, 0); h += 8;
        putU64(out.a, h, shoff); h += 8;
        putU32(out.a, h, 0); h += 4;
        putU16(out.a, h, 64); h += 2;
        putU16(out.a, h, 0); h += 2;
        putU16(out.a, h, 0); h += 2;
        putU16(out.a, h, 64); h += 2;
        putU16(out.a, h, shnum); h += 2;
        putU16(out.a, h, shstrIndex); h += 2;

        // section headers (written at their absolute position)
        long pos = out.size();
        for (int i = 0; i < order.size(); i++) writeShdrAt(out, shoff + i * 64L,
                i == 0 ? 0 : shNameOff[i],
                sectionType(order.get(i)), sectionFlags(order.get(i)), 0, offsets[i], sizes[i],
                sectionLink(order.get(i), idx, shnum),
                sectionInfo(order.get(i), idx, comdatNameOff), order.get(i));
        writeShdrAt(out, shoff + (long) shstrIndex * 64, 0, Const.SHT_STRTAB, 0, 0,
                shstrOff, shstr.size(), 0, 0, "");
        return out.toArray();
    }


    private static void putU16(byte[] a, int p, int v) { a[p]=(byte)v; a[p+1]=(byte)(v>>>8); }
    private static void putU32(byte[] a, int p, long v) {
        for (int i=0;i<4;i++) a[p+i]=(byte)(v>>>(8*i));
    }
    private static void putU64(byte[] a, int p, long v) {
        for (int i=0;i<8;i++) a[p+i]=(byte)(v>>>(8*i));
    }

    private void writeShdrAt(Bytes out, long at, int name, int type, long flags, long addr,
                             long off, long size, int link, int info, String groupFlag) {
        long end = at + 64;
        if (end > out.size()) out.resize((int) end);
        int saved = out.size();
        out.seek((int) at);
        writeShdr(out, name, type, flags, addr, off, size, link, info, groupFlag);
        out.seek(saved);
    }

    private void patchDynamic(Bytes dynamic, long[] offsets, Map<String, Integer> idx,
                              boolean hasDef, boolean hasNeed, boolean hasVersym) {
        int p = 0;
        while (p + 16 <= dynamic.size()) {
            long tag = dynamic.getU64(p);
            if (tag == Const.DT_NULL) break;
            if (hasDef && tag == Const.DT_VERDEF) dynamic.setU64(p + 8, offsets[idx.get(SEC_VERDEF)]);
            else if (hasNeed && tag == Const.DT_VERNEED) dynamic.setU64(p + 8, offsets[idx.get(SEC_VERNEED)]);
            else if (hasVersym && tag == Const.DT_VERSYM) dynamic.setU64(p + 8, offsets[idx.get(SEC_VERSYM)]);
            p += 16;
        }
    }

    private int sectionType(String n) {
        switch (n) {
            case SEC_TEXT: case SEC_DATA: return Const.SHT_PROGBITS;
            case SEC_BSS: return Const.SHT_NOBITS;
            case SEC_SYMTAB: case SEC_DYNSYM:
                return n.equals(SEC_DYNSYM) ? Const.SHT_DYNSYM : Const.SHT_SYMTAB;
            case SEC_STRTAB: case SEC_DYNSTR: return Const.SHT_STRTAB;
            case SEC_DYNAMIC: return Const.SHT_DYNAMIC;
            case SEC_VERSYM: return 0x6fffffff;
            case SEC_VERDEF: return 0x6ffffffd;
            case SEC_VERNEED: return 0x6ffffffe;
            case SEC_GROUP: return Const.SHT_GROUP;
            default:
                if (n.startsWith(".rela")) return Const.SHT_RELA;
                return Const.SHT_PROGBITS;
        }
    }

    private long sectionFlags(String n) {
        switch (n) {
            case SEC_TEXT: return Const.SHF_ALLOC | Const.SHF_EXECINSTR;
            case SEC_DATA: case SEC_BSS:
                return Const.SHF_ALLOC | Const.SHF_WRITE;
            case SEC_DYNSYM: case SEC_DYNSTR: case SEC_DYNAMIC:
            case SEC_VERSYM: case SEC_VERDEF: case SEC_VERNEED:
                return Const.SHF_ALLOC;
            case SEC_GROUP: return 0;
            default:
                if (n.startsWith(".rela")) return 0;
                return 0;
        }
    }

    private int sectionLink(String n, Map<String, Integer> idx, int sectionCount) {
        switch (n) {
            case SEC_SYMTAB: return idx.get(SEC_STRTAB);
            case SEC_DYNSYM: return idx.get(SEC_DYNSTR);
            case SEC_DYNAMIC: return idx.get(SEC_DYNSTR);
            case SEC_VERDEF: return idx.get(SEC_DYNSTR);
            case SEC_VERNEED: return idx.get(SEC_DYNSTR);
            case SEC_GROUP: return idx.get(SEC_SYMTAB);
            default:
                if (n.startsWith(".rela")) return idx.get(etType == Const.ET_DYN ? SEC_DYNSYM : SEC_SYMTAB);
                return 0;
        }
    }

    private int sectionInfo(String n, Map<String, Integer> idx, int comdatNameOff) {
        if (n.equals(SEC_SYMTAB) || n.equals(SEC_DYNSYM)) return 1;
        if (n.startsWith(".rela")) return idx.get(n.substring(5));
        if (n.equals(SEC_GROUP)) return comdatNameOff < 0 ? 0 : (syms.size() + 1);
        return 0;
    }

    private void writeShdr(Bytes out, int name, int type, long flags, long addr, long off,
                           long size, int link, int info, String nameForGroup) {
        out.u32(name);
        out.u32(type);
        out.u64(flags);
        out.u64(addr);
        out.u64(off);
        out.u64(size);
        out.u32(link);
        if (nameForGroup.equals(SEC_GROUP)) out.u32(1); // signature is symtab index 1 by convention
        else out.u32(info);
        out.u64(8);
        out.u64(type == Const.SHT_RELA ? 24 : (type == Const.SHT_SYMTAB || type == Const.SHT_DYNSYM) ? 24
                : (type == 0x6fffffff ? 2 : 0));
    }

    private int countNeedFiles() {
        return (int) verNeeds.stream().map(n -> n.file).distinct().count();
    }

    private Bytes stubBytes(int n) {
        Bytes b = new Bytes();
        for (int i = 0; i < n; i++) b.u8(0);
        return b;
    }

    private void writeSym(Bytes b, int name, long value, long size, int info, int vis, int shndx) {
        b.u32(name);
        b.u8(info);
        b.u8(vis & 0x3);
        b.u16(shndx);
        b.u64(value);
        b.u64(size);
    }

    private void buildVerdef(Bytes b, Bytes dynstr, Map<String, Integer> defIndex,
                             Map<String, Integer> strOff) {
        for (int i = 0; i < verDefs.size(); i++) {
            VerDefSpec d = verDefs.get(i);
            int cnt = 1 + d.aliases.size();
            int auxSize = cnt * 8;
            int nextSize = (i + 1 < verDefs.size()) ? 20 + auxSize : 0;
            // Elf64_Verdef (20 bytes)
            b.u16(1);                          // vd_version
            b.u16(0);                          // vd_flags
            b.u16(defIndex.get(d.name));       // vd_ndx
            b.u16(cnt);                        // vd_cnt
            b.u32(0);                          // vd_hash
            b.u32(20);                         // vd_aux (relative to this Verdef)
            b.u32(nextSize);                   // vd_next
            // aux records: vna_name, vna_next
            for (int k = 0; k < cnt; k++) {
                String nm = k == 0 ? d.name : d.aliases.get(k - 1);
                b.u32(strOff.get(nm));
                b.u32(k + 1 < cnt ? 8 : 0);
            }
        }
    }

    private void buildVerneed(Bytes b, Bytes dynstr, Map<String, Integer> needIndex,
                              Map<String, Integer> strOff) {
        // group needs by file in encounter order
        Map<String, List<VerNeedSpec>> byFile = new LinkedHashMap<>();
        for (VerNeedSpec n : verNeeds) byFile.computeIfAbsent(n.file, k -> new ArrayList<>()).add(n);
        for (Map.Entry<String, List<VerNeedSpec>> e : byFile.entrySet()) {
            List<VerNeedSpec> list = e.getValue();
            int start = b.size();
            b.u16(1);                 // vn_version
            b.u16(list.size());       // vn_cnt
            b.u32(strOff.get(e.getKey())); // vn_file
            b.u32(16);                // vn_aux
            b.u32(0);                 // vn_next (single file in fixtures)
            for (int k = 0; k < list.size(); k++) {
                VerNeedSpec n = list.get(k);
                b.u32(0);                       // vna_hash
                b.u16(n.weak ? Const.VER_FLG_WEAK : 0);
                b.u16(needIndex.get(n.ver));    // vna_other
                b.u32(strOff.get(n.ver));       // vna_name
                b.u32(k + 1 < list.size() ? 16 : 0); // vna_next
            }
        }
    }

    private static long align(long v, int a) { return (v + a - 1) & ~(long) (a - 1); }

/** Growable little-endian byte buffer with patching support. */
static final class Bytes {
    private byte[] a = new byte[64];
    private int len;

    private void ensure(int n) {
        if (len + n > a.length) {
            int cap = a.length;
            while (cap < len + n) cap <<= 1;
            a = java.util.Arrays.copyOf(a, cap);
        }
    }
    void u8(int v) { ensure(1); a[len++] = (byte) v; }
    void u16(int v) { ensure(2); a[len++] = (byte) v; a[len++] = (byte) (v >>> 8); }
    void u32(long v) {
        ensure(4);
        a[len++] = (byte) v; a[len++] = (byte) (v >>> 8);
        a[len++] = (byte) (v >>> 16); a[len++] = (byte) (v >>> 24);
    }
    void u64(long v) {
        ensure(8);
        for (int i = 0; i < 8; i++) a[len++] = (byte) (v >>> (8 * i));
    }
    void bytes(byte[] bs) { ensure(bs.length); System.arraycopy(bs, 0, a, len, bs.length); len += bs.length; }
    void byte0() { u8(0); }
    int cstr(String s) {
        int off = len;
        bytes(s.getBytes(StandardCharsets.UTF_8));
        u8(0);
        return off;
    }
    int size() { return len; }
    byte[] toArray() { return java.util.Arrays.copyOf(a, len); }
    long getU64(int p) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (a[p + i] & 0xff);
        return v;
    }
    void setU64(int p, long v) { for (int i = 0; i < 8; i++) a[p + i] = (byte) (v >>> (8 * i)); }
    void resize(int n) { ensure(Math.max(0, n - len)); while (len < n) u8(0); }
    void padTo(int p) { while (len < p) u8(0); }
    void seek(int p) { this.len = p; }
    void setU16(int p, int v) { a[p] = (byte) v; a[p + 1] = (byte) (v >>> 8); }
}
}
