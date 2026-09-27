package com.symcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed, immutable-ish view of one ELF file. All indices/offsets already validated. */
public final class ElfFile {
    public enum Kind { REL, EXEC, DYN, OTHER }

    public static final class Section {
        public final int index;
        public final String name;
        public final long type;
        public final long offset;
        public final long size;
        public final long link;
        public final long info;
        public final long entsize;
        public final long addr;

        public Section(int index, String name, long type, long offset, long size,
                       long link, long info, long entsize, long addr) {
            this.index = index;
            this.name = name;
            this.type = type;
            this.offset = offset;
            this.size = size;
            this.link = link;
            this.info = info;
            this.entsize = entsize;
            this.addr = addr;
        }
    }

    public static final class Symbol {
        public int index;
        public String name = "";
        public long value;
        public long size;
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public boolean dyn;
        /** raw VERSYM value, -1 when no version table */
        public int versionRaw = -1;
        /** restored version name, null when unversioned */
        public String versionName;
        /** true when the version is non-default (foo@VER, hidden bit set) */
        public boolean versionHidden;
        /** for needed versions: the file that must provide the version */
        public String versionFile;

        public boolean defined() {
            return shndx != ElfConst.SHN_UNDEF;
        }

        public boolean common() {
            return shndx == ElfConst.SHN_COMMON;
        }

        public boolean local() {
            return bind == ElfConst.STB_LOCAL;
        }

        public boolean weak() {
            return bind == ElfConst.STB_WEAK;
        }

        public boolean hidden() {
            return visibility == ElfConst.STV_HIDDEN || visibility == ElfConst.STV_INTERNAL;
        }

        public boolean exported() {
            return !local() && !hidden();
        }

        public String displayName() {
            if (versionName == null) {
                return name;
            }
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static final class Reloc {
        public int sectionIndex;
        public String sectionName = "";
        public long offset;
        public int symIndex;
        public long type;
        public long addend;
        public boolean rela;
        /** true when the linked symbol table is SHT_DYNSYM */
        public boolean dynTable;
    }

    public static final class VerDef {
        public int ndx;
        public String name;
        public boolean base;
    }

    public static final class VerNeedEntry {
        public String version;
        public int other;
    }

    public static final class VerNeed {
        public String file;
        public List<VerNeedEntry> entries = new ArrayList<>();
    }

    public static final class Group {
        public String signature;
        public boolean comdat;
        public List<Integer> sections = new ArrayList<>();
    }

    public int elfClass; // 1 = 32-bit, 2 = 64-bit
    public boolean littleEndian;
    public Kind kind = Kind.OTHER;
    public int machine;
    public List<Section> sections = new ArrayList<>();
    public List<Symbol> symtab = new ArrayList<>();
    public List<Symbol> dynsym = new ArrayList<>();
    public List<Reloc> relocs = new ArrayList<>();
    public Map<Integer, VerDef> verdefs = new LinkedHashMap<>();
    public List<VerNeed> verneeds = new ArrayList<>();
    public List<Group> groups = new ArrayList<>();
    public List<String> needed = new ArrayList<>();
    public String soname;
    public List<String> warnings = new ArrayList<>();

    public Section section(int idx) {
        return idx >= 0 && idx < sections.size() ? sections.get(idx) : null;
    }
}
