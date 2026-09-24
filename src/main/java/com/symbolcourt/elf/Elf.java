package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed, read-only representation of one ELF object. */
public final class Elf {
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int ET_REL = 1;
    public static final int ET_DYN = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_ABS = 0xfff1;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;

    public boolean is64;
    public boolean littleEndian = true;
    public int elfType;
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();   // .symtab
    public final List<Symbol> dynSymbols = new ArrayList<>(); // .dynsym
    public final List<Reloc> relocs = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<VerDef> verDefs = new ArrayList<>();
    public final List<VerNeed> verNeeds = new ArrayList<>();
    /** versym[i] corresponds to dynSymbols.get(i); -1 when absent. */
    public int[] versym = new int[0];

    public boolean isShared() { return elfType == ET_DYN; }
    public boolean isRelocatable() { return elfType == ET_REL; }

    public static final class Section {
        public String name;
        public int type;
        public long flags;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long entsize;
    }

    public static final class Symbol {
        public String name;
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        /** originating table: "symtab" or "dynsym". */
        public String table;
        /** index within its table. */
        public int index;

        public boolean isUndefined() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON; }
        public boolean isLocal() { return bind == STB_LOCAL; }

        public String bindName() {
            return switch (bind) {
                case STB_LOCAL -> "LOCAL";
                case STB_GLOBAL -> "GLOBAL";
                case STB_WEAK -> "WEAK";
                default -> "BIND#" + bind;
            };
        }

        public String visibilityName() {
            return switch (visibility) {
                case STV_DEFAULT -> "DEFAULT";
                case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN";
                case STV_PROTECTED -> "PROTECTED";
                default -> "VIS#" + visibility;
            };
        }
    }

    public static final class Reloc {
        public String sectionName;
        public long offset;
        public int symIndex;   // index into the linked symbol table
        public int type;
        public long addend;
        public boolean hasAddend;
        public String symTable; // "symtab" or "dynsym"
    }

    public static final class Group {
        public String sectionName;
        public boolean comdat;
        public String signature;
        public List<Integer> memberSections = new ArrayList<>();
    }

    public static final class VerDef {
        public int index;       // vd_ndx
        public String name;     // first verdaux name
        public boolean hidden;  // VER_FLG_BASE == 0 means base; hidden flag tracked via versym
        public boolean base;
    }

    public static final class VerNeed {
        public String file;     // needed shared object file name
        public String name;     // version name
        public int other;       // vna_other -> versym index
    }
}
