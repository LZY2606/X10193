package court.symbol.fixture;

import court.symbol.elf.Elf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits a small ELF64 LE file from fixture symbols. Layout is assembled in two
 * passes: first gather section byte payloads, then write header + aligned
 * section bodies + section header table.
 */
final class ElfAssembler {

    private static final int SHN = 1; // .text
    private static final int ADDR_ALIGN = 8;

    private ElfAssembler() {
    }

    static byte[] assemble(int etype, int machine, List<ElfFixture.Sym> symbols,
                           int localCount, Map<Integer, List<ElfFixture.Rela>> relas,
                           List<ElfFixture.VerDef> verDefs,
                           List<ElfFixture.VerNeed> verNeeds,
                           List<String> needed, String soname, String comdat) {

        boolean dyn = etype == Elf.ET_DYN;

        // ---- string tables
        StrTab shstr = new StrTab();
        shstr.intern("");
        int nText = shstr.intern(".text");
        int nData = shstr.intern(".data");
        int nBss = shstr.intern(".bss");
        int nSymtab = shstr.intern(".symtab");
        int nStrtab = shstr.intern(".strtab");
        int nRelaText = shstr.intern(".rela.text");
        int nGroup = shstr.intern(".group");
        int nDynsym = shstr.intern(".dynsym");
        int nDynstr = shstr.intern(".dynstr");
        int nDynamic = shstr.intern(".dynamic");
        int nVerdef = shstr.intern(".gnu.version_d");
        int nVerneed = shstr.intern(".gnu.version_r");
        int nVersym = shstr.intern(".gnu.version");
        int nHash = shstr.intern(".gnu.hash");

        StrTab strtab = new StrTab();
        strtab.intern("");
        StrTab dynstr = new StrTab();
        dynstr.intern("");
        int dynSonameOff = soname == null ? -1 : dynstr.intern(soname);
        List<Integer> neededOffsets = new ArrayList<>();
        for (String n : needed) {
            neededOffsets.add(dynstr.intern(n));
        }
        for (ElfFixture.VerNeed vn : verNeeds) {
            dynstr.intern(vn.fromSoname()); // may already exist
        }

        int[] nameOff = new int[symbols.size()];
        for (int i = 0; i < symbols.size(); i++) {
            ElfFixture.Sym s = symbols.get(i);
            if (s.name == null || s.name.isEmpty()) {
                nameOff[i] = 0;
            } else {
                nameOff[i] = dyn ? dynstr.intern(s.name) : strtab.intern(s.name);
            }
        }
        int groupSigName = comdat == null ? -1 : strtab.intern(comdat);

        // ---- section list (index 0 = null)
        List<Sec> sections = new ArrayList<>();
        sections.add(null);
        int textIdx = sections.size();
        byte[] textBody = new byte[]{(byte) 0xc3}; // ret
        sections.add(new Sec(".text", Elf.SHT_PROGBITS, Elf.SHF_ALLOC | Elf.SHF_EXECINSTR,
                textBody, 16, 1));
        int dataIdx = sections.size();
        sections.add(new Sec(".data", Elf.SHT_PROGBITS, Elf.SHF_ALLOC | Elf.SHF_WRITE,
                new byte[]{1, 2, 3, 4}, 16, 1));

        int symtabIdx = -1;
        int strtabIdx = -1;
        int dynsymIdx = -1;
        int dynstrIdx = -1;
        int groupIdx = -1;

        // COMDAT group section (SHT_GROUP) refers to .text
        int groupSigSymIndex = -1;
        if (comdat != null) {
            ElfFixture.Sym gs = ElfFixture.Sym.def(comdat, Elf.SHN_UNDEF);
            gs.type = Elf.STT_OBJECT;
            // signature symbol typically STT_NOTYPE local in .symtab
            gs = gs.local();
            symbols.add(localCount, gs);
            groupSigSymIndex = localCount;
            localCount++;
            // shift name offsets array by 1 (local inserted at groupSigSymIndex)
            int[] shifted = new int[nameOff.length + 1];
            System.arraycopy(nameOff, 0, shifted, 0, groupSigSymIndex);
            shifted[groupSigSymIndex] = groupSigName;
            System.arraycopy(nameOff, groupSigSymIndex, shifted,
                    groupSigSymIndex + 1, nameOff.length - groupSigSymIndex);
            nameOff = shifted;
            byte[] groupBody = buildGroupBody(textIdx);
            groupIdx = sections.size();
            sections.add(new Sec(".group", Elf.SHT_GROUP, 0, groupBody, 4, 4));
            // mark .text as member of a group (SHF_GROUP)
            Sec textSec = sections.get(textIdx);
            sections.set(textIdx, new Sec(".text", Elf.SHT_PROGBITS,
                    Elf.SHF_ALLOC | Elf.SHF_EXECINSTR | Elf.SHF_GROUP,
                    textBody, 16, 1));
        }

        // symbol table (dynsym for ET_DYN, otherwise symtab)
        byte[] symBody = buildSymtab(symbols, nameOff);
        List<Integer> relaSectionIdx = new ArrayList<>();
        for (Map.Entry<Integer, List<ElfFixture.Rela>> e : relas.entrySet()) {
            byte[] body = buildRela(e.getValue());
            int idx = sections.size();
            sections.add(new Sec(".rela.text", Elf.SHT_RELA, 0, body, 8, 24));
            relaSectionIdx.add(idx);
            // back-link info set later
        }

        if (dyn) {
            dynstrIdx = sections.size();
            sections.add(new Sec(".dynstr", Elf.SHT_STRTAB, 0, dynstr.bytes(), 1, 0));
            dynsymIdx = sections.size();
            sections.add(new Sec(".dynsym", Elf.SHT_DYNSYM, Elf.SHF_ALLOC,
                    symBody, 8, 24));
            // minimal .gnu.hash-like SHT_PROGBITS placeholder is unnecessary;
            // add a real-ish GNU_HASH with nbuckets=0 is complex, skip (parser tolerates).
        } else {
            strtabIdx = sections.size();
            sections.add(new Sec(".strtab", Elf.SHT_STRTAB, 0, strtab.bytes(), 1, 0));
            symtabIdx = sections.size();
            sections.add(new Sec(".symtab", Elf.SHT_SYMTAB, 0, symBody, 8, 24));
        }

        // version sections (dyn only)
        int verdefIdx = -1;
        int verneedIdx = -1;
        int versymIdx = -1;
        if (dyn) {
            if (!verDefs.isEmpty()) {
                byte[] body = buildVerdef(verDefs, dynstr);
                verdefIdx = sections.size();
                sections.add(new Sec(".gnu.version_d", Elf.SHT_GNU_VERDEF,
                        Elf.SHF_ALLOC, body, 8, 20));
            }
            if (!verNeeds.isEmpty()) {
                byte[] body = buildVerneed(verNeeds, dynstr);
                verneedIdx = sections.size();
                sections.add(new Sec(".gnu.version_r", Elf.SHT_GNU_VERNEED,
                        Elf.SHF_ALLOC, body, 8, 16));
            }
            byte[] versymBody = buildVersym(symbols);
            versymIdx = sections.size();
            sections.add(new Sec(".gnu.version", Elf.SHT_GNU_VERSYM,
                    Elf.SHF_ALLOC, versymBody, 2, 2));

            // .dynamic section: entries for NEEDED / SONAME / version counts
            byte[] dynamicBody = buildDynamic(neededOffsets, dynSonameOff,
                    verDefs.isEmpty() ? -1 : verdefIdx,
                    verNeeds.isEmpty() ? -1 : verneedIdx,
                    verDefs.size(), verNeeds.size(),
                    dynstrIdx, dynsymIdx, versymIdx);
            sections.add(new Sec(".dynamic", Elf.SHT_DYNAMIC,
                    Elf.SHF_ALLOC | Elf.SHF_WRITE, dynamicBody, 8, 16));
        }

        // section name string table goes last
        byte[] shstrBody = shstr.bytes();
        int shstrIdx = sections.size();
        sections.add(new Sec(".shstrtab", Elf.SHT_STRTAB, 0, shstrBody, 1, 0));

        // fix links/info
        if (dyn) {
            Sec ds = sections.get(dynsymIdx);
            sections.set(dynsymIdx, new Sec(ds.name, ds.type, ds.flags, ds.body,
                    ds.addrAlign, ds.entrySize, dynstrIdx, localCount));
            if (verdefIdx >= 0) {
                Sec v = sections.get(verdefIdx);
                sections.set(verdefIdx, new Sec(v.name, v.type, v.flags, v.body,
                        v.addrAlign, v.entrySize, dynstrIdx, 0));
            }
            if (verneedIdx >= 0) {
                Sec v = sections.get(verneedIdx);
                sections.set(verneedIdx, new Sec(v.name, v.type, v.flags, v.body,
                        v.addrAlign, v.entrySize, dynstrIdx, 0));
            }
            if (versymIdx >= 0) {
                Sec v = sections.get(versymIdx);
                sections.set(versymIdx, new Sec(v.name, v.type, v.flags, v.body,
                        v.addrAlign, v.entrySize, dynsymIdx, 0));
            }
        } else {
            Sec st = sections.get(symtabIdx);
            sections.set(symtabIdx, new Sec(st.name, st.type, st.flags, st.body,
                    st.addrAlign, st.entrySize, strtabIdx, localCount));
        }
        for (int i = 0; i < relaSectionIdx.size(); i++) {
            int ri = relaSectionIdx.get(i);
            Sec rs = sections.get(ri);
            int target = (int) relas.keySet().toArray()[i];
            int linkedSymtab = dyn ? dynsymIdx : symtabIdx;
            sections.set(ri, new Sec(rs.name, rs.type, rs.flags, rs.body,
                    rs.addrAlign, rs.entrySize, linkedSymtab, target));
        }
        if (groupIdx >= 0) {
            Sec g = sections.get(groupIdx);
            sections.set(groupIdx, new Sec(g.name, g.type, g.flags, g.body,
                    g.addrAlign, g.entrySize,
                    dyn ? dynsymIdx : symtabIdx, groupSigSymIndex));
            Sec textSec = sections.get(textIdx);
            sections.set(textIdx, new Sec(textSec.name, textSec.type, textSec.flags,
                    textSec.body, textSec.addrAlign, textSec.entrySize, 0, 0,
                    groupIdx, 0));
        }

        // assign section names from shstr
        for (int i = 1; i < sections.size(); i++) {
            Sec s = sections.get(i);
            int nameOff2 = shstr.offset(s.name);
            sections.set(i, new Sec(s.name, s.type, s.flags, s.body, s.addrAlign,
                    s.entrySize, s.link, s.info, s.group, nameOff2));
        }

        return emitElf(etype, machine, sections);
    }

    // -------------------------------------------------------------- helpers

    private static final class Sec {
        final String name;
        final int type;
        final long flags;
        final byte[] body;
        final long addrAlign;
        final long entrySize;
        final int link;
        final int info;
        final int group;
        final int nameIndex;

        Sec(String name, int type, long flags, byte[] body, long align, long entsize) {
            this(name, type, flags, body, align, entsize, 0, 0, 0, 0);
        }

        Sec(String name, int type, long flags, byte[] body, long align, long entsize,
            int link, int info) {
            this(name, type, flags, body, align, entsize, link, info, 0, 0);
        }

        Sec(String name, int type, long flags, byte[] body, long align, long entsize,
            int link, int info, int group, int nameIndex) {
            this.name = name;
            this.type = type;
            this.flags = flags;
            this.body = body;
            this.addrAlign = align;
            this.entrySize = entsize;
            this.link = link;
            this.info = info;
            this.group = group;
            this.nameIndex = nameIndex;
        }
    }

    private static final class StrTab {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final Map<String, Integer> offsets = new LinkedHashMap<>();

        StrTab() {
            buf.write(0);
        }

        int intern(String s) {
            Integer existing = offsets.get(s);
            if (existing != null) {
                return existing;
            }
            int off = buf.size();
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            buf.writeBytes(b);
            buf.write(0);
            offsets.put(s, off);
            return off;
        }

        int offset(String s) {
            return offsets.getOrDefault(s, 0);
        }

        byte[] bytes() {
            return buf.toByteArray();
        }
    }

    private static byte[] buildGroupBody(int... members) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        write4(b, 1); // GRP_COMDAT
        for (int m : members) {
            write4(b, m);
        }
        return b.toByteArray();
    }

    private static byte[] buildSymtab(List<ElfFixture.Sym> syms, int[] nameOffsets) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        // null symbol
        for (int i = 0; i < 24; i++) {
            b.write(0);
        }
        for (int i = 0; i < syms.size(); i++) {
            ElfFixture.Sym s = syms.get(i);
            int nameOff = i < nameOffsets.length ? nameOffsets[i] : 0;
            write4(b, nameOff);
            b.write((s.type & 0xf) | ((s.binding & 0xf) << 4));
            b.write(s.visibility & 0x3);
            write2(b, s.shndx);
            write8(b, s.value);
            write8(b, s.size);
        }
        return b.toByteArray();
    }

    private static byte[] buildRela(List<ElfFixture.Rela> relas) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (ElfFixture.Rela r : relas) {
            write8(b, r.offset());
            long info = ((long) r.symIndex() << 32) | (r.type() & 0xffffffffL);
            write8(b, info);
            write8(b, r.addend());
        }
        return b.toByteArray();
    }

    private static byte[] buildVersym(List<ElfFixture.Sym> syms) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        write2(b, 0); // index 0
        for (ElfFixture.Sym s : syms) {
            write2(b, s.versym & 0xffff);
        }
        return b.toByteArray();
    }

    private static byte[] buildVerdef(List<ElfFixture.VerDef> defs, StrTab dynstr) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        // Layout pass: Verdaux records follow each Verdef.
        long base = 0;
        List<Long> verdefOffsets = new ArrayList<>();
        long cursor = 0;
        for (int i = 0; i < defs.size(); i++) {
            verdefOffsets.add(cursor);
            ElfFixture.VerDef d = defs.get(i);
            int auxCount = 1 + d.aliases().size();
            cursor += 20; // verdef
            cursor += 8L * auxCount; // verdaux
        }
        cursor = 0;
        for (int i = 0; i < defs.size(); i++) {
            ElfFixture.VerDef d = defs.get(i);
            int auxCount = 1 + d.aliases().size();
            write2(b, 1);                 // vd_version
            write2(b, 0);                 // vd_flags (default)
            write2(b, d.ndx());
            write2(b, auxCount);
            write4(b, 0);                 // vd_hash
            write4(b, 20);                // vd_aux offset
            write4(b, i + 1 < defs.size() ? (20 + 8L * auxCount) : 0); // next
            // first aux
            write4(b, dynstr.offset(d.name()));
            write4(b, auxCount > 1 ? 8 : 0);
            for (int a = 0; a < d.aliases().size(); a++) {
                boolean last = a == d.aliases().size() - 1;
                write4(b, dynstr.offset(d.aliases().get(a)));
                write4(b, last ? 0 : 8);
            }
        }
        return b.toByteArray();
    }

    private static byte[] buildVerneed(List<ElfFixture.VerNeed> needs, StrTab dynstr) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        // group needs by soname in order
        Map<String, List<ElfFixture.VerNeed>> bySoname = new LinkedHashMap<>();
        for (ElfFixture.VerNeed n : needs) {
            bySoname.computeIfAbsent(n.fromSoname(), k -> new ArrayList<>()).add(n);
        }
        boolean first = true;
        for (Map.Entry<String, List<ElfFixture.VerNeed>> e : bySoname.entrySet()) {
            List<ElfFixture.VerNeed> list = e.getValue();
            int vernauxSize = 16;
            long nextVerneed = first ? 0 : 0; // compute later if needed
            // We emit each vernaux chained; single verneed block per soname.
            write2(b, 1);                 // vn_version
            write2(b, list.size());
            write4(b, dynstr.offset(e.getKey()));
            write4(b, 16);                // vn_aux
            write4(b, 0);                 // vn_next (no chained soname blocks)
            for (int i = 0; i < list.size(); i++) {
                ElfFixture.VerNeed n = list.get(i);
                write4(b, dynstr.offset(n.name())); // vna_hash placeholder uses name offset
                write4(b, i + 1 < list.size() ? 16 : 0); // vna_next
                write2(b, 0);
                write2(b, n.weak() ? Elf.VER_FLG_WEAK : 0);
                write2(b, n.ndx());
                write2(b, 0);             // vna_pad
            }
            first = false;
        }
        return b.toByteArray();
    }

    private static byte[] buildDynamic(List<Integer> neededOffsets, int sonameOff,
                                       int verdefIdx, int verneedIdx,
                                       int verdefNum, int verneedNum,
                                       int dynstrIdx, int dynsymIdx, int versymIdx) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int off : neededOffsets) {
            write8(b, Elf.DT_NEEDED);
            write8(b, off);
        }
        if (sonameOff >= 0) {
            write8(b, Elf.DT_SONAME);
            write8(b, sonameOff);
        }
        write8(b, 0x6ffffffb); // DT_FLAGS_1 placeholder (ignored by parser)
        write8(b, 0);
        if (verdefIdx >= 0) {
            write8(b, 0x6ffffffcL); // DT_VERDEFNUM? we also emit VERDEF pointer
            write8(b, verdefIdx);
            write8(b, Elf.DT_VERDEFNUM);
            write8(b, verdefNum);
        }
        if (verneedIdx >= 0) {
            write8(b, 0x6ffffffeL);
            write8(b, verneedIdx);
            write8(b, Elf.DT_VERNEEDNUM);
            write8(b, verneedNum);
        }
        write8(b, Elf.DT_NULL);
        write8(b, 0);
        return b.toByteArray();
    }

    private static byte[] emitElf(int etype, int machine, List<Sec> sections) {
        long shnum = sections.size();
        long headerSize = 64;

        // first pass: compute offsets
        long[] offsets = new long[(int) shnum];
        long[] addrs = new long[(int) shnum];
        long cursor = headerSize;
        long vaddr = 0;
        for (int i = 1; i < shnum; i++) {
            Sec s = sections.get(i);
            long align = Math.max(1, s.addrAlign);
            cursor = align(cursor, align);
            offsets[i] = cursor;
            cursor += s.body.length;
            if ((s.flags & Elf.SHF_ALLOC) != 0) {
                vaddr = align(vaddr, align);
                addrs[i] = vaddr;
                vaddr += s.body.length;
            }
        }
        cursor = align(cursor, 8);
        long shoff = cursor;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // e_ident
        out.writeBytes(new byte[]{0x7f, 'E', 'L', 'F', Elf.ELFCLASS64,
                Elf.ELFDATA2LSB, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        write2(out, etype);
        write2(out, machine);
        write4(out, 1);              // e_version
        write8(out, 0);              // e_entry
        write8(out, 0);              // e_phoff
        write8(out, shoff);          // e_shoff
        write4(out, 0);              // e_flags
        write2(out, 64);             // e_ehsize
        write2(out, 0);              // e_phentsize
        write2(out, 0);              // e_phnum
        write2(out, 64);             // e_shentsize
        write2(out, (int) shnum);
        write2(out, (int) shnum - 1); // e_shstrndx

        // section bodies
        for (int i = 1; i < shnum; i++) {
            while (out.size() < offsets[i]) {
                out.write(0);
            }
            out.writeBytes(sections.get(i).body);
        }
        while (out.size() < shoff) {
            out.write(0);
        }
        // section headers
        for (int i = 0; i < shnum; i++) {
            if (i == 0) {
                for (int z = 0; z < 64; z++) {
                    out.write(0);
                }
                continue;
            }
            Sec s = sections.get(i);
            write4(out, s.nameIndex);
            write4(out, s.type);
            write8(out, s.flags);
            write8(out, addrs[i]);
            write8(out, offsets[i]);
            write8(out, s.body.length);
            write4(out, s.link);
            write4(out, s.info);
            write8(out, s.addrAlign);
            write8(out, s.entrySize);
        }
        return out.toByteArray();
    }

    private static long align(long value, long alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }

    private static void write2(ByteArrayOutputStream b, int v) {
        b.write(v & 0xff);
        b.write((v >>> 8) & 0xff);
    }

    private static void write4(ByteArrayOutputStream b, long v) {
        b.write((int) (v & 0xff));
        b.write((int) ((v >>> 8) & 0xff));
        b.write((int) ((v >>> 16) & 0xff));
        b.write((int) ((v >>> 24) & 0xff));
    }

    private static void write8(ByteArrayOutputStream b, long v) {
        for (int i = 0; i < 8; i++) {
            b.write((int) ((v >>> (i * 8)) & 0xff));
        }
    }
}
