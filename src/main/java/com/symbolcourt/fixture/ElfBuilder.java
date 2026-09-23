package com.symbolcourt.fixture;

import com.symbolcourt.elf.Elf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用代码构造小型 ELF64 小端对象（ET_REL/ET_DYN/ET_EXEC），
 * 含 symtab/dynsym、GNU 版本三表、SHT_GROUP 与 RELA 重定位。
 */
public class ElfBuilder {

    public static class SectionSpec {
        String name;
        int type;
        long flags;
        byte[] data = new byte[0];
        long size;
        long align = 1;
        long entsize;
        int link;
        int info;
        boolean nobits;
        String groupSignature;
        int groupFlags = Elf.GRP_COMDAT;
    }

    private final List<SymSpec> syms = new ArrayList<>();
    private final List<RelocSpec> rels = new ArrayList<>();
    private final Map<String, SectionSpec> sections = new LinkedHashMap<>();
    private int type = Elf.ET_REL;
    private int machine = 62;
    private String soname;
    private final List<String> needed = new ArrayList<>();
    private final List<VersionAlias> versionAliases = new ArrayList<>();

    private static class VersionAlias {
        String main;
        List<String> aliases = new ArrayList<>();
    }

    public static ElfBuilder rel(String name) {
        ElfBuilder b = new ElfBuilder();
        b.section(".text", Elf.SHT_PROGBITS, Elf.SHF_ALLOC | Elf.SHF_EXECINSTR).pad(32);
        b.section(".data", Elf.SHT_PROGBITS, Elf.SHF_ALLOC | Elf.SHF_WRITE).pad(16);
        b.section(".bss", Elf.SHT_NOBITS, Elf.SHF_ALLOC | Elf.SHF_WRITE).size(8);
        return b;
    }

    public static ElfBuilder dyn(String soname) {
        ElfBuilder b = new ElfBuilder();
        b.type = Elf.ET_DYN;
        b.soname = soname;
        b.section(".text", Elf.SHT_PROGBITS, Elf.SHF_ALLOC | Elf.SHF_EXECINSTR).pad(32);
        b.section(".data", Elf.SHT_PROGBITS, Elf.SHF_ALLOC | Elf.SHF_WRITE).pad(16);
        return b;
    }

    public static ElfBuilder exec(String name) {
        ElfBuilder b = rel(name);
        b.type = Elf.ET_EXEC;
        return b;
    }

    public SectionSpec section(String name, int type, long flags) {
        SectionSpec s = new SectionSpec();
        s.name = name;
        s.type = type;
        s.flags = flags;
        if (type == Elf.SHT_NOBITS) {
            s.nobits = true;
        }
        sections.put(name, s);
        return s;
    }

    public ElfBuilder group(String signature, String... members) {
        for (String m : members) {
            sections.get(m).groupSignature = signature;
        }
        return b -> {} == null ? this : this;
    }

    public ElfBuilder needed(String lib) {
        needed.add(lib);
        return this;
    }

    public ElfBuilder versionAlias(String main, String... aliases) {
        VersionAlias va = new VersionAlias();
        va.main = main;
        va.aliases.addAll(List.of(aliases));
        versionAliases.add(va);
        return this;
    }

    private SymSpec sym(String name) {
        SymSpec s = new SymSpec();
        s.name = name;
        syms.add(s);
        return s;
    }

    public ElfBuilder strong(String name, String sec) {
        SymSpec s = sym(name);
        s.bind = Elf.STB_GLOBAL;
        s.type = Elf.STT_FUNC;
        s.section = sec;
        return this;
    }

    public ElfBuilder strongObject(String name, String sec, long value, long size) {
        SymSpec s = sym(name);
        s.bind = Elf.STB_GLOBAL;
        s.type = Elf.STT_OBJECT;
        s.section = sec;
        s.value = value;
        s.size = size;
        return this;
    }

    public ElfBuilder weak(String name, String sec) {
        SymSpec s = sym(name);
        s.bind = Elf.STB_WEAK;
        s.type = Elf.STT_FUNC;
        s.section = sec;
        return this;
    }

    public ElfBuilder common(String name, long size, long align) {
        SymSpec s = sym(name);
        s.bind = Elf.STB_GLOBAL;
        s.type = Elf.STT_OBJECT;
        s.defined = true;
        s.section = "*COMMON*";
        s.size = size;
        s.commonAlign = align;
        return this;
    }

    public ElfBuilder hiddenStrong(String name, String sec) {
        strong(name, sec);
        syms.get(syms.size() - 1).visibility = Elf.STV_HIDDEN;
        return this;
    }

    public ElfBuilder protectedStrong(String name, String sec) {
        strong(name, sec);
        syms.get(syms.size() - 1).visibility = Elf.STV_PROTECTED;
        return this;
    }

    public ElfBuilder undefined(String name, int bind) {
        SymSpec s = sym(name);
        s.bind = bind;
        s.type = Elf.STT_NOTYPE;
        s.defined = false;
        s.section = "*UNDEF*";
        s.staticOnly = type == Elf.ET_REL;
        return this;
    }

    public ElfBuilder undefinedVersion(String name, int bind, String file, String version) {
        undefined(name, bind);
        SymSpec s = syms.get(syms.size() - 1);
        s.version = version;
        s.versionRequired = true;
        s.neededFromFile = file;
        s.dynamicOnly = true;
        s.staticOnly = false;
        if (!needed.contains(file)) {
            needed.add(file);
        }
        return this;
    }

    public ElfBuilder definedVersion(String name, String sec, String version, boolean nonDefault) {
        strong(name, sec);
        SymSpec s = syms.get(syms.size() - 1);
        s.version = version;
        s.versionHidden = nonDefault;
        s.dynamicOnly = type == Elf.ET_DYN;
        s.staticOnly = type == Elf.ET_REL;
        return this;
    }

    public ElfBuilder reloc(String targetSection, long off, int type, String symbol, long addend) {
        RelocSpec r = new RelocSpec();
        r.targetSection = targetSection;
        r.offset = off;
        r.type = type;
        r.symbol = symbol;
        r.addend = addend;
        rels.add(r);
        return this;
    }

    public byte[] build() {
        return new ElfAssembler().assemble(this);
    }

    // 访问器供 assembler 使用
    List<SymSpec> symSpecs() { return syms; }
    List<RelocSpec> relocSpecs() { return rels; }
    Map<String, SectionSpec> sectionSpecs() { return sections; }
    int elfType() { return type; }
    int elfMachine() { return machine; }
    String elfSoname() { return soname; }
    List<String> elfNeeded() { return needed; }
    List<VersionAlias> aliases() { return versionAliases; }
}
