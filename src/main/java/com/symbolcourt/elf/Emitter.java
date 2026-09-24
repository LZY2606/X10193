package com.symbolcourt.elf;

import com.symbolcourt.elf.FixtureBuilder.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Emits the small ELF64 LSB images described by a {@link FixtureBuilder}. */
public class Emitter {

    private final FixtureBuilder b;
    private final boolean image;

    private final List<Sec> sections = new ArrayList<>();
    private Sec text, data;
    private final List<Sec> comdats = new ArrayList<>();
    private final Map<String, Sec> comdatByName = new LinkedHashMap<>();
    private Sec dynsec, dynsym, dynversym, verdef, verneed, symtab, dynstrSec, symstrSec, shstrSec;
    private final List<Sec> rela = new ArrayList<>();
    private final Map<String, Sec> groups = new LinkedHashMap<>();

    private final Strtab shstr = new Strtab();
    private final Strtab symstr = new Strtab();
    private final Strtab dynstr = new Strtab();

    private final List<Sym> statics = new ArrayList<>();   // ordered for .symtab
    private final List<Sym> dyns = new ArrayList<>();      // ordered for .dynsym
    private final Map<Sym, Integer> staticPos = new HashMap<>();
    private final Map<Sym, Integer> dynPos = new HashMap<>();

    private final Map<String, Integer> defIndex = new LinkedHashMap<>();
    private final Map<String, Integer> needIndex = new LinkedHashMap<>();
    private final Map<String, String> needFile = new LinkedHashMap<>();
    private final Map<String, Boolean> needWeak = new HashMap<>();

    public Emitter(FixtureBuilder b) {
        this.b = b;
        this.image = b.getElfType() == Elf.ET_DYN || b.getElfType() == Elf.ET_EXEC;
    }

    static class Sec {
        String name; int type; long flags; int link; long info; long entsize; long addr, offset, size; int nameOff;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Sec(String n, int t) { name = n; type = t; }
        byte[] bytes() { return buf.toByteArray(); }
    }
    static class Strtab {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Map<String, Integer> cache = new HashMap<>();
        Strtab() { out.write(0); }
        int add(String s) {
            Integer c = cache.get(s);
            if (c != null) return c;
            int off = out.size(); cache.put(s, off);
            out.writeBytes(s.getBytes(StandardCharsets.UTF_8)); out.write(0);
            return off;
        }
        byte[] bytes() { return out.toByteArray(); }
    }

    static void w16(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >>> 8) & 0xff); }
    static void w32(ByteArrayOutputStream o, long v) { for (int i = 0; i < 4; i++) o.write((int) (v >>> (8 * i)) & 0xff); }
    static void w64(ByteArrayOutputStream o, long v) { for (int i = 0; i < 8; i++) o.write((int) (v >>> (8 * i)) & 0xff); }

    public byte[] emit() {
        planVersions();
        buildSectionPlan();
        assignIndices();
        orderSymbols();
        fillSections();
        return layout();
    }

    private void planVersions() {
        int nextDef = 1, nextNeed = 2;
        for (Sym s : b.getSymbols()) {
            if (s.ver == null) continue;
            if (s.verFile != null) {
                if (!needIndex.containsKey(s.ver)) {
                    needIndex.put(s.ver, nextNeed);
                    needFile.put(s.ver, s.verFile);
                    nextNeed++;
                }
                needWeak.put(s.ver, Boolean.TRUE.equals(needWeak.getOrDefault(s.ver, false)) || s.verWeak);
            } else {
                if (!defIndex.containsKey(s.ver)) { defIndex.put(s.ver, nextDef); nextDef++; }
            }
        }
    }

    private void buildSectionPlan() {
        sections.add(null); // SHT_NULL
        text = add(".text", 1, Elf.SHF_ALLOC | 0x4);
        data = add(".data", 1, Elf.SHF_ALLOC | Elf.SHF_WRITE);
        Set<String> sigs = new LinkedHashSet<>();
        for (Sym s : b.getSymbols()) if (s.group != null) sigs.add(s.group);
        for (String sig : sigs) {
            Sec c = add(".text." + sig, 1, Elf.SHF_ALLOC | 0x4 | Elf.SHF_GROUP);
            comdats.add(c); comdatByName.put(sig, c);
            groups.put(sig, add(".group", Elf.SHT_GROUP, 0));
        }
        if (image) {
            dynsec = add(".dynamic", Elf.SHT_DYNAMIC, Elf.SHF_ALLOC | Elf.SHF_WRITE);
            dynsec.entsize = 16;
            dynsym = add(".dynsym", Elf.SHT_DYNSYM, Elf.SHF_ALLOC);
            dynsym.entsize = 24;
            dynstrSec = add(".dynstr", Elf.SHT_STRTAB, 0);
            dynversym = add(".gnu.version", Elf.SHT_GNU_versym, Elf.SHF_ALLOC);
            dynversym.entsize = 2;
        }
        if (!defIndex.isEmpty()) verdef = add(".gnu.version_d", Elf.SHT_GNU_verdef, 0);
        if (!needIndex.isEmpty()) verneed = add(".gnu.version_r", Elf.SHT_GNU_verneed, 0);
        symtab = add(".symtab", Elf.SHT_SYMTAB, 0);
        symtab.entsize = 24;
        symstrSec = add(".strtab", Elf.SHT_STRTAB, 0);
        // relocation sections
        Set<Integer> usedTargets = new TreeSet<>();
        for (Rel r : b.getRels()) usedTargets.add(targetIndex(r.target));
        for (Integer t : usedTargets) add(".rela" + sections.get(t).name, Elf.SHT_RELA, 0);
        shstrSec = add(".shstrtab", Elf.SHT_STRTAB, 0);
    }

    private Sec add(String n, int t, long f) { Sec s = new Sec(n, t); s.flags = f; sections.add(s); return s; }

    private int targetIndex(int target) {
        if (target == FixtureBuilder.SEC_DATA) return sections.indexOf(data);
        if (target == FixtureBuilder.SEC_TEXT) return sections.indexOf(text);
        return sections.indexOf(text);
    }

    private void assignIndices() {
        for (int i = 0; i < sections.size(); i++) if (sections.get(i) != null) sections.get(i).index = i;
    }

    private int shndx(Sym s) {
        if (s.undefined || s.section == FixtureBuilder.SEC_UNDEF) return Elf.SHN_UNDEF;
        if (s.section == FixtureBuilder.SEC_ABS) return Elf.SHN_ABS;
        if (s.section == FixtureBuilder.SEC_COMMON) return Elf.SHN_COMMON;
        if (s.group != null) return sections.indexOf(comdatByName.get(s.group));
        if (s.section == FixtureBuilder.SEC_DATA) return sections.indexOf(data);
        return sections.indexOf(text);
    }

    private void orderSymbols() {
        // locals first, then globals/weak, matching ELF st_info conventions.
        List<Sym> locals = new ArrayList<>();
        List<Sym> globals = new ArrayList<>();
        for (Sym s : b.getSymbols()) (s.bind == Elf.STB_LOCAL ? locals : globals).add(s);
        statics.clear(); statics.addAll(locals); statics.addAll(globals);
        int i = 0;
        for (Sym s : statics) staticPos.put(s, i++);
        symtab.info = locals.size();
        if (image) {
            List<Sym> dl = new ArrayList<>(), dg = new ArrayList<>();
            for (Sym s : b.getSymbols()) {
                if (s.dynamic || (image && (s.verFile != null || s.ver != null))) {
                    (s.bind == Elf.STB_LOCAL ? dl : dg).add(s);
                }
            }
            dyns.clear(); dyns.addAll(dl); dyns.addAll(dg);
            int j = 0;
            for (Sym s : dyns) dynPos.put(s, j++);
            dynsym.info = dl.size();
        }
    }

    private void fillSections() {
        for (Sec c : comdats) pad(c.buf, 8);
        pad(text.buf, 16);
        pad(data.buf, 16);
        fillStrtabs();
        fillSymtab(symtab.buf, statics, staticPos, symstr, false);
        if (image) {
            fillSymtab(dynsym.buf, dyns, dynPos, dynstr, true);
            fillVersym();
            fillDynamic();
        }
        fillVerdef();
        fillVerneed();
        fillGroups();
        fillRela();
    }

    private void pad(ByteArrayOutputStream o, int n) {
        while (o.size() < n) o.write(0);
    }

    private void fillStrtabs() {
        for (Sec s : sections) {
            if (s != null) s.nameOff(shstr.add(s.name));
        }
        for (Sym s : statics) s._nameOff = symstr.add(s.name);
        for (Sym s : dyns) s._dynNameOff = dynstr.add(s.name);
        symstrSec.buf.writeBytes(symstr.bytes());
        if (dynstrSec != null) {
            if (b.getSoname() != null) dynstr.add(b.getSoname());
            for (String f : needFile.values()) dynstr.add(f);
            dynstrSec.buf.writeBytes(dynstr.bytes());
        }
        shstrSec.buf.writeBytes(shstr.bytes());
    }

    private void fillSymtab(ByteArrayOutputStream out, List<Sym> list, Map<Sym, Integer> pos, Strtab str, boolean dynamic) {
        w64(out, 0); w16(out, 0); w16(out, 0); w16(out, 0); w16(out, 0); w64(out, 0); w64(out, 0);
        for (Sym s : list) {
            int nameOff = dynamic ? s._dynNameOff : s._nameOff;
            int shndx = shndx(s);
            long value = s.value;
            long size = s.size;
            if (shndx == Elf.SHN_COMMON && b.getElfType() == Elf.ET_REL) {
                value = s.value == 0 ? 1 : s.value; // alignment
                size = s.size;
            }
            w32(out, nameOff);
            out.write(Elf.stInfo(s.bind, s.type));
            out.write(s.vis & 3);
            w16(out, shndx);
            w64(out, value);
            w64(out, size);
        }
    }

    private int versionRaw(Sym s) {
        if (s.ver == null) return 0;
        int idx;
        if (s.verFile != null) idx = needIndex.getOrDefault(s.ver, 1);
        else idx = defIndex.getOrDefault(s.ver, 1);
        if (!s.verDefault) idx |= Elf.VERSYM_HIDDEN;
        return idx;
    }

    private void fillVersym() {
        w16(dynversym.buf, 0); // symbol 0
        for (Sym s : dyns) w16(dynversym.buf, s.ver == null ? 1 : versionRaw(s));
        dynversym.link = sections.indexOf(dynsym);
    }

    private void fillDynamic() {
        ByteArrayOutputStream o = dynsec.buf;
        dynsec.link = sections.indexOf(dynstrSec);
        if (b.getSoname() != null) {
            w64(o, Elf.DT_SONAME); w64(o, dynstr.cache.get(b.getSoname()));
        }
        if (verdef != null) {
            w64(o, Elf.DT_VERDEF); w64(o, 0); // address unused by parser
            w64(o, Elf.DT_VERDEFNUM); w64(o, defIndex.size());
        }
        if (verneed != null) {
            w64(o, Elf.DT_VERNEED); w64(o, 0);
            w64(o, Elf.DT_VERNEEDNUM); w64(o, needFile.size());
        }
        w64(o, Elf.DT_NULL); w64(o, 0);
    }

    private void fillVerdef() {
        if (verdef == null) return;
        verdef.link = sections.indexOf(dynstrSec == null ? symstrSec : dynstrSec);
        ByteArrayOutputStream o = verdef.buf;
        int vdSize = 20, auxSize = 20;
        long recStart = 0;
        for (Map.Entry<String, Integer> e : defIndex.entrySet()) {
            long base = o.size();
            w16(o, 1);                 // vd_version
            w16(o, 0);                 // flags (non-base definition)
            w16(o, 1);                 // vd_ndx
            w16(o, 1);                 // vd_cnt (one aux)
            w32(o, 0xffffffff);        // vd_hash
            w32(o, vdSize);            // vd_aux offset
            w32(o, 0);                 // vd_next (last)
            w32(o, e.getValue());      // vd_ndx real index stored again below?
            // Layout used: vd_ndx field at offset 8; last w32 above sits at 16 -> fix by writing index at 16 too
            // aux
            w32(o, dynstr.add(e.getKey())); // vda_name
            w32(o, 0);                       // vda_next
            w32(o, 0xffffffff);              // vda_hash
            w32(o, 0); w32(o, 0);            // padding
        }
    }

    private void fillVerneed() {
        if (verneed == null) return;
        verneed.link = sections.indexOf(dynstrSec == null ? symstrSec : dynstrSec);
        ByteArrayOutputStream o = verneed.buf;
        Map<String, List<String>> byFile = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : needFile.entrySet())
            byFile.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        for (Map.Entry<String, List<String>> f : byFile.entrySet()) {
            long vnStart = o.size();
            w16(o, 1);                          // vn_version
            w16(o, f.getValue().size());        // vn_cnt
            w32(o, 0xffffffff);                 // vn_file hash
            w32(o, dynstr.add(f.getKey()));     // vn_file
            w32(o, 16);                         // vn_aux
            w32(o, 0);                          // vn_next (last)
            long auxStart = o.size();
            int ai = 0;
            for (String vname : f.getValue()) {
                w16(o, Boolean.TRUE.equals(needWeak.get(vname)) ? Elf.VER_FLG_WEAK : 0);
                w16(o, needIndex.get(vname));
                w32(o, 0xffffffff);
                w32(o, dynstr.add(vname));
                w32(o, ai + 1 < f.getValue().size() ? 16 : 0);
                ai++;
            }
        }
    }

    private void fillGroups() {
        for (Map.Entry<String, Sec> e : groups.entrySet()) {
            Sec g = e.getValue();
            g.link = sections.indexOf(symtab);
            ByteArrayOutputStream o = g.buf;
            w32(o, 1); // GRP_COMDAT
            // signature symbol = first group symbol in static ordering
            Sym sig = null;
            for (Sym s : statics) if (e.getKey().equals(s.group)) { sig = s; break; }
            w32(o, sig == null ? 0 : staticPos.get(sig));
            w32(o, sections.indexOf(comdatByName.get(e.getKey())));
            g.entsize = 4;
        }
    }

    private void fillRela() {
        if (b.getRels().isEmpty()) return;
        // group rela sections by target section name
        Map<Integer, List<Rel>> byTarget = new LinkedHashMap<>();
        for (Rel r : b.getRels()) byTarget.computeIfAbsent(targetIndex(r.target), k -> new ArrayList<>()).add(r);
        for (Map.Entry<Integer, List<Rel>> e : byTarget.entrySet()) {
            Sec rs = null;
            for (Sec s : sections) if (s != null && s.type == Elf.SHT_RELA && s.name.equals(".rela" + sections.get(e.getKey()).name)) rs = s;
            if (rs == null) continue;
            rs.link = sections.indexOf(symtab);
            rs.info = e.getKey();
            rs.entsize = 24;
            for (Rel r : e.getValue()) {
                long info = ((long) staticPos.get(r.sym) << 32) | (r.type & 0xffffffffL);
                w64(rs.buf, r.offset);
                w64(rs.buf, info);
                w64(rs.buf, r.addend);
            }
        }
    }
