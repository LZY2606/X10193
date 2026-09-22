package com.court.fixtures;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Programmatic builder for small, valid ELF64-LE objects/shared objects/
 * executables, used by tests and by the built-in demo fixtures.
 *
 * It generates the section tables the court actually consumes:
 * .symtab/.dynsym + strtab, SHT_REL/SHT_RELA, .gnu.version(.d/.r),
 * SHT_GROUP, and .dynamic. No system linker is invoked.
 */
public class Elf {
    public static final int STB_LOCAL = 0, STB_GLOBAL = 1, STB_WEAK = 2;
    public static final int STT_NOTYPE = 0, STT_OBJECT = 1, STT_FUNC = 2, STT_COMMON = 5;
    public static final int STV_DEFAULT = 0, STV_INTERNAL = 1, STV_HIDDEN = 2, STV_PROTECTED = 3;
    public static final int SHN_UNDEF = 0, SHN_ABS = 0xfff1, SHN_COMMON = 0xfff2;

    public static final int SHT_PROGBITS = 1, SHT_SYMTAB = 2, SHT_STRTAB = 3,
            SHT_RELA = 4, SHT_HASH = 5, SHT_DYNAMIC = 6, SHT_REL = 9,
            SHT_DYNSYM = 11, SHT_GROUP = 17;
    public static final long SHF_WRITE = 1, SHF_ALLOC = 2, SHF_EXECINSTR = 4;
    public static final long SHF_WRITE_ALLOC = SHF_WRITE | SHF_ALLOC;

    public static final int R_X86_64_64 = 1, R_X86_64_PC32 = 2, R_X86_64_COPY = 5,
            R_X86_64_GLOB_DAT = 6, R_X86_64_JUMP_SLOT = 7, R_X86_64_RELATIVE = 8;

    public static final int ET_REL = 1, ET_EXEC = 2, ET_DYN = 3;

    public static class Sym {
        String name;
        int bind = STB_GLOBAL, type = STT_NOTYPE, vis = STV_DEFAULT, shndx = SHN_UNDEF;
        long value, size;
        Integer dynVersionIndex;
        Sym(String name) { this.name = name; }
        public Sym local() { bind = STB_LOCAL; return this; }
        public Sym weak() { bind = STB_WEAK; return this; }
        public Sym hidden() { vis = STV_HIDDEN; return this; }
        public Sym protectedVis() { vis = STV_PROTECTED; return this; }
        public Sym object() { type = STT_OBJECT; return this; }
        public Sym func() { type = STT_FUNC; return this; }
        public Sym defined(int sec, long value) { shndx = sec; this.value = value; return this; }
        public Sym common(long size, long align) { shndx = SHN_COMMON; this.size = size; this.value = align; return this; }
        public Sym size(long s) { this.size = s; return this; }
        public Sym version(int v) { this.dynVersionIndex = v; return this; }
    }

    public static class Reloc {
        String symbol;
        long offset;
        int type;
        Long addend;
        boolean applyToDynamic;
        int applySection = 1;
        Reloc(String symbol, long offset, int type, Long addend) {
            this.symbol = symbol; this.offset = offset; this.type = type; this.addend = addend;
        }
        public Reloc section(int s) { this.applySection = s; return this; }
        public Reloc dynamic() { this.applyToDynamic = true; return this; }
    }

    public static class VerDef {
        String name; int index; boolean nonDefaultAliasAlso; boolean base;
        public VerDef(String name, int index) { this.name = name; this.index = index; }
        public VerDef alias() { this.nonDefaultAliasAlso = true; return this; }
        public VerDef base() { this.base = true; return this; }
    }

    public static class VerNeed {
        String fromFile, name; int index; boolean weak;
        public VerNeed(String fromFile, String name, int index) {
            this.fromFile = fromFile; this.name = name; this.index = index;
        }
        public VerNeed weak() { this.weak = true; return this; }
    }

    private final int type;
    private final List<Sym> syms = new ArrayList<>();
    private final List<Sym> dynSyms = new ArrayList<>();
    private final List<Reloc> relas = new ArrayList<>();
    private final List<Reloc> dynRelas = new ArrayList<>();
    private final List<VerDef> verDefs = new ArrayList<>();
    private final List<VerNeed> verNeeds = new ArrayList<>();
    private final List<String> needed = new ArrayList<>();
    private String soname;
    private final List<int[]> groups = new ArrayList<>(); // {sigSymIdx, flag, members...}

    public static class Comdat {
        final String signature;
        final int signatureSymLocalIndex; // index in static sym list (-1 = auto add)
        final List<Integer> memberSections = new ArrayList<>();
        Comdat(String signature) { this.signature = signature; this.signatureSymLocalIndex = -1; }
        public Comdat member(int secIdx) { memberSections.add(secIdx); return this; }
    }
    private final List<Comdat> comdats = new ArrayList<>();
    private final Map<Integer, String> progbitsNames = new LinkedHashMap<>();

    public Elf(int type) { this.type = type; }

    public static Elf reloc() { return new Elf(ET_REL); }
    public static Elf shared() { return new Elf(ET_DYN); }
    public static Elf executable() { return new Elf(ET_EXEC); }

    public Sym sym(String name) { Sym s = new Sym(name); syms.add(s); return s; }
    public Sym dyn(String name) { Sym s = new Sym(name); dynSyms.add(s); return s; }
    public Elf rela(String sym, long off, int type, Long addend) {
        relas.add(new Reloc(sym, off, type, addend)); return this;
    }
    public Elf dynRela(String sym, long off, int type, Long addend) {
        Reloc r = new Reloc(sym, off, type, addend); r.dynamic(); dynRelas.add(r); return this;
    }
    public Elf need(String lib) { needed.add(lib); return this; }
    public Elf soname(String s) { this.soname = s; return this; }
    public Elf verDef(VerDef d) { verDefs.add(d); return this; }
    public Elf verNeed(VerNeed n) { verNeeds.add(n); return this; }
    public Elf sectionName(int idx, String name) { progbitsNames.put(idx, name); return this; }
    public Comdat comdat(String sig) { Comdat c = new Comdat(sig); comdats.add(c); return c; }

    public byte[] build() {
        return new Layout(this).emit();
    }

    int getType() { return type; }
    List<Sym> getSyms() { return syms; }
    List<Sym> getDynSyms() { return dynSyms; }
    List<Reloc> getRelas() { return relas; }
    List<Reloc> getDynRelas() { return dynRelas; }
    List<VerDef> getVerDefs() { return verDefs; }
    List<VerNeed> getVerNeeds() { return verNeeds; }
    List<String> getNeeded() { return needed; }
    String getSoname() { return soname; }
    Map<Integer, String> getProgbitsNames() { return progbitsNames; }
    List<Comdat> getComdats() { return comdats; }
}
