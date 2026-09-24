package com.verdict.elf;

import java.util.ArrayList;
import java.util.List;

/** In-memory model of a parsed ELF object. Pure data, no behaviour. */
public final class Elf {
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_FILE = 4;
    public static final int STT_COMMON = 5;
    public static final int STT_TLS = 6;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int GRP_COMDAT = 1;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;

    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;

    public int elfClass; // 1 = 32-bit, 2 = 64-bit
    public int type;     // ET_*
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Sym> symbols = new ArrayList<>();    // SHT_SYMTAB
    public final List<Sym> dynSymbols = new ArrayList<>(); // SHT_DYNSYM (versions attached)
    public final List<VerDef> verDefs = new ArrayList<>();
    public final List<VerNeed> verNeeds = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<Reloc> relocs = new ArrayList<>();
    public final List<String> neededLibs = new ArrayList<>();
    public String soname;

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
        public String name;
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        public boolean dynamic;
        /** Resolved version name, null when unversioned. */
        public String versionName;
        /** true when this is the default (@@) version of the symbol. */
        public boolean versionDefault = true;

        public boolean isDefined() {
            return shndx != SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == SHN_COMMON;
        }
    }

    public static final class VerDef {
        public int index;
        public String name;
        public boolean base; // VER_DEF_FLAG_BASE
    }

    public static final class VerNeed {
        public int index;
        public String name;
        public String library;
    }

    public static final class Group {
        public String sectionName;
        public String signature;
        public boolean comdat;
        public final List<Integer> memberSections = new ArrayList<>();
    }

    public static final class Reloc {
        public String sectionName;
        public long offset;
        public int symIndex;
        public String symName;
        public long type;
        public Long addend; // null for REL
        public boolean fromDynSym;
    }

    public static String bindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND#" + bind;
        };
    }

    public static String visName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS#" + vis;
        };
    }

    public static String typeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_SECTION -> "SECTION";
            case STT_FILE -> "FILE";
            case STT_COMMON -> "COMMON";
            case STT_TLS -> "TLS";
            default -> "TYPE#" + type;
        };
    }
}
