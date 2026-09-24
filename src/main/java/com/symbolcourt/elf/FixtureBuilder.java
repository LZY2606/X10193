package com.symbolcourt.elf;

import com.symbolcourt.model.Models.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds small, valid ELF64 little-endian (x86-64) objects entirely in memory,
 * plus GNU ar archives, so tests never depend on the host toolchain or linker.
 */
public class FixtureBuilder {

    public static final int SEC_UNDEF = -1;
    public static final int SEC_TEXT = 1;
    public static final int SEC_DATA = 2;
    public static final int SEC_ABS = -2;
    public static final int SEC_COMMON = -3;

    public static class Sym {
        String name;
        int bind = Elf.STB_GLOBAL;
        int type = Elf.STT_FUNC;
        int vis = Elf.STV_DEFAULT;
        long value;
        long size = 4;
        int section = SEC_TEXT;
        boolean dynamic;
        String ver;            // version name (def or need)
        boolean verDefault = true;
        boolean verWeak;
        String verFile;        // needed-from file for a requirement
        String group;          // COMDAT signature
        int _nameOff, _dynNameOff;
        int groupSection = SEC_TEXT;
        boolean undefined;

        public Sym(String name) { this.name = name; }
        public Sym bind(int b) { this.bind = b; return this; }
        public Sym type(int t) { this.type = t; return this; }
        public Sym vis(int v) { this.vis = v; return this; }
        public Sym value(long v) { this.value = v; return this; }
        public Sym size(long s) { this.size = s; return this; }
        public Sym section(int s) { this.section = s; return this; }
        public Sym dynamic(boolean d) { this.dynamic = d; return this; }
        public Sym version(String v) { this.ver = v; return this; }
        public Sym versionDefault(boolean d) { this.verDefault = d; return this; }
        public Sym weakVersion(boolean w) { this.verWeak = w; return this; }
        public Sym needFrom(String f) { this.verFile = f; return this; }
        public Sym comdat(String sig) { this.group = sig; this.section = 100; return this; }
        public Sym weak() { this.bind = Elf.STB_WEAK; return this; }
        public Sym local() { this.bind = Elf.STB_LOCAL; return this; }
        public Sym hidden() { this.vis = Elf.STV_HIDDEN; return this; }
        public Sym protectedVis() { this.vis = Elf.STV_PROTECTED; return this; }
        public Sym undef() { this.undefined = true; this.section = SEC_UNDEF; this.type = Elf.STT_NOTYPE; return this; }
        public Sym common(long align) { this.section = SEC_COMMON; this.type = Elf.STT_OBJECT; this.value = align; return this; }
    }

    public static class Rel {
        int type = Elf.R_X86_64_64;
        long offset;
        long addend;
        Sym sym;
        boolean dynamicSym = true;
        int target = SEC_TEXT;
        public Rel type(int t) { this.type = t; return this; }
        public Rel offset(long o) { this.offset = o; return this; }
        public Rel addend(long a) { this.addend = a; return this; }
        public Rel sym(Sym s) { this.sym = s; return this; }
        public Rel dyn(boolean d) { this.dynamicSym = d; return this; }
        public Rel target(int t) { this.target = t; return this; }
    }

    private int elfType = Elf.ET_REL;
    private String soname;
    private final List<Sym> symbols = new ArrayList<>();
    private final List<Rel> rels = new ArrayList<>();

    public FixtureBuilder relocatable() { this.elfType = Elf.ET_REL; return this; }
    public FixtureBuilder shared() { this.elfType = Elf.ET_DYN; return this; }
    public FixtureBuilder executable() { this.elfType = Elf.ET_EXEC; return this; }
    public FixtureBuilder soname(String s) { this.soname = s; return this; }

    public Sym sym(String name) { Sym s = new Sym(name); symbols.add(s); return s; }
    public Sym add(Sym s) { symbols.add(s); return s; }
    public Rel rel(Sym s) { Rel r = new Rel(); r.sym = s; rels.add(r); return r; }
    public Rel reloc() { Rel r = new Rel(); rels.add(r); return r; }

    public byte[] build() { return new Emitter(this).emit(); }

    public ParsedObject parseAs(String id, String name) {
        return new ElfParser().parse(id, name, build());
    }

    public static byte[] archive(Map.Entry<String, byte[]>... members) {
        return ar(List.of(members));
    }

    public static byte[] ar(List<Map.Entry<String, byte[]>> members) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] magic = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(magic);
        for (Map.Entry<String, byte[]> e : members) {
            byte[] body = e.getValue();
            String header = String.format(Locale.ROOT,
                    "%-16s0           0     100644  %-10d`\n", e.getKey() + "/", body.length);
            out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(body);
            if ((body.length & 1) == 1) out.write('\n');
        }
        return out.toByteArray();
    }

    int getElfType() { return elfType; }
    String getSoname() { return soname; }
    List<Sym> getSymbols() { return symbols; }
    List<Rel> getRels() { return rels; }
}
