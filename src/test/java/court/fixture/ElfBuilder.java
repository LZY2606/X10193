package court.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Constructs minimal little-endian ELF64 objects for tests: ET_REL with
 * .symtab/.strtab/.rela.text and optional COMDAT groups, or ET_DYN with
 * .dynsym/.dynstr/.gnu.version{,_d,_r}/.rela.dyn/.dynamic.
 */
public final class ElfBuilder {

    public static final int ET_REL = 1;
    public static final int ET_DYN = 3;
    public static final int EM_X86_64 = 62;

    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;

    public static final int STB_LOCAL = 0, STB_GLOBAL = 1, STB_WEAK = 2;
    public static final int STT_NOTYPE = 0, STT_OBJECT = 1, STT_FUNC = 2, STT_COMMON = 5;
    public static final int STV_DEFAULT = 0, STV_HIDDEN = 2, STV_PROTECTED = 3;
    public static final short SHN_UNDEF = 0, SHN_COMMON = (short) 0xfff2;

    public static final int R_X86_64_64 = 1;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;

    private final int type;
    private int machine = EM_X86_64;
    private String soname;
    private final List<String> needed = new ArrayList<>();

    private final Map<String, byte[]> strTabs = new LinkedHashMap<>();
    private final List<SecDef> secs = new ArrayList<>();
    private final List<SymDef> syms = new ArrayList<>();
    private final List<RelDef> relas = new ArrayList<>();
    private final List<GroupDef> groups = new ArrayList<>();

    private final List<VerDef> verDefs = new ArrayList<>();
    private final List<VerNeed> verNeeds = new ArrayList<>();
    private final Map<String, Integer> symVersion = new LinkedHashMap<>();
    private final Map<String, Boolean> symHiddenVersion = new LinkedHashMap<>();

    public ElfBuilder(int type) {
        this.type = type;
    }

    public ElfBuilder machine(int m) { this.machine = m; return this; }
    public ElfBuilder soname(String s) { this.soname = s; return this; }
    public ElfBuilder needed(String s) { this.needed.add(s); return this; }

    public static final class SymDef {
        String name; int bind, type, vis; short shndx; long value, size;
        boolean dynamic;
    }

    public ElfBuilder sym(String name, int bind, int type, int vis, short shndx) {
        SymDef s = new SymDef();
        s.name = name; s.bind = bind; s.type = type; s.vis = vis;
        s.shndx = shndx; s.dynamic = type == ET_DYN;
        syms.add(s);
        return this;
    }

    public ElfBuilder symVal(String name, int bind, int type, int vis, String section,
                             long value, long size) {
        SymDef s = new SymDef();
        s.name = name; s.bind = bind; s.type = type; s.vis = vis;
        s.value = value; s.size = size; s.dynamic = this.type == ET_DYN;
        // section index resolved at build; stash section name via marker map
        pendingSection.put(s, section);
        syms.add(s);
        return this;
    }

    private final Map<SymDef, String> pendingSection = new LinkedHashMap<>();

    public ElfBuilder reloc(String targetSection, String symName, int type, long offset,
                            long addend, boolean dynamicSym) {
        RelDef r = new RelDef();
        r.target = targetSection; r.symName = symName; r.type = type;
        r.offset = offset; r.addend = addend; r.dynamicSym = dynamicSym;
        relas.add(r);
        return this;
    }

    private static final class RelDef {
        String target, symName; int type; long offset, addend; boolean dynamicSym;
    }

    public ElfBuilder group(String signature, String... members) {
        GroupDef g = new GroupDef();
        g.signature = signature;
        g.members.addAll(List.of(members));
        groups.add(g);
        return this;
    }

    private static final class GroupDef {
        String signature; final List<String> members = new ArrayList<>();
    }

    public ElfBuilder progbits(String name, int size) {
        SecDef s = new SecDef();
        s.name = name; s.type = SHT_PROGBITS; s.size = size;
        secs.add(s);
        return this;
    }

    public ElfBuilder nobits(String name, int size) {
        SecDef s = new SecDef();
        s.name = name; s.type = SHT_NOBITS; s.size = size;
        secs.add(s);
        return this;
    }

    // ----- versions -----

    public static final class VerDef {
        String name; int ndx; boolean base; List<String> aliases = new ArrayList<>();
    }

    public ElfBuilder versionDef(String name, int ndx, boolean base, String... aliases) {
        VerDef v = new VerDef();
        v.name = name; v.ndx = ndx; v.base = base; v.aliases.addAll(List.of(aliases));
        verDefs.add(v);
        return this;
    }

    public static final class VerNeed {
        String file; String version; int ndx;
    }

    public ElfBuilder versionNeed(String file, String version, int ndx) {
        VerNeed v = new VerNeed();
        v.file = file; v.version = version; v.ndx = ndx;
        verNeeds.add(v);
        return this;
    }

    public ElfBuilder symVersion(String sym, int ndx, boolean hidden) {
        symVersion.put(sym, ndx);
        symHiddenVersion.put(sym, hidden);
        return this;
    }

    private static final class SecDef {
        String name; int type; long size; byte[] data;
        int link, info; long entsize; long flags; long addr; long align = 1;
        int info2; // sh_name offset
        GroupDef group;
    }

    public byte[] build() {
        try {
            return doBuild();
        } catch (Exception e) {
            throw new RuntimeException("elf build failed", e);
        }
    }

    private byte[] doBuild() throws Exception {
        // ---- section plan (index order matters for sym shndx) ----
        List<SecDef> plan = new ArrayList<>();
        // 0 = null
        SecDef shstr = new SecDef(); shstr.name = ".shstrtab"; shstr.type = SHT_STRTAB; plan.add(shstr);
        for (SecDef s : secs) plan.add(s);

        // COMDAT group sections: each group signature needs a symbol in symtab;
        // create the .group sec before referenced member sections.
        List<SecDef> groupSecs = new ArrayList<>();
        for (GroupDef gd : groups) {
            SecDef g = new SecDef();
            g.name = ".group." + gd.signature; g.type = SHT_GROUP;
            g.size = 4 + 4L * (gd.members.size() + 1);
            g.group = gd;
            groupSecs.add(g);
        }
        // insert groups right after progbits, but members must exist; index fix-up
        // happens after indices are assigned.
        plan.addAll(groupSecs);

        boolean dyn = type == ET_DYN;
        SecDef strtab = new SecDef();
        strtab.name = dyn ? ".dynstr" : ".strtab"; strtab.type = SHT_STRTAB;
        plan.add(strtab);
        SecDef symtab = new SecDef();
        symtab.name = dyn ? ".dynsym" : ".symtab";
        symtab.type = dyn ? SHT_DYNSYM : SHT_SYMTAB;
        symtab.entsize = 24;
        plan.add(symtab);

        // relocation sections (one per target)
        Map<String, SecDef> relocSecs = new LinkedHashMap<>();
        for (RelDef r : relas) {
            relocSecs.computeIfAbsent(r.target + (r.dynamicSym ? "#dyn" : "#static"), k -> {
                SecDef x = new SecDef();
                x.name = ".rela" + r.target + (r.dynamicSym ? ".dyn" : "");
                x.type = SHT_RELA; x.entsize = 24;
                return x;
            });
        }
        // In our fixtures a target carries either dyn or static relocs, not both.
        Map<String, SecDef> byTarget = new LinkedHashMap<>();
        for (RelDef r : relas) byTarget.put(r.target, relocSecs.get(r.target+(r.dynamicSym?"#dyn":"#static")));
        plan.addAll(byTarget.values());

        SecDef verSec = null, verdefSec = null, verneedSec = null, dynSec = null;
        if (dyn && !symVersion.isEmpty() || dyn && (!verDefs.isEmpty() || !verNeeds.isEmpty())) {
            verSec = new SecDef(); verSec.name=".gnu.version"; verSec.type=SHT_PROGBITS; plan.add(verSec);
        }
        if (dyn && !verDefs.isEmpty()) {
            verdefSec = new SecDef(); verdefSec.name=".gnu.version_d"; verdefSec.type=SHT_PROGBITS;
            plan.add(verdefSec);
        }
        if (dyn && !verNeeds.isEmpty()) {
            verneedSec = new SecDef(); verneedSec.name=".gnu.version_r"; verneedSec.type=SHT_PROGBITS;
            plan.add(verneedSec);
        }
        if (dyn) {
            dynSec = new SecDef(); dynSec.name=".dynamic"; dynSec.type=SHT_DYNAMIC;
            dynSec.entsize = 16; plan.add(dynSec);
        }

        // Positional index BEFORE inserting the NULL section at index 0.
        Map<SecDef, Integer> posIndex = new LinkedHashMap<>();
        for (int i = 0; i < plan.size(); i++) posIndex.put(plan.get(i), i);
        Map<String, Integer> nameIndex = new LinkedHashMap<>();
        for (int i = 0; i < plan.size(); i++) nameIndex.putIfAbsent(plan.get(i).name, i);

        // ---- string tables ----
        StrBuilder shstrb = new StrBuilder();
        for (SecDef s : plan) s.data = s.data == null ? new byte[(int) Math.max(0, s.size)] : s.data;
        for (SecDef s : plan) s.info2 = shstrb.put(s.name);
        shstr.data = shstrb.bytes();

        StrBuilder strb = new StrBuilder();
        Map<String, Integer> symNameOff = new LinkedHashMap<>();
        for (GroupDef gd : groups) symNameOff.put(gd.signature, strb.put(gd.signature));
        for (SymDef s : syms) symNameOff.put(s.name, strb.put(s.name));
        strb.put(soname == null ? "" : soname);
        Map<String, Integer> dynStrings = new LinkedHashMap<>();
        if (dyn) {
            for (String n : needed) dynStrings.put("NEEDED:" + n, strb.put(n));
            if (soname != null) dynStrings.put("SONAME", strb.put(soname));
        }
        for (VerDef vd : verDefs) {
            dynStrings.put("VD:" + vd.name, strb.put(vd.name));
            for (String a : vd.aliases) dynStrings.put("VDA:" + a, strb.put(a));
        }
        for (VerNeed vn : verNeeds) {
            dynStrings.put("VNF:" + vn.file, strb.put(vn.file));
            dynStrings.put("VNA:" + vn.version, strb.put(vn.version));
        }
        strtab.data = strb.bytes();

        // ---- symbol table: null entry + locals first then globals ----
        ByteArrayOutputStream symOut = new ByteArrayOutputStream();
        writeSym(symOut, 0, 0, 0, 0, (short) 0, 0, 0);
        List<SymDef> locals = new ArrayList<>();
        List<SymDef> globals = new ArrayList<>();
        // group signature symbols are LOCAL NOTYPE in symtab
        for (GroupDef gd : groups) {
            SymDef sig = new SymDef();
            sig.name = gd.signature; sig.bind = STB_LOCAL; sig.type = STT_NOTYPE;
            sig.vis = STV_DEFAULT; sig.shndx = SHN_UNDEF;
            locals.add(sig);
        }
        for (SymDef s : syms) {
            if (s.bind == STB_LOCAL) locals.add(s); else globals.add(s);
        }
        Map<String, Integer> symIdx = new LinkedHashMap<>();
        int idx = 1;
        for (SymDef s : locals) {
            symIdx.put(s.name + "@" + idx, idx);
            short sh = resolveShndx(s, nameIndex);
            writeSym(symOut, symNameOff.get(s.name), s.bind, s.type, s.vis, sh, s.value, s.size);
            idx++;
        }
        symtab.info = idx; // first global index
        for (SymDef s : globals) {
            symIdx.put(s.name, idx);
            short sh = resolveShndx(s, nameIndex);
            writeSym(symOut, symNameOff.get(s.name), s.bind, s.type, s.vis, sh, s.value, s.size);
            idx++;
        }
        symtab.data = symOut.toByteArray();
        symtab.size = symtab.data.length;
        symtab.link = posIndex.get(strtab);
        symtab.align = 8;

        // ---- group section data ----
        int grpI = 0;
        for (SecDef g : groupSecs) {
            GroupDef gd = g.group;
            ByteArrayOutputStream go = new ByteArrayOutputStream();
            write4(go, 1); // GRP_COMDAT
            // signature symbol index: find local sig
            int sigIdx = 1 + grpI; // locals order: sigs first
            g.link = posIndex.get(symtab);
            g.info = sigIdx;
                for (String m : gd.members) {
                int mi = memberIndex(plan, m, g);
                write4(go, mi);
            }
            g.data = go.toByteArray();
            g.size = g.data.length;
            g.align = 4;
            grpI++;
        }

        // ---- relocations ----
        Map<SecDef, List<RelDef>> relsBySec = new LinkedHashMap<>();
        for (RelDef r : relas) {
            SecDef rs = byTarget.get(r.target);
            relsBySec.computeIfAbsent(rs, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<SecDef, List<RelDef>> en : relsBySec.entrySet()) {
            SecDef rs = en.getKey();
            ByteArrayOutputStream ro = new ByteArrayOutputStream();
            rs.link = posIndex.get(symtab);
            rs.info = posIndex.get(sectionByName(plan, en.getValue().get(0).target));
            rs.align = 8;
            for (RelDef r : en.getValue()) {
                Integer si = symIdx.get(r.symName);
                if (si == null) throw new IllegalStateException("reloc sym missing: " + r.symName);
                writeRela(ro, r.offset, (long) si << 32 | (r.type & 0xffffffffL), r.addend);
            }
            rs.data = ro.toByteArray();
            rs.size = rs.data.length;
        }

        // ---- version sections ----
        if (verSec != null) {
            ByteArrayOutputStream vo = new ByteArrayOutputStream();
            for (int i = 0; i < globals.size() + 1 + locals.size(); i++) {
                // map index->symbol
                SymDef s = i == 0 ? null : (i - 1 < locals.size() ? locals.get(i - 1)
                        : globals.get(i - 1 - locals.size()));
                int val = 1;
                if (s != null && symVersion.containsKey(s.name)) {
                    val = symVersion.get(s.name);
                    if (Boolean.TRUE.equals(symHiddenVersion.get(s.name))) val |= 0x8000;
                }
                write2(vo, val);
            }
            verSec.data = vo.toByteArray();
            verSec.size = verSec.data.length;
            verSec.link = posIndex.get(strtab);
            verSec.align = 2;
        }

        if (verdefSec != null) {
            ByteArrayOutputStream vdOut = new ByteArrayOutputStream();
            boolean[] first = {true};
            for (VerDef vd : verDefs) {
                int start = vdOut.size();
                int cnt = 1 + vd.aliases.size();
                write2(vdOut, 1); // version
                write2(vdOut, vd.base ? 1 : 0); // flags VER_FLG_BASE
                write2(vdOut, vd.ndx);
                write2(vdOut, cnt);
                write4(vdOut, 0); // hash
                write4(vdOut, 20); // aux offset
                write4(vdOut, 0); // next (patched later)
                int auxStart = vdOut.size();
                write4(vdOut, dynStrings.get("VD:" + vd.name));
                write4(vdOut, vd.aliases.isEmpty() ? 0 : 16); // next aux
                write4(vdOut, 0); // hash
                write2(vdOut, 0); // other
                vdOut.write(new byte[2]); // pad to 16
                for (String a : vd.aliases) {
                    write4(vdOut, dynStrings.get("VDA:" + a));
                    write4(vdOut, 0);
                    write4(vdOut, 0);
                    write2(vdOut, 0);
                    vdOut.write(new byte[2]);
                }
                    verDefsLayout.add(new int[]{start});
                first[0] = false;
            }
            byte[] vdB = vdOut.toByteArray();
            for (int i = 0; i < verDefsLayout.size(); i++) {
                int cur = verDefsLayout.get(i)[0];
                int nextOff = i + 1 < verDefsLayout.size()
                        ? verDefsLayout.get(i + 1)[0] - cur : 0;
                patch4(vdB, cur + 16, nextOff);
            }
            verdefSec.data = vdB;
            verdefSec.size = verdefSec.data.length;
            verdefSec.link = posIndex.get(strtab);
            verdefSec.align = 8;
        }

        if (verneedSec != null) {
            ByteArrayOutputStream vnOut = new ByteArrayOutputStream();
            List<Integer> starts = new ArrayList<>();
            for (VerNeed vn : verNeeds) {
                starts.add(vnOut.size());
                write2(vnOut, 1);
                write2(vnOut, 1); // cnt
                write4(vnOut, dynStrings.get("VNF:" + vn.file));
                write4(vnOut, 16); // aux
                write4(vnOut, 0); // next
                // aux Vernaux
                write4(vnOut, 0); // hash
                write2(vnOut, 0); // flags
                write2(vnOut, vn.ndx); // vna_other
                write4(vnOut, dynStrings.get("VNA:" + vn.version));
                write4(vnOut, 0);
            }
            byte[] vnB = vnOut.toByteArray();
            for (int i = 0; i < starts.size(); i++) {
                int nextOff = i + 1 < starts.size() ? starts.get(i + 1) - starts.get(i) : 0;
                patch4(vnB, starts.get(i) + 12, nextOff);
            }
            verneedSec.data = vnB;
            verneedSec.size = verneedSec.data.length;
            verneedSec.link = posIndex.get(strtab);
            verneedSec.align = 8;
        }

        if (dynSec != null) {
            ByteArrayOutputStream dOut = new ByteArrayOutputStream();
            for (String n : needed) writeDyn(dOut, 1, dynStrings.get("NEEDED:" + n));
            if (soname != null) writeDyn(dOut, 14, dynStrings.get("SONAME"));
            writeDyn(dOut, 0, 0);
            dynSec.data = dOut.toByteArray();
            dynSec.size = dynSec.data.length;
            dynSec.link = posIndex.get(strtab);
            dynSec.align = 8;
        }

        return emit(plan, shstr, strtab, symtab, machine);
    }

    private final List<int[]> verDefsLayout = new ArrayList<>();

    private SecDef sectionByName(List<SecDef> plan, String name) {
        for (SecDef sd : plan) if (sd.name.equals(name)) return sd;
        throw new IllegalStateException("section missing: " + name);
    }

    private int memberIndex(List<SecDef> plan, String member, SecDef groupSec) {
        // index in the FINAL layout = positional index + 1 for the NULL section
        SecDef target = sectionByName(plan, member);
        for (int i = 0; i < plan.size(); i++) {
            if (plan.get(i) == target) return i + 1;
        }
        throw new IllegalStateException("member missing: " + member);
    }

    private short resolveShndx(SymDef s, Map<String, Integer> nameIndex) {
        if (s.shndx != 0) return s.shndx; // SHN_COMMON etc. are special values
        String sec = pendingSection.get(s);
        if (sec == null) return SHN_UNDEF;
        Integer i = nameIndex.get(sec);
        if (i == null) throw new IllegalStateException("sym section missing: " + sec);
        // nameIndex predates insertion of the mandatory NULL section at index 0.
        return (short) (i + 1);
    }

    private byte[] emit(List<SecDef> plan, SecDef shstr, SecDef strtab, SecDef symtab,
                        int machine) throws Exception {
        // link/info were assigned as pre-NULL positional indices; convert to
        // final section indices by adding one (symtab.info is not a section index).
        for (SecDef sd : plan) {
            if (sd.link > 0) sd.link += 1;
            if (sd.info > 0 && sd.type != SHT_SYMTAB && sd.type != SHT_DYNSYM) sd.info += 1;
        }
        // Insert implicit SHT_NULL at index 0.
        plan.add(0, new SecDef());
        int n = plan.size();
        int ehsize = 64;
        long off = ehsize;
        long[] offsets = new long[n];
        long[] sizes = new long[n];
        long[] addrs = new long[n];
        for (int i = 0; i < n; i++) {
            SecDef sd = plan.get(i);
            if (sd.type == SHT_NOBITS) {
                long a = Math.max(1, sd.align);
                off = (off + a - 1) / a * a;
                offsets[i] = off;
                sizes[i] = sd.size;
                addrs[i] = sd.addr;
                off += sd.size;
            } else if (i > 0) {
                long a = Math.max(1, sd.align);
                off = (off + a - 1) / a * a;
                offsets[i] = off;
                byte[] data = sd.data == null ? new byte[0] : sd.data;
                sizes[i] = sd.size > 0 ? sd.size : data.length;
                off += sizes[i];
            }
        }
        long shentsize = 64;
        off = (off + 7) / 8 * 8;
        long shoff = off;
        long total = shoff + (long) n * shentsize;

        byte[] out = new byte[(int) total];
        // ELF header
        out[0]=0x7f;out[1]='E';out[2]='L';out[3]='F';
        out[4]=2; // ELFCLASS64
        out[5]=1; // little endian
        out[6]=1; // EV_CURRENT
        put2(out,16,type);
        put2(out,18,machine);
        put4(out,20,1);
        put8(out,32,0); // phoff
        put8(out,40,shoff);
        put4(out,48,64); // ehsize
        put2(out,58,(int)shentsize);
        put2(out,60,n);
        put2(out,62,1); // shstrndx

        for (int i = 0; i < n; i++) {
            SecDef sd = plan.get(i);
            int b = (int) (shoff + (long) i * shentsize);
            put4(out, b + 0, i == 0 ? 0 : sd.info2);
            put4(out, b + 4, sd.type);
            put8(out, b + 8, sd.flags);
            put8(out, b + 16, addrs[i]);
            put8(out, b + 24, offsets[i]);
            put8(out, b + 32, sizes[i]);
            put4(out, b + 40, sd.link);
            put4(out, b + 44, sd.info);
            put8(out, b + 48, Math.max(1, sd.align));
            put8(out, b + 56, sd.entsize);
            if (i > 0 && sd.type != SHT_NOBITS && sd.data != null) {
                System.arraycopy(sd.data, 0, out, (int) offsets[i], sd.data.length);
            }
        }
        return out;
    }

    private static void writeSym(ByteArrayOutputStream o, int name, int bind, int type,
                                 int vis, short shndx, long value, long size) {
        write4(o, name);
        o.write((bind << 4) | (type & 0xf));
        o.write(vis & 3);
        write2(o, shndx & 0xffff);
        write8(o, value);
        write8(o, size);
    }

    private static void writeRela(ByteArrayOutputStream o, long off, long info, long add) {
        write8(o, off);
        write8(o, info);
        write8(o, add);
    }

    private static void writeDyn(ByteArrayOutputStream o, long tag, long val) {
        write8(o, tag);
        write8(o, val);
    }

    private static void write2(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff); o.write((v >>> 8) & 0xff);
    }

    private static void write4(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff); o.write((v >>> 8) & 0xff);
        o.write((v >>> 16) & 0xff); o.write((v >>> 24) & 0xff);
    }

    private static void write8(ByteArrayOutputStream o, long v) {
        for (int i = 0; i < 8; i++) o.write((int) (v >>> (8 * i)) & 0xff);
    }

    private static void put2(byte[] b, int o, int v) {
        b[o]=(byte)v; b[o+1]=(byte)(v>>>8);
    }

    private static void put4(byte[] b, int o, int v) {
        b[o]=(byte)v; b[o+1]=(byte)(v>>>8); b[o+2]=(byte)(v>>>16); b[o+3]=(byte)(v>>>24);
    }

    private static void put8(byte[] b, int o, long v) {
        for (int i = 0; i < 8; i++) b[o + i] = (byte) (v >>> (8 * i));
    }

    private static void patch4(byte[] b, int o, int v) {
        b[o]=(byte)v; b[o+1]=(byte)(v>>>8); b[o+2]=(byte)(v>>>16); b[o+3]=(byte)(v>>>24);
    }

    private static final class StrBuilder {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final Map<String, Integer> map = new LinkedHashMap<>();

        StrBuilder() {
            out.write(0);
        }

        int put(String s) {
            byte[] b = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
            int existing = map.getOrDefault(nullSafe(s), -1);
            if (existing >= 0) return existing;
            int off = out.size();
            map.put(nullSafe(s), off);
            out.write(b, 0, b.length);
            out.write(0);
            return off;
        }

        private String nullSafe(String s) { return s == null ? "" : s; }

        byte[] bytes() { return out.toByteArray(); }
    }
}
