package com.gsb.tribunal.elf;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed ELF model. Pure data; produced by {@link ElfParser}.
 */
public final class Elf {

    public enum Kind { RELOCATABLE, EXECUTABLE, SHARED, OTHER }

    public enum Bind { LOCAL, GLOBAL, WEAK, OTHER }

    public enum SymType { NOTYPE, OBJECT, FUNC, SECTION, FILE, COMMON, TLS, OTHER }

    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED }

    public static final int SHN_UNDEF = 0;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int GRP_COMDAT = 0x1;

    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    public static final class Section {
        public int index;
        public String name;
        public long type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long addralign;
        public long entsize;
        /** For SHT_GROUP: member section indices. */
        public List<Integer> groupMembers = new ArrayList<>();
        /** For SHT_GROUP: true when GRP_COMDAT flag set. */
        public boolean groupComdat;
    }

    public static final class Symbol {
        public int index;
        public String name;
        public long value;
        public long size;
        public Bind bind;
        public SymType type;
        public Visibility visibility;
        public int shndx;

        public boolean isUndefined() {
            return shndx == SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == SHN_COMMON;
        }
    }

    public static final class Reloc {
        public String sectionName;
        public int sectionIndex;
        public long offset;
        public int symIndex;
        public long type;
        public String typeName;
        public Long addend;
    }

    public static final class Verdef {
        public int index;
        public String name;
        public int flags;
        public int ndx;
        public int cnt;
        public int hash;
        public boolean base;
    }

    public static final class Verneed {
        public int index;
        public String file;
        public int cnt;
    }

    public static final class Vernaux {
        public int index;
        public int parentIndex;
        public String name;
        public int flags;
        public int other;
        public int hash;
    }

    public int elfClass; // 1 = 32-bit, 2 = 64-bit
    public boolean littleEndian;
    public Kind kind = Kind.OTHER;
    public long machine;
    public long entry;
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symtab = new ArrayList<>();
    public final List<Symbol> dynsym = new ArrayList<>();
    public final List<Reloc> relocs = new ArrayList<>();
    public final List<Reloc> dynRelocs = new ArrayList<>();
    public final List<Verdef> verdefs = new ArrayList<>();
    public final List<Verneed> verneeds = new ArrayList<>();
    public final List<Vernaux> vernauxes = new ArrayList<>();
    /** Raw .gnu.version half-words, parallel to dynsym. */
    public List<Integer> versym = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;

    public Section section(int index) {
        if (index < 0 || index >= sections.size()) return null;
        return sections.get(index);
    }

    public Symbol symtabSymbol(int index) {
        if (index < 0 || index >= symtab.size()) return null;
        return symtab.get(index);
    }

    public Symbol dynsymSymbol(int index) {
        if (index < 0 || index >= dynsym.size()) return null;
        return dynsym.get(index);
    }

    /**
     * Resolve the version name attached to a dynamic symbol definition.
     * Returns null for unversioned definitions.
     */
    public String definedVersionOf(int dynsymIndex) {
        if (dynsymIndex < 0 || dynsymIndex >= versym.size()) return null;
        int raw = versym.get(dynsymIndex);
        boolean hidden = (raw & 0x8000) != 0;
        int idx = raw & 0x7fff;
        if (idx <= VER_NDX_GLOBAL) return null;
        for (Verdef vd : verdefs) {
            if (vd.ndx == idx && !vd.base) return vd.name;
        }
        return null;
    }

    /**
     * Resolve the required version name attached to a dynamic undefined symbol.
     * Returns null when the reference carries no version requirement.
     */
    public String requiredVersionOf(int dynsymIndex) {
        if (dynsymIndex < 0 || dynsymIndex >= versym.size()) return null;
        int raw = versym.get(dynsymIndex);
        int idx = raw & 0x7fff;
        if (idx <= VER_NDX_GLOBAL) return null;
        for (Vernaux va : vernauxes) {
            if (va.other == idx) return va.name;
        }
        return null;
    }

    public String requiredVersionFileOf(int dynsymIndex) {
        if (dynsymIndex < 0 || dynsymIndex >= versym.size()) return null;
        int raw = versym.get(dynsymIndex);
        int idx = raw & 0x7fff;
        if (idx <= VER_NDX_GLOBAL) return null;
        for (Vernaux va : vernauxes) {
            if (va.other == idx) {
                for (Verneed vn : verneeds) {
                    if (vn.index == va.parentIndex) return vn.file;
                }
            }
        }
        return null;
    }
}
