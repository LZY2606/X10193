package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed in-memory model of one ELF object (or one archive member). */
public final class ElfFile {
    public String name = "?";
    public int elfClass;
    public int elfType;
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Sym> symtab = new ArrayList<>();
    public final List<Sym> dynsym = new ArrayList<>();
    public final List<Reloc> relocations = new ArrayList<>();
    public final List<VerDef> verdefs = new ArrayList<>();
    public final List<VerNeed> verneeds = new ArrayList<>();
    public int[] versym = new int[0];
    public final List<Group> groups = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;

    public boolean isShared() {
        return elfType == Elf.ET_DYN;
    }

    public boolean isExecutable() {
        return elfType == Elf.ET_EXEC;
    }

    public boolean isRelocatable() {
        return elfType == Elf.ET_REL;
    }

    /** Resolve a dynsym entry's version index back to its version name. */
    public VersionRef versionOf(int dynIndex) {
        if (dynIndex < 0 || dynIndex >= versym.length) {
            return null;
        }
        int raw = versym[dynIndex];
        boolean hidden = (raw & Elf.VERSYM_HIDDEN) != 0;
        int idx = raw & Elf.VERSYM_VERSION;
        if (idx == Elf.VER_NDX_LOCAL || idx == Elf.VER_NDX_GLOBAL) {
            return null;
        }
        for (VerDef d : verdefs) {
            if (d.index == idx) {
                return new VersionRef(d.name, hidden, idx);
            }
        }
        for (VerNeed n : verneeds) {
            for (VerNeedAux a : n.aux) {
                if (a.other == idx) {
                    return new VersionRef(a.name, hidden, idx);
                }
            }
        }
        throw new ElfParseException("version index " + idx + " has no name in " + name);
    }

    public static final class Section {
        public String name;
        public int type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long entsize;
    }

    public static final class Sym {
        public int index;
        public String name = "";
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        public boolean dynamic;

        public boolean isDefined() {
            return shndx != Elf.SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == Elf.SHN_COMMON;
        }

        public boolean isLocal() {
            return bind == Elf.STB_LOCAL;
        }

        public boolean isWeak() {
            return bind == Elf.STB_WEAK;
        }

        public boolean isHiddenFromOutside() {
            return visibility == Elf.STV_HIDDEN || visibility == Elf.STV_INTERNAL;
        }
    }

    public static final class Reloc {
        public String sectionName;
        public long offset;
        public int symIndex;
        public int type;
        public Long addend;
        public boolean dynamic; // relocation uses dynsym
    }

    public static final class VerDef {
        public int index;
        public int flags;
        public String name;
        public final List<String> aliases = new ArrayList<>();
    }

    public static final class VerNeedAux {
        public String name;
        public int other;
    }

    public static final class VerNeed {
        public String file;
        public final List<VerNeedAux> aux = new ArrayList<>();
    }

    public static final class Group {
        public String signature;
        public boolean comdat;
        public final List<Integer> memberSections = new ArrayList<>();
    }

    public record VersionRef(String name, boolean hidden, int index) {
    }
}
