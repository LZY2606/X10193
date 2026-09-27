package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed, read-only view of one ELF file. */
public final class ElfFile {
    public static final int ET_REL = 1, ET_EXEC = 2, ET_DYN = 3;
    public static final int STB_LOCAL = 0, STB_GLOBAL = 1, STB_WEAK = 2;
    public static final int STT_NOTYPE = 0, STT_OBJECT = 1, STT_FUNC = 2, STT_SECTION = 3, STT_FILE = 4, STT_COMMON = 5;
    public static final int STV_DEFAULT = 0, STV_INTERNAL = 1, STV_HIDDEN = 2, STV_PROTECTED = 3;
    public static final int SHN_UNDEF = 0, SHN_ABS = 0xfff1, SHN_COMMON = 0xfff2;

    public String name = "?";
    public int elfClass;      // 1 = 32-bit, 2 = 64-bit
    public int elfType;
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Sym> symtab = new ArrayList<>();
    public final List<Sym> dynsym = new ArrayList<>();
    public final List<VersionDef> versionDefs = new ArrayList<>();
    public final List<VersionNeed> versionNeeds = new ArrayList<>();
    public final List<Reloc> relocs = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<String> neededLibs = new ArrayList<>();
    public String soname;

    public boolean isRelocatable() { return elfType == ET_REL; }
    public boolean isShared() { return elfType == ET_DYN; }
    public boolean isExecutable() { return elfType == ET_EXEC; }

    public static final class Section {
        public int index;
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
        public int index;            // index inside its own table
        public boolean dynamic;      // dynsym vs symtab
        public String name = "";
        public int bind;
        public int type;
        public int visibility = STV_DEFAULT;
        public int shndx;
        public long value;
        public long size;
        public String sectionName;   // resolved, "" for UND/ABS/COMMON
        public int versionIndex = -1; // raw versym index (hidden bit stripped), -1 when none
        public boolean versionHidden; // non-default version (@ instead of @@)
        public String versionName;    // resolved version name, null when none

        public boolean isUndefined() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON; }
        public boolean isLocal() { return bind == STB_LOCAL; }
        public boolean isWeak() { return bind == STB_WEAK; }
        public boolean hiddenFromGlobalScope() {
            return visibility == STV_HIDDEN || visibility == STV_INTERNAL;
        }
        public String displayVersion() {
            if (versionName == null) return "";
            return (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static final class VersionDef {
        public int index;
        public String name;
        public boolean base;
        public String parent; // predecessor in the aux chain (version graph edge)
    }

    public static final class VersionNeed {
        public String file;
        public final List<Entry> entries = new ArrayList<>();
        public static final class Entry {
            public int index;
            public String name;
        }
    }

    public static final class Reloc {
        public String sectionName;
        public long offset;
        public int symIndex;     // index into the linked symbol table
        public boolean dynamicTable;
        public String symName;
        public long type;
        public long addend;
        public boolean rela;
    }

    public static final class Group {
        public String signature;
        public boolean comdat;
        public final List<String> memberSections = new ArrayList<>();
    }
}
