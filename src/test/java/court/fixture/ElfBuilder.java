package court.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java 字节级 ELF64-LE 构造器（x86_64）。产出可直接喂给生产代码 ElfParser，
 * 实现“程序构造的小型 ELF fixture”测试，不依赖系统链接器/汇编器。
 *
 * 支持：symtab/dynsym、strtab/dynstr、RELA、SHT_GROUP(COMDAT)、
 * versym/verdef/verneed、.dynamic(DT_NEEDED/DT_SONAME)、PROGBITS；ET_REL 或 ET_DYN。
 */
public final class ElfBuilder {

    static final int T_PROGBITS = 1, T_SYMTAB = 2, T_STRTAB = 3, T_RELA = 4,
            T_NOBITS = 8, T_DYNSYM = 11, T_GROUP = 17, T_DYNAMIC = 6;
    static final int T_VERDEF = 0x6ffffffd, T_VERNEED = 0x6ffffffe, T_VERSYM = 0x6fffffff;
    static final long F_ALLOC = 2, F_WRITE = 1, F_EXEC = 4, F_GROUP = 0x200L;

    private boolean dyn;
    private String soname;
    private final List<String> needed = new ArrayList<>();
    private final List<byte[]> progbits = new ArrayList<>();
    private final List<GroupSpec> groups = new ArrayList<>();
    private final List<Sym> symbols = new ArrayList<>();
    private final List<Rela> relas = new ArrayList<>();
    private final List<Verdef> verdefs = new ArrayList<>();
    private final List<Verneed> verneeds = new ArrayList<>();

    public record Verdef(String name, int index, boolean base, List<String> aliases) {
    }

    public record Verneed(String file, String versionName, int index) {
    }

    public static ElfBuilder rel() {
        return new ElfBuilder();
    }

    public static ElfBuilder so() {
        ElfBuilder b = new ElfBuilder();
        b.dyn = true;
        return b;
    }

    public ElfBuilder soname(String s) { this.soname = s; return this; }
    public ElfBuilder need(String lib) { this.needed.add(lib); return this; }
    public ElfBuilder data(byte[] d) { this.progbits.add(d); return this; }
    public ElfBuilder text(String marker) { return data(marker.getBytes(StandardCharsets.UTF_8)); }
    public ElfBuilder groups(GroupSpec... gs) { this.groups.addAll(List.of(gs)); return this; }
    public ElfBuilder syms(Sym... ss) { this.symbols.addAll(List.of(ss)); return this; }
    public ElfBuilder relas(Rela... rs) { this.relas.addAll(List.of(rs)); return this; }
    public ElfBuilder verdef(Verdef v) { this.verdefs.add(v); return this; }
    public ElfBuilder verneed(Verneed v) { this.verneeds.add(v); return this; }

    private static final class Str {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Map<String, Integer> off = new LinkedHashMap<>();
        Str() { out.write(0); }
        int intern(String s) {
            Integer e = off.get(s);
            if (e != null) return e;
            int o = out.size();
            off.put(s, o);
            out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
            out.write(0);
            return o;
        }
        byte[] bytes() { return out.toByteArray(); }
        int get(String s) { return off.getOrDefault(s, 0); }
    }

    private static final class Sec {
        String name;
        int type;
        long flags;
        long addr;
        byte[] body = EMPTY;
        int link;
        int info;
        long align = 1;
        long entsize;
        long offset;
        boolean alloc;
    }

    private static final byte[] EMPTY = new byte[0];

    public byte[] build() {
        try {
            return doBuild();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("fixture 构造失败: " + e.getMessage(), e);
        }
    }

    private byte[] doBuild() throws Exception {
        List<Sec> secs = new ArrayList<>();
        secs.add(new Sec()); // index 0
        Str shstr = new Str();
        Str strtab = new Str();
        Str dynstr = new Str();

        // progbits
        List<Integer> progIdx = new ArrayList<>();
        for (int i = 0; i < progbits.size(); i++) {
            Sec s = new Sec();
            s.name = ".text" + (progbits.size() > 1 ? "." + i : "");
            s.type = T_PROGBITS;
            s.flags = F_ALLOC | F_EXEC;
            s.alloc = true;
            s.body = progbits.get(i);
            secs.add(s);
            progIdx.add(secs.size() - 1);
        }

        // COMDAT 成员 section + group section；group.sh_info = 签名符号索引（稍后修正）
        List<int[]> groupFixup = new ArrayList<>(); // {groupSecIdx}
        for (GroupSpec g : groups) {
            List<Integer> members = new ArrayList<>();
            for (byte[] md : g.memberData) {
                Sec ms = new Sec();
                ms.name = ".text." + g.signature;
                ms.type = T_PROGBITS;
                ms.flags = F_ALLOC | F_EXEC | F_GROUP;
                ms.alloc = true;
                ms.body = md;
                secs.add(ms);
                members.add(secs.size() - 1);
            }
            Sec grp = new Sec();
            grp.name = ".group";
            grp.type = T_GROUP;
            grp.entsize = 4;
            ByteArrayOutputStream gb = new ByteArrayOutputStream();
            w32(gb, 0x1);
            for (int m : members) w32(gb, m);
            grp.body = gb.toByteArray();
            secs.add(grp);
            g._memberIndices = members;
            groupFixup.add(new int[]{secs.size() - 1});
        }

        // 符号表
        Sec symSec = new Sec();
        symSec.name = dyn ? ".dynsym" : ".symtab";
        symSec.type = dyn ? T_DYNSYM : T_SYMTAB;
        symSec.entsize = 24;
        symSec.alloc = dyn;
        symSec.flags = dyn ? F_ALLOC : 0;
        secs.add(symSec);
        int symSecIdx = secs.size() - 1;

        Str names = dyn ? dynstr : strtab;
        // locals: group 签名符号（LOCAL NOTYPE, shndx=group sec），再用户 locals
        List<Sym> ordered = new ArrayList<>();
        int localCount = 1;
        for (int gi = 0; gi < groups.size(); gi++) {
            GroupSpec g = groups.get(gi);
            Sym sig = new Sym(g.signature);
            sig.bind = 0;
            sig.shndx = groupFixup.get(gi)[0]; // 1 基 section 索引
            ordered.add(sig);
            localCount++;
            // 组 section 关联符号表 + 签名符号索引
            secs.get(groupFixup.get(gi)[0]).link = symSecIdx;
            secs.get(groupFixup.get(gi)[0]).info = localCount - 1;
        }
        for (Sym s : symbols) if (s.bind == 0) { ordered.add(s); localCount++; }
        for (Sym s : symbols) if (s.bind != 0) ordered.add(s);

        Map<String, Integer> nameToSymIdx = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) nameToSymIdx.put(ordered.get(i).name, i + 1);

        // versym（与符号表等长）
        int versymIdx = -1;
        if (dyn) {
            Sec vs = new Sec();
            vs.name = ".gnu.version";
            vs.type = T_VERSYM;
            vs.flags = F_ALLOC;
            vs.alloc = true;
            vs.link = symSecIdx;
            vs.entsize = 2;
            ByteArrayOutputStream vb = new ByteArrayOutputStream();
            w16(vb, 0);
            for (Sym s : ordered) w16(vb, s.versionIndex == null ? 0 : s.versionIndex);
            vs.body = vb.toByteArray();
            secs.add(vs);
            versymIdx = secs.size() - 1;
        }

        // verdef / verneed
        if (dyn && !verdefs.isEmpty()) {
            Sec vd = new Sec();
            vd.name = ".gnu.version_d";
            vd.type = T_VERDEF;
            vd.flags = F_ALLOC;
            vd.alloc = true;
            vd.body = buildVerdef(verdefs, dynstr);
            secs.add(vd);
        }
        if (dyn && !verneeds.isEmpty()) {
            Sec vn = new Sec();
            vn.name = ".gnu.version_r";
            vn.type = T_VERNEED;
            vn.flags = F_ALLOC;
            vn.alloc = true;
            vn.body = buildVerneed(verneeds, dynstr);
            secs.add(vn);
        }

        // rela
        if (!relas.isEmpty()) {
            Sec r = new Sec();
            r.name = ".rela.text";
            r.type = T_RELA;
            r.entsize = 24;
            r.link = symSecIdx;
            r.info = progIdx.isEmpty() ? 1 : progIdx.get(0);
            ByteArrayOutputStream rb = new ByteArrayOutputStream();
            for (Rela rl : relas) {
                Integer si = nameToSymIdx.get(rl.symName);
                if (si == null) throw new IllegalStateException("rela 符号缺失: " + rl.symName);
                w64(rb, rl.offset);
                w64(rb, ((long) si << 32) | (rl.type & 0xffffffffL));
                w64(rb, rl.addend);
            }
            r.body = rb.toByteArray();
            secs.add(r);
        }

        // 符号表 body（此时名字表已包含所有符号名——需先 intern）
        for (Sym s : ordered) names.intern(s.name);
        symSec.body = buildSyms(ordered, names);
        symSec.info = localCount;

        return layout(secs, shstr, strtab, dynstr);
    }

    private byte[] buildSyms(List<Sym> ordered, Str names) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 24; i++) out.write(0); // STN_UNDEF
        for (Sym s : ordered) {
            w32(out, names.get(s.name));
            out.write((s.bind & 0xf) << 4 | (s.type & 0xf));
            out.write(s.vis & 3);
            w16(out, s.shndx & 0xffff);
            w64(out, s.value);
            w64(out, s.size);
        }
        return out.toByteArray();
    }

    private byte[] buildVerdef(List<Verdef> defs, Str dynstr) throws Exception {
        List<Verdef> sorted = new ArrayList<>(defs);
        sorted.sort((a, b) -> Integer.compare(a.index, b.index));
        ByteArrayOutputStream flat = new ByteArrayOutputStream();
        List<int[]> patches = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Verdef v = sorted.get(i);
            List<String> namesList = new ArrayList<>();
            namesList.add(v.name);
            namesList.addAll(v.aliases);
            ByteArrayOutputStream aux = new ByteArrayOutputStream();
            for (int ai = 0; ai < namesList.size(); ai++) {
                w32(aux, ai < namesList.size() - 1 ? 8 : 0);
                w32(aux, dynstr.intern(namesList.get(ai)));
            }
            w16(flat, 1);
            w16(flat, v.base ? 1 : 0);
            w16(flat, v.index);
            w16(flat, namesList.size());
            w32(flat, 0);
            w32(flat, 20);
            int nextPos = flat.size();
            w32(flat, 0);
            flat.writeBytes(aux.toByteArray());
            int blockLen = 20 + aux.size();
            if (i < sorted.size() - 1) patches.add(new int[]{nextPos, blockLen});
        }
        byte[] res = flat.toByteArray();
        for (int[] p : patches) put32At(res, p[0], p[1]);
        return res;
    }

    private byte[] buildVerneed(List<Verneed> needs, Str dynstr) throws Exception {
        ByteArrayOutputStream flat = new ByteArrayOutputStream();
        // 按 file 分组
        Map<String, List<Verneed>> byFile = new LinkedHashMap<>();
        for (Verneed v : needs) byFile.computeIfAbsent(v.file, k -> new ArrayList<>()).add(v);
        List<int[]> nextPatches = new ArrayList<>();
        int fi = 0;
        for (Map.Entry<String, List<Verneed>> e : byFile.entrySet()) {
            int fileOff = dynstr.intern(e.getKey());
            // aux 块（每项 16 字节）
            ByteArrayOutputStream aux = new ByteArrayOutputStream();
            List<Verneed> list = e.getValue();
            for (int ai = 0; ai < list.size(); ai++) {
                Verneed v = list.get(ai);
                w32(aux, 0); // vna_hash
                w16(aux, 0); // vna_flags（非 WEAK => 要求确切版本）
                w16(aux, v.index); // vna_other
                w32(aux, dynstr.intern(v.versionName));
                w32(aux, ai < list.size() - 1 ? 16 : 0); // vna_next
            }
            w16(flat, 1); // vn_version
            w16(flat, list.size());
            w32(flat, fileOff);
            w32(flat, 16); // vn_aux
            int nextPos = flat.size();
            w32(flat, 0); // vn_next
            flat.writeBytes(aux.toByteArray());
            int blockLen = 16 + aux.size();
            if (fi < byFile.size() - 1) nextPatches.add(new int[]{nextPos, blockLen});
            fi++;
        }
        byte[] res = flat.toByteArray();
        for (int[] p : nextPatches) put32At(res, p[0], p[1]);
        return res;
    }


    private byte[] buildDynamic(List<Sec> secs, Str dynstr, int dynstrIdx, int symIdx,
                                int verdefIdx, int verneedIdx, int versymIdx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long symVA = secs.get(symIdx).addr;
        long strVA = secs.get(dynstrIdx).addr;
        dyn(out, 6, symVA);
        dyn(out, 5, strVA);
        dyn(out, 11, 24);
        dyn(out, 10, secs.get(dynstrIdx).body.length);
        for (String n : needed) dyn(out, 1, dynstr.get(n));
        if (soname != null) dyn(out, 14, dynstr.get(soname));
        if (verdefIdx >= 0) {
            dyn(out, 0x6ffffffcL, secs.get(verdefIdx).addr);
            dyn(out, 0x6ffffffdL, verdefs.size());
        }
        if (verneedIdx >= 0) {
            dyn(out, 0x6ffffffeL, secs.get(verneedIdx).addr);
            dyn(out, 0x6fffffffL,
                    (int) verneeds.stream().map(v -> v.file).distinct().count());
        }
        if (versymIdx >= 0) dyn(out, 0x6ffffff0L, secs.get(versymIdx).addr);
        dyn(out, 0, 0);
        return out.toByteArray();
    }

    private byte[] layout(List<Sec> secs, Str shstr, Str strtab, Str dynstr) throws Exception {
        int dynstrIdx = -1;
        if (dyn) {
            // NEEDED / SONAME 名字必须在 .dynamic 构建前进入 dynstr
            for (String n : needed) dynstr.intern(n);
            if (soname != null) dynstr.intern(soname);
            Sec ds = new Sec();
            ds.name = ".dynstr";
            ds.type = T_STRTAB;
            ds.flags = F_ALLOC;
            ds.alloc = true;
            ds.body = dynstr.bytes();
            secs.add(ds);
            dynstrIdx = secs.size() - 1;
            // symtab/verdef/verneed link = dynstr
            for (Sec x : secs) {
                if (x.type == T_DYNSYM || x.type == T_VERDEF || x.type == T_VERNEED) {
                    x.link = dynstrIdx;
                }
            }
            int vdIdx = -1, vnIdx = -1, vsIdx = -1, symIdx = -1;
            for (int i = 1; i < secs.size(); i++) {
                switch (secs.get(i).type) {
                    case T_DYNSYM -> symIdx = i;
                    case T_VERDEF -> vdIdx = i;
                    case T_VERNEED -> vnIdx = i;
                    case T_VERSYM -> vsIdx = i;
                    default -> { }
                }
            }
            Sec d = new Sec();
            d.name = ".dynamic";
            d.type = T_DYNAMIC;
            d.flags = F_ALLOC | F_WRITE;
            d.alloc = true;
            d.entsize = 16;
            d.link = dynstrIdx;
            d.body = buildDynamic(secs, dynstr, dynstrIdx, symIdx, vdIdx, vnIdx, vsIdx);
            secs.add(d);
        } else {
            Sec st = new Sec();
            st.name = ".strtab";
            st.type = T_STRTAB;
            st.body = strtab.bytes();
            secs.add(st);
            int strIdx = secs.size() - 1;
            for (Sec x : secs) if (x.type == T_SYMTAB) x.link = strIdx;
        }
        Sec sh = new Sec();
        sh.name = ".shstrtab";
        sh.type = T_STRTAB;
        // 所有 section 名先 intern
        for (Sec x : secs) if (x.name != null) shstr.intern(x.name);
        shstr.intern(".shstrtab");
        sh.body = shstr.bytes();
        secs.add(sh);
        int shstrIdx = secs.size() - 1;

        // ---- 排布文件：alloc 节顺序分配 vaddr；全部节分配文件 offset
        long off = 64;
        long vaddr = 0x1000;
        for (int i = 1; i < secs.size(); i++) {
            Sec x = secs.get(i);
            if (x.alloc) {
                x.addr = vaddr;
                vaddr += Math.max(x.body.length, 1);
            }
            off = align8(off);
            x.offset = off;
            off += x.body.length;
        }
        off = align8(off);
        long shoff = off;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] ident = {0x7f, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        out.writeBytes(ident);
        w16(out, dyn ? 3 : 1);
        w16(out, 62);
        w32(out, 1);
        w64(out, 0);
        w64(out, 0);
        w64(out, shoff);
        w32(out, 0);
        w16(out, 64);
        w16(out, 0);
        w16(out, 0);
        w16(out, 64);
        w16(out, secs.size());
        w16(out, shstrIdx);
        for (int i = 1; i < secs.size(); i++) {
            while (out.size() < secs.get(i).offset) out.write(0);
            out.writeBytes(secs.get(i).body);
        }
        while (out.size() < shoff) out.write(0);
        for (int i = 0; i < secs.size(); i++) {
            Sec x = secs.get(i);
            if (i == 0) {
                out.writeBytes(new byte[64]);
                continue;
            }
            w32(out, x.name == null ? 0 : shstr.get(x.name));
            w32(out, x.type);
            w64(out, x.flags);
            w64(out, x.addr);
            w64(out, x.offset);
            w64(out, x.body.length);
            w32(out, x.link);
            w32(out, x.info);
            w64(out, x.align);
            w64(out, x.entsize);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------ 小端编码

    private static void w8(ByteArrayOutputStream o, int v) { o.write(v & 0xff); }
    private static void w16(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >>> 8) & 0xff); }
    private static void w32(ByteArrayOutputStream o, long v) {
        o.write((int) (v & 0xff)); o.write((int) ((v >>> 8) & 0xff));
        o.write((int) ((v >>> 16) & 0xff)); o.write((int) ((v >>> 24) & 0xff));
    }
    private static void w64(ByteArrayOutputStream o, long v) {
        for (int i = 0; i < 8; i++) o.write((int) ((v >>> (8 * i)) & 0xff));
    }
    private static void dyn(ByteArrayOutputStream o, long tag, long val) { w64(o, tag); w64(o, val); }
    private static void put32At(byte[] a, int off, int v) {
        a[off] = (byte) v; a[off + 1] = (byte) (v >>> 8);
        a[off + 2] = (byte) (v >>> 16); a[off + 3] = (byte) (v >>> 24);
    }
    private static long align8(long v) { return (v + 7) & ~7L; }
}
