package com.verdict.elf;

import java.util.ArrayList;
import java.util.List;

/** In-memory model of a parsed ELF object (relocatable, executable or shared object). */
public final class ElfModel {

    public enum Kind { RELOCATABLE, EXECUTABLE, SHARED }

    public enum Binding { LOCAL, GLOBAL, WEAK }

    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED }

    public static final class Section {
        public final String name;
        public final int type;
        public final long flags;
        public final long addr;
        public final long size;
        public final int link;
        public final int info;

        public Section(String name, int type, long flags, long addr, long size, int link, int info) {
            this.name = name;
            this.type = type;
            this.flags = flags;
            this.addr = addr;
            this.size = size;
            this.link = link;
            this.info = info;
        }
    }

    public static final class Symbol {
        public final String table; // "symtab" or "dynsym"
        public final int index;
        public final String name;
        public final Binding binding;
        public final int type;
        public final Visibility visibility;
        public final int shndx;
        public final long value;
        public final long size;
        public String sectionName;   // resolved section name or special name
        public int versionIndex;     // raw versym value (0 local, 1 global, >=2 versioned)
        public String versionName;   // resolved version name or null
        public boolean versionHidden; // non-default version (@ instead of @@)

        public Symbol(String table, int index, String name, Binding binding, int type,
                      Visibility visibility, int shndx, long value, long size) {
            this.table = table;
            this.index = index;
            this.name = name;
            this.binding = binding;
            this.type = type;
            this.visibility = visibility;
            this.shndx = shndx;
            this.value = value;
            this.size = size;
        }

        public boolean isDefined() {
            return shndx != 0;
        }

        public boolean isCommon() {
            return shndx == 0xfff2;
        }

        public boolean isAbsolute() {
            return shndx == 0xfff1;
        }

        /** Display name including version decoration, e.g. foo@@VER1 or foo@VER1. */
        public String displayName() {
            if (versionName == null) {
                return name;
            }
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static final class Relocation {
        public final String section; // section the relocation applies to
        public final long offset;
        public final long type;
        public final int symIndex;
        public final String symbolName; // resolved, "" for none
        public final long addend;
        public final boolean rela;

        public Relocation(String section, long offset, long type, int symIndex, String symbolName,
                          long addend, boolean rela) {
            this.section = section;
            this.offset = offset;
            this.type = type;
            this.symIndex = symIndex;
            this.symbolName = symbolName;
            this.addend = addend;
            this.rela = rela;
        }
    }

    public static final class Group {
        public final String signature;
        public final boolean comdat;
        public final List<String> members = new ArrayList<>();

        public Group(String signature, boolean comdat) {
            this.signature = signature;
            this.comdat = comdat;
        }
    }

    public static final class VerDef {
        public final int index;
        public final String name;
        public final String parent;

        public VerDef(int index, String name, String parent) {
            this.index = index;
            this.name = name;
            this.parent = parent;
        }
    }

    public static final class VerNeedEntry {
        public final int index;
        public final String name;

        public VerNeedEntry(int index, String name) {
            this.index = index;
            this.name = name;
        }
    }

    public static final class VerNeed {
        public final String file;
        public final List<VerNeedEntry> entries = new ArrayList<>();

        public VerNeed(String file) {
            this.file = file;
        }
    }

    public int elfClass; // 1 = 32-bit, 2 = 64-bit
    public int endian;   // 1 = little, 2 = big
    public Kind kind;
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();    // SHT_SYMTAB
    public final List<Symbol> dynSymbols = new ArrayList<>(); // SHT_DYNSYM
    public final List<Relocation> relocations = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<VerDef> verDefs = new ArrayList<>();
    public final List<VerNeed> verNeeds = new ArrayList<>();
    public final List<String> needed = new ArrayList<>(); // DT_NEEDED
    public String soname;
}
