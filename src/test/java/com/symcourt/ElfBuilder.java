package com.symcourt;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Programmatic ELF64 little-endian fixture builder. */
public final class ElfBuilder {

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int SHN_COMMON = 0xfff2;

    private record Sym(String name, int bind, int type, int vis, int shndx, long value, long size) { }
    private record Verdef(int index, String name) { }
    private record Verneed(String file, int index, String name) { }
    private record Reloc(long offset, int sym, int type, long addend) { }

    private final int elfType;
    private boolean dyn;
    private final List<Sym> syms = new ArrayList<>();
    private final Map<Integer, Integer> versym = new LinkedHashMap<>();
    private final List<Verdef> verdefs = new ArrayList<>();
    private final List<Verneed> verneeds = new ArrayList<>();
    private final List<Reloc> relocs = new ArrayList<>();
    private Integer groupSigSym;
    private final List<Integer> groupSections = new ArrayList<>();

    private ElfBuilder(int elfType) {
        this.elfType = elfType;
        syms.add(new Sym("", 0, 0, 0, 0, 0, 0));
    }

    public static ElfBuilder create(int elfType) {
        return new ElfBuilder(elfType);
    }

    public ElfBuilder dynsym() {
        dyn = true;
        return this;
    }

    private int add(String name, int bind, int type, int vis, int shndx, long value, long size) {
        syms.add(new Sym(name, bind, type, vis, shndx, value, size));
        return syms.size() - 1;
    }

    public ElfBuilder def(String name) {
        add(name, 1, 2, 0, 1, 0x10 * syms.size(), 8);
        return this;
    }

    public ElfBuilder weak(String name) {
        add(name, 2, 2, 0, 1, 0x10 * syms.size(), 8);
        return this;
    }

    public ElfBuilder hidden(String name) {
        add(name, 1, 2, 2, 1, 0x10 * syms.size(), 8);
        return this;
    }

    public ElfBuilder protectedDef(String name) {
        add(name, 1, 2, 3, 1, 0x10 * syms.size(), 8);
        return this;
    }

    public ElfBuilder object(String name) {
        add(name, 1, 1, 0, 1, 0x10 * syms.size(), 16);
        return this;
    }

    public ElfBuilder common(String name, long size) {
        add(name, 1, 1, 0, SHN_COMMON, 8, size);
        return this;
    }

    public ElfBuilder undef(String name) {
        add(name, 1, 0, 0, 0, 0, 0);
        return this;
    }

    public ElfBuilder undefWeak(String name) {
        add(name, 2, 0, 0, 0, 0, 0);
        return this;
    }

    /** Attach version index to the most recently added symbol. */
    public ElfBuilder version(int verIndex, boolean nonDefault) {
        versym.put(syms.size() - 1, verIndex | (nonDefault ? 0x8000 : 0));
        return this;
    }

    public ElfBuilder verdef(int index, String name) {
        verdefs.add(new Verdef(index, name));
        return this;
    }

    public ElfBuilder verneed(String file, int index, String name) {
        verneeds.add(new Verneed(file, index, name));
        return this;
    }

    /** COMDAT group over .text, signature = most recently added symbol. */
    public ElfBuilder group() {
        groupSigSym = syms.size() - 1;
        groupSections.add(1);
        return this;
    }

    public ElfBuilder reloc(long offset, String symName, int type, long addend) {
        int idx = -1;
        for (int i = 0; i < syms.size(); i++) {
            if (syms.get(i).name.equals(symName)) {
                idx = i;
            }
        }
        if (idx < 0) {
            throw new IllegalArgumentException("unknown symbol " + symName);
        }
        relocs.add(new Reloc(offset, idx, type, addend));
        return this;
    }

    private static final class Str {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final Map<String, Integer> offs = new LinkedHashMap<>();

        Str() {
            buf.write(0);
        }

        int add(String s) {
            return offs.computeIfAbsent(s, k -> {
                int off = buf.size();
                buf.writeBytes(k.getBytes(StandardCharsets.UTF_8));
                buf.write(0);
                return off;
            });
        }

        byte[] bytes() {
            return buf.toByteArray();
        }
    }

    private record Sec(String name, int type, long flags, int link, int info,
                       byte[] data, long entsize, int align) { }

    public byte[] build() {
        Str strtab = new Str();
        Str shstr = new Str();
        for (Sym s : syms) {
            strtab.add(s.name);
        }
        for (Verdef d : verdefs) {
            strtab.add(d.name);
        }
        for (Verneed n : verneeds) {
            strtab.add(n.file);
            strtab.add(n.name);
        }

        List<Sec> secs = new ArrayList<>();
        secs.add(new Sec("", 0, 0, 0, 0, new byte[0], 0, 0));
        secs.add(new Sec(".text", 1, 6, 0, 0, new byte[] {(byte) 0x90, (byte) 0xc3}, 0, 16));
        if (groupSigSym != null) {
            ByteBuffer g = ByteBuffer.allocate(4 + 4 * groupSections.size()).order(ByteOrder.LITTLE_ENDIAN);
            g.putInt(1);
            for (int s : groupSections) {
                g.putInt(s);
            }
            secs.add(new Sec(".group", 17, 0, 3, groupSigSym, g.array(), 4, 4));
        }
        int symtabIdx = secs.size();
        byte[] symBytes = symBytes(strtab);
        secs.add(new Sec(dyn ? ".dynsym" : ".symtab", dyn ? 11 : 2, 0, symtabIdx + 1, 1, symBytes, 24, 8));
        secs.add(new Sec(dyn ? ".dynstr" : ".strtab", 3, 0, 0, 0, strtab.bytes(), 0, 1));
        if (dyn && !versym.isEmpty()) {
            ByteBuffer v = ByteBuffer.allocate(2 * syms.size()).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < syms.size(); i++) {
                v.putShort((short) (int) versym.getOrDefault(i, 0));
            }
            secs.add(new Sec(".gnu.version", 0x6fffffff, 0, symtabIdx, 0, v.array(), 2, 2));
        }
        if (!verdefs.isEmpty()) {
            secs.add(new Sec(".gnu.version_d", 0x6ffffffd, 0, symtabIdx + 1, verdefs.size(),
                    verdefBytes(strtab), 0, 8));
        }
        if (!verneeds.isEmpty()) {
            secs.add(new Sec(".gnu.version_r", 0x6ffffffe, 0, symtabIdx + 1, verneeds.size(),
                    verneedBytes(strtab), 0, 8));
        }
        if (!relocs.isEmpty()) {
            ByteBuffer r = ByteBuffer.allocate(24 * relocs.size()).order(ByteOrder.LITTLE_ENDIAN);
            for (Reloc x : relocs) {
                r.putLong(x.offset);
                r.putLong(((long) x.sym << 32) | (x.type & 0xffffffffL));
                r.putLong(x.addend);
            }
            secs.add(new Sec(".rela.text", 4, 0, symtabIdx, 1, r.array(), 24, 8));
        }
        for (Sec s : secs) {
            shstr.add(s.name);
        }
        shstr.add(".shstrtab");
        secs.add(new Sec(".shstrtab", 3, 0, 0, 0, shstr.bytes(), 0, 1));

        int shnum = secs.size();
        long off = 64;
        long[] offs = new long[shnum];
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(new byte[64]);
        for (int i = 0; i < shnum; i++) {
            Sec s = secs.get(i);
            int align = Math.max(1, s.align);
            while (off % align != 0) {
                body.write(0);
                off++;
            }
            offs[i] = s.data.length == 0 ? 0 : off;
            body.writeBytes(s.data);
            off += s.data.length;
        }
        while (off % 8 != 0) {
            body.write(0);
            off++;
        }
        long shoff = off;

        ByteBuffer sh = ByteBuffer.allocate(64 * shnum).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < shnum; i++) {
            Sec s = secs.get(i);
            sh.putInt(shstr.add(s.name));
            sh.putInt(s.type);
            sh.putLong(s.flags);
            sh.putLong(0);
            sh.putLong(offs[i]);
            sh.putLong(s.data.length);
            sh.putInt(s.link);
            sh.putInt(s.info);
            sh.putLong(s.align);
            sh.putLong(s.entsize);
        }
        body.writeBytes(sh.array());

        ByteBuffer eh = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        eh.put(new byte[] {0x7f, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        eh.putShort((short) elfType);
        eh.putShort((short) 0x3e);
        eh.putInt(1);
        eh.putLong(0);
        eh.putLong(0);
        eh.putLong(shoff);
        eh.putInt(0);
        eh.putShort((short) 64);
        eh.putShort((short) 0);
        eh.putShort((short) 0);
        eh.putShort((short) 64);
        eh.putShort((short) shnum);
        eh.putShort((short) (shnum - 1));
        byte[] out = body.toByteArray();
        System.arraycopy(eh.array(), 0, out, 0, 64);
        return out;
    }

    private byte[] symBytes(Str strtab) {
        ByteBuffer b = ByteBuffer.allocate(24 * syms.size()).order(ByteOrder.LITTLE_ENDIAN);
        for (Sym s : syms) {
            b.putInt(strtab.add(s.name));
            b.put((byte) ((s.bind << 4) | (s.type & 0xf)));
            b.put((byte) s.vis);
            b.putShort((short) s.shndx);
            b.putLong(s.value);
            b.putLong(s.size);
        }
        return b.array();
    }

    private byte[] verdefBytes(Str strtab) {
        ByteBuffer b = ByteBuffer.allocate(28 * verdefs.size()).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < verdefs.size(); i++) {
            Verdef d = verdefs.get(i);
            b.putShort((short) 1);
            b.putShort((short) 0);
            b.putShort((short) d.index);
            b.putShort((short) 1);
            b.putInt(0);
            b.putInt(20);
            b.putInt(i + 1 < verdefs.size() ? 28 : 0);
            b.putInt(strtab.add(d.name));
            b.putInt(0);
        }
        return b.array();
    }

    private byte[] verneedBytes(Str strtab) {
        Map<String, List<Verneed>> byFile = new LinkedHashMap<>();
        for (Verneed n : verneeds) {
            byFile.computeIfAbsent(n.file, k -> new ArrayList<>()).add(n);
        }
        int total = byFile.size() * 16 + verneeds.size() * 16;
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        int fileIdx = 0;
        for (Map.Entry<String, List<Verneed>> e : byFile.entrySet()) {
            List<Verneed> entries = e.getValue();
            b.putShort((short) 1);
            b.putShort((short) entries.size());
            b.putInt(strtab.add(e.getKey()));
            b.putInt(16);
            b.putInt(++fileIdx < byFile.size() ? 16 + 16 * entries.size() : 0);
            for (int i = 0; i < entries.size(); i++) {
                Verneed n = entries.get(i);
                b.putInt(0);
                b.putShort((short) 0);
                b.putShort((short) n.index);
                b.putInt(strtab.add(n.name));
                b.putInt(i + 1 < entries.size() ? 16 : 0);
            }
        }
        return b.array();
    }
}
