package com.verdictcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** In-memory model of a parsed ELF object. All structures are bounds-checked at parse time. */
public final class ElfModel {

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;

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

    public static final int SHN_UNDEF = 0;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int GRP_COMDAT = 1;

    public static final class Section {
        public int index;
        public String name = "";
        public int type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long entsize;
    }

    public static final class Symbol {
        public int index;            // index within its own table
        public boolean dynamic;      // dynsym vs symtab
        public String name = "";
        public int bind;
        public int type;
        public int visibility;
        public int shndx;            // resolved (SHN_XINDEX expanded)
        public long value;
        public long size;
        public String versionName;   // resolved from versym index, null when unversioned
        public boolean versionHidden; // '@' non-default version marker

        public boolean isDefined() {
            return shndx != SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == SHN_COMMON;
        }

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

    public static final class VersionDef {
        public int index;            // vd_ndx (without hidden bit)
        public boolean hidden;       // base flag from versym hidden bit is per-symbol; defs carry names
        public String name = "";
        public int flags;
    }

    public static final class VersionNeedEntry {
        public String name = "";
        public int other;            // vna_other: index used in versym
    }

    public static final class VersionNeed {
        public String file = "";
        public List<VersionNeedEntry> entries = new ArrayList<>();
    }

    public static final class Reloc {
        public String sectionName = "";
        public long offset;
        public int symIndex;
        public long type;
        public Long addend;          // null for REL
        public boolean fromDynamicTable; // reloc section links dynsym
        public String symbolName = "";
        public String symbolVersion; // resolved later
    }

    public static final class Group {
        public String signature = "";
        public boolean comdat;
        public List<String> memberSections = new ArrayList<>();
    }

    public int elfClass;             // 1 = 32-bit, 2 = 64-bit
    public boolean littleEndian;
    public int type;
    public int machine;
    public List<Section> sections = new ArrayList<>();
    public List<Symbol> symtab = new ArrayList<>();
    public List<Symbol> dynsym = new ArrayList<>();
    public List<VersionDef> versionDefs = new ArrayList<>();
    public List<VersionNeed> versionNeeds = new ArrayList<>();
    public List<Reloc> relocs = new ArrayList<>();
    public List<Group> groups = new ArrayList<>();

    public String typeName() {
        return switch (type) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            default -> "TYPE#" + type;
        };
    }

    /** Sections belonging to a COMDAT group keyed by signature. */
    public List<Group> comdatGroups() {
        List<Group> out = new ArrayList<>();
        for (Group g : groups) {
            if (g.comdat) out.add(g);
        }
        return out;
    }
}
