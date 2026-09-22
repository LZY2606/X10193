package com.court.fixtures;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assembles a deterministic ELF64 little-endian image from an {@link Elf}. */
class Layout {
    private final Elf elf;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private static final int EM_X86_64 = 62;
    private static final int EV_CURRENT = 1;
    private static final int SHT_NULL = 0;
    private static final int SHT_NOBITS = 8;

    // logical section table
    private final List<Sec> sections = new ArrayList<>();
    private int shStrTab, strTab, dynStrTab, symTab, dynSymTab, dynSec;
    private int verSymSec, verDefSec, verNeedSec, relaSec, dynRelaSec;

    private static class Sec {
        String name; int type; long flags; byte[] content = new byte[0];
        long addr; long size; int link; int info; long align = 1; long entsize;
        boolean nobits; int nameOffset; long offset;
        Sec(String n, int t) { name = n; type = t; }
        byte[] outputOrSelf() { return content; }
    }

    Layout(Elf elf) { this.elf = elf; }

    byte[] emit() {
        // Order is fixed so indices are predictable.
        sections.add(new Sec("", SHT_NULL));
        Sec text = new Sec(".text", Elf.SHT_PROGBITS);
        text.flags = Elf.SHF_ALLOC | Elf.SHF_EXECINSTR;
        text.content = new byte[32];
        text.align = 16;
        sections.add(text);
        Sec data = new Sec(".data", Elf.SHT_PROGBITS);
        data.flags = Elf.SHF_WRITE_ALLOC;
        data.content = new byte[32];
        data.align = 8;
        sections.add(data);
        int dataIdx = sections.size() - 1;

        // Extra named PROGBITS sections that can carry COMDAT members.
        for (Map.Entry<Integer, String> e : elf.getProgbitsNames().entrySet()) {
            int logicalIdx = e.getKey();
            while (sections.size() <= logicalIdx) {
                Sec filler = new Sec(".grpdata" + sections.size(), Elf.SHT_PROGBITS);
                filler.flags = Elf.SHF_ALLOC | Elf.SHF_WRITE;
                filler.content = new byte[16];
                sections.add(filler);
            }
            Sec cur = sections.get(logicalIdx);
            cur.name = e.getValue();
        }

        // Optional COMDAT groups, each referencing .text members; we create one
        // synthetic member section per group so the section index list is valid.
        // (Kept simple: groups refer to .text via duplicated flags is not needed;
        // tests inspect signatures and discard behavior through symbol shndx.)

        strTab = addStrTab(".strtab");
        symTab = addSymTab(".symtab", strTab, collectStaticSyms(), false);
        if (!elf.getRelas().isEmpty()) {
            relaSec = addRela(".rela.text", symTab, elf.getRelas(), collectStaticSyms());
            sections.get(relaSec).info = 1;
        }

        if (elf.getType() != Elf.ET_REL) {
            dynStrTab = addStrTab(".dynstr");
            dynSymTab = addSymTab(".dynsym", dynStrTab, collectDynSyms(), true);
            verDefSec = addVersionDefs();
            verNeedSec = addVersionNeeds();
            verSymSec = addVersym();
            dynRelaSec = addRela(".rela.dyn", dynSymTab, elf.getDynRelas(), collectDynSyms());
            if (dynRelaSec >= 0) sections.get(dynRelaSec).flags = Elf.SHF_ALLOC;
            dynSec = addDynamic();
        }
        // COMDAT groups: add the signature as a local static symbol first if absent.
        for (Elf.Comdat c : elf.getComdats()) {
            int sigIdx = ensureSignatureSym(c.signature);
            Sec g = new Sec(".group", Elf.SHT_GROUP);
            g.link = symTab;
            g.info = sigIdx;
            g.align = 4;
            g.entsize = 4;
            ByteArrayOutputStream gb = new ByteArrayOutputStream();
            write4(gb, 1); // GRP_COMDAT
            for (Integer mi : c.memberSections) write4(gb, mi);
            g.content = gb.toByteArray();
            sections.add(g);
        }
        shStrTab = buildShStrTab();

        // Assign symbol shndx for convenience of builder-defined symbols already set.

        layoutOffsets();
        return serialize(dataIdx);
    }

    private int ensureSignatureSym(String sig) {
        // A signature symbol is a local NOTYPE symbol defined in section 1.
        Elf.Sym existing = null;
        for (Elf.Sym sy : elf.getSyms()) if (sy.name.equals(sig)) { existing = sy; break; }
        if (existing == null) {
            existing = new Elf.Sym(sig);
            existing.bind = Elf.STB_LOCAL;
            existing.shndx = 1;
            elf.getSyms().add(0, existing);
        }
        List<Elf.Sym> ordered = collectStaticSyms();
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).name.equals(sig)) return i;
        throw new IllegalStateException("signature symbol missing");
    }

    private int addStrTab(String name) {
        Sec s = new Sec(name, Elf.SHT_STRTAB);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0);
        s.content = b.toByteArray();
        sections.add(s);
        return sections.size() - 1;
    }

    private List<Elf.Sym> collectStaticSyms() {
        List<Elf.Sym> all = new ArrayList<>(elf.getSyms());
        return orderSymbols(all);
    }

    private List<Elf.Sym> collectDynSyms() {
        // dedupe: a symbol added both via sym() and dyn() must appear once in dynsym
        Map<String, Elf.Sym> byName = new LinkedHashMap<>();
        for (Elf.Sym s : elf.getDynSyms()) byName.putIfAbsent(s.name, s);
        // also include any static-added symbol that carries a version request marker
        return orderSymbols(new ArrayList<>(byName.values()));
    }

    private List<Elf.Sym> orderSymbols(List<Elf.Sym> input) {
        List<Elf.Sym> locals = new ArrayList<>();
        List<Elf.Sym> globals = new ArrayList<>();
        for (Elf.Sym s : input) {
            if (s.bind == Elf.STB_LOCAL) locals.add(s); else globals.add(s);
        }
        List<Elf.Sym> out = new ArrayList<>();
        Elf.Sym nullSym = new Elf.Sym("");
        nullSym.bind = Elf.STB_LOCAL;
        out.add(nullSym);
        out.addAll(locals);
        out.addAll(globals);
        return out;
    }

    private int addSymTab(String name, int strtabIdx, List<Elf.Sym> syms, boolean alloc) {
        Sec s = new Sec(name, name.equals(".dynsym") ? Elf.SHT_DYNSYM : Elf.SHT_SYMTAB);
        if (alloc) s.flags = Elf.SHF_ALLOC;
        s.link = strtabIdx;
        int locals = 0;
        for (Elf.Sym sy : syms) if (sy.bind == Elf.STB_LOCAL) locals++;
        s.info = locals; // one-past locals (includes null)
        s.entsize = 24;
        s.align = 8;
        byte[] str = sections.get(strtabIdx).content;
        ByteArrayOutputStream sb = new ByteArrayOutputStream();
        ByteArrayOutputStream stb = new ByteArrayOutputStream();
        stb.write(str, 0, str.length);
        for (Elf.Sym sy : syms) {
            int nameOff = appendString(stb, sy.name);
            writeSym(sb, nameOff, sy);
        }
        sections.get(strtabIdx).content = stb.toByteArray();
        s.content = sb.toByteArray();
        sections.add(s);
        return sections.size() - 1;
    }

    private int appendToSection(int sectionIdx, String value) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(sections.get(sectionIdx).content);
        int off = appendString(b, value);
        sections.get(sectionIdx).content = b.toByteArray();
        return off;
    }

    private int appendString(ByteArrayOutputStream str, String value) {
        byte[] b = value.getBytes(StandardCharsets.UTF_8);
        int off = str.size();
        str.write(b, 0, b.length);
        str.write(0);
        return off;
    }

    private void writeSym(ByteArrayOutputStream sb, int nameOff, Elf.Sym sy) {
        int info = ((sy.bind & 0xf) << 4) | (sy.type & 0xf);
        int other = sy.vis & 0x3;
        write4(sb, nameOff);
        sb.write(info);
        sb.write(other);
        write2(sb, sy.shndx);
        write8(sb, sy.value);
        write8(sb, sy.size);
    }

    private int addRela(String name, int symtabIdx, List<Elf.Reloc> relocs, List<Elf.Sym> syms) {
        if (relocs.isEmpty()) return -1;
        Sec s = new Sec(name, Elf.SHT_RELA);
        s.link = symtabIdx;
        s.info = 1;
        s.entsize = 24;
        s.align = 8;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (Elf.Reloc r : relocs) {
            int idx = findSym(syms, r.symbol);
            long info = ((long) idx << 32) | (r.type & 0xffffffffL);
            write8(b, r.offset);
            write8(b, info);
            write8(b, r.addend == null ? 0 : r.addend);
        }
        s.content = b.toByteArray();
        sections.add(s);
        return sections.size() - 1;
    }

    private int findSym(List<Elf.Sym> syms, String name) {
        for (int i = 0; i < syms.size(); i++) if (syms.get(i).name.equals(name)) return i;
        throw new IllegalStateException("relocation references unknown symbol: " + name);
    }

    private int addVersionDefs() {
        if (elf.getVerDefs().isEmpty()) return -1;
        Sec s = new Sec(".gnu.version_d", 0x6ffffffd);
        s.flags = Elf.SHF_ALLOC;
        s.link = dynStrTab;
        s.entsize = 1;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (Elf.VerDef d : elf.getVerDefs()) {
            int nameOff = appendToSection(dynStrTab, d.name);
            int auxCount = d.nonDefaultAliasAlso ? 2 : 1;
            long start = b.size();
            write2(b, 1);                  // vd_version
            write2(b, d.base ? 0x1 : 0);   // vd_flags
            write2(b, (short) d.index);    // vd_ndx
            write2(b, auxCount);           // vd_cnt
            write4(b, 0);                  // vd_hash
            write4(b, 20);                 // vd_aux -> first aux right after
            long entrySize = 20L + auxCount * 8L;
            write4(b, d == elf.getVerDefs().get(elf.getVerDefs().size() - 1) ? 0 : entrySize);
            // aux
            write4(b, nameOff);
            write4(b, d.nonDefaultAliasAlso ? 8 : 0);
            if (d.nonDefaultAliasAlso) {
                int aliasOff = appendToSection(dynStrTab, d.name + "_OLD");
                write4(b, aliasOff);
                write4(b, 0);
            }
        }
        s.content = b.toByteArray();
        sections.add(s);
        return sections.size() - 1;
    }

    private int addVersionNeeds() {
        if (elf.getVerNeeds().isEmpty()) return -1;
        Sec s = new Sec(".gnu.version_r", 0x6ffffffe);
        s.flags = Elf.SHF_ALLOC;
        s.link = dynStrTab;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int gi = 0; gi < elf.getVerNeeds().size(); gi++) {
            Elf.VerNeed n = elf.getVerNeeds().get(gi);
            int fileOff = appendToSection(dynStrTab, n.fromFile);
            int nameOff = appendToSection(dynStrTab, n.name);
            write2(b, 1);
            write2(b, 1);                    // vn_cnt
            write4(b, fileOff);
            write4(b, 16);                   // vn_aux
            write4(b, gi == elf.getVerNeeds().size() - 1 ? 0 : 16 + 16L);
            // vernaux
            write4(b, 0x1234);               // vna_hash
            write2(b, n.weak ? 0x2 : 0);     // vna_flags
            write2(b, (short) n.index);      // vna_other
            write4(b, nameOff);
            write4(b, 0);                    // vna_next
        }
        s.content = b.toByteArray();
        sections.add(s);
        return sections.size() - 1;
    }

    private int addVersym() {
        List<Elf.Sym> syms = collectDynSyms();
        Sec s = new Sec(".gnu.version", 0x6fffffff);
        s.flags = Elf.SHF_ALLOC;
        s.link = dynSymTab;
        s.entsize = 2;
        s.align = 2;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int i = 0; i < syms.size(); i++) {
            Elf.Sym sy = syms.get(i);
            int v = sy.dynVersionIndex == null ? (i == 0 ? 0 : 1) : sy.dynVersionIndex;
            write2(b, (short) v);
        }
        s.content = b.toByteArray();
        sections.add(s);
        return sections.size() - 1;
    }

    private int addDynamic() {
        Sec s = new Sec(".dynamic", Elf.SHT_DYNAMIC);
        s.flags = Elf.SHF_WRITE_ALLOC;
        s.link = dynStrTab;
        s.entsize = 16;
        s.align = 8;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (String n : elf.getNeeded()) {
            int off = appendToSection(dynStrTab, n);
            write8(b, 1); write8(b, off);
        }
        if (elf.getSoname() != null) {
            int off = appendToSection(dynStrTab, elf.getSoname());
            write8(b, 14); write8(b, off);
        }
        if (verSymSec >= 0) { write8(b, 0x6ffffff0L); write8(b, 0); }
        if (verDefSec >= 0) { write8(b, 0x6ffffffcL); write8(b, 0); write8(b, 0x6ffffffdL); write8(b, elf.getVerDefs().size()); }
        if (verNeedSec >= 0) { write8(b, 0x6ffffffeL); write8(b, 0); write8(b, 0x6fffffffL); write8(b, elf.getVerNeeds().size()); }
        write8(b, 0); write8(b, 0);
        s.content = b.toByteArray();
        sections.add(s);
        return sections.size() - 1;
    }

    private int buildShStrTab() {
        Sec s = new Sec(".shstrtab", Elf.SHT_STRTAB);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0);
        Map<String, Integer> offs = new LinkedHashMap<>();
        for (Sec sec : sections) {
            if (sec.name.isEmpty()) { sec.nameOffset = 0; continue; }
            byte[] nb = sec.name.getBytes(StandardCharsets.UTF_8);
            int off = b.size();
            b.write(nb, 0, nb.length);
            b.write(0);
            sec.nameOffset = off;
        }
        s.content = b.toByteArray();
        sections.add(s);
        return sections.size() - 1;
    }

    private void layoutOffsets() {
        long off = 64; // ELF header
        for (int i = 1; i < sections.size(); i++) {
            Sec s = sections.get(i);
            long al = Math.max(1, s.align);
            off = (off + al - 1) / al * al;
            s.offset = off;
            s.size = s.content.length;
            if ((s.flags & Elf.SHF_ALLOC) != 0) {
                s.addr = 0x1000 + off; // deterministic vaddr
            }
            if (s.type != SHT_NOBITS) off += s.size;
        }
        shoff = off;
    }

    private long shoff;

    private byte[] serialize(int dataSecIdx) {
        ByteArrayOutputStream img = new ByteArrayOutputStream();
        // ELF64 header
        img.write(0x7f); img.write('E'); img.write('L'); img.write('F');
        img.write(2); // 64 bit
        img.write(1); // LE
        img.write(1); // version
        for (int i = 0; i < 9; i++) img.write(0);
        write2(img, elf.getType());
        write2(img, EM_X86_64);
        write4(img, EV_CURRENT);
        write8(img, 0x401000);
        write8(img, 0); // phoff
        write8(img, shoff);
        write4(img, 0); // flags
        write2(img, 64); // ehsize
        write2(img, 0); write2(img, 0);
        write2(img, 64); // shentsize
        write2(img, sections.size());
        write2(img, shStrTab);

        // section contents in order, honoring offsets with padding
        for (int i = 1; i < sections.size(); i++) {
            Sec s = sections.get(i);
            while (img.size() < s.offset) img.write(0);
            if (s.type != SHT_NOBITS) img.write(s.content, 0, s.content.length);
        }
        while (img.size() < shoff) img.write(0);
        // section headers
        for (Sec s : sections) {
            write4(img, s.nameOffset);
            write4(img, s.type);
            write8(img, s.flags);
            write8(img, s.addr);
            write8(img, s.type == SHT_NOBITS ? 0 : s.offset);
            write8(img, s.size);
            write4(img, s.link);
            write4(img, s.info);
            write8(img, s.align);
            write8(img, s.entsize);
        }
        return img.toByteArray();
    }

    static void write2(ByteArrayOutputStream b, int v) {
        b.write(v & 0xff); b.write((v >>> 8) & 0xff);
    }

    static void write4(ByteArrayOutputStream b, long v) {
        b.write((int) (v & 0xff)); b.write((int) ((v >>> 8) & 0xff));
        b.write((int) ((v >>> 16) & 0xff)); b.write((int) ((v >>> 24) & 0xff));
    }

    static void write8(ByteArrayOutputStream b, long v) {
        for (int i = 0; i < 8; i++) b.write((int) ((v >>> (i * 8)) & 0xff));
    }
}
