package com.symcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory model of a parsed ELF file. Only what the adjudicator needs. */
public final class Elf {

    // e_type
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    // section types
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    // special section indices
    public static final int SHN_UNDEF = 0;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    // bindings
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    // symbol types
    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_FILE = 4;
    public static final int STT_COMMON = 5;
    public static final int STT_GNU_IFUNC = 10;

    // visibility (st_other & 3)
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int GRP_COMDAT = 1;
    public static final int VER_FLG_BASE = 1;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_VERSION_MASK = 0x7fff;

    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;

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
        public int index;          // index inside its own table
        public boolean dynamic;    // from .dynsym
        public String name = "";
        public int bind;
        public int type;
        public int other;          // visibility lives in low 2 bits
        public int shndx;
        public long value;
        public long size;
        public Integer versymRaw;  // raw .gnu.version half, null if absent
        public String versionName; // resolved version name, null if none
        public boolean versionHidden; // non-default version (@, not @@)

        public int visibility() { return other & 3; }
        public boolean isUndefined() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON; }
        public boolean isDefined() { return !isUndefined() && !isCommon(); }

        public String bindName() {
            return switch (bind) {
                case STB_LOCAL -> "LOCAL";
                case STB_GLOBAL -> "GLOBAL";
                case STB_WEAK -> "WEAK";
                default -> "bind#" + bind;
            };
        }

        public String visibilityName() {
            return switch (visibility()) {
                case STV_DEFAULT -> "DEFAULT";
                case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN";
                case STV_PROTECTED -> "PROTECTED";
                default -> "vis#" + visibility();
            };
        }

        /** Display form: name, name@@VER (default) or name@VER (non-default). */
        public String displayName() {
            if (versionName == null) return name;
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static final class VerDef {
        public int index;          // vd_ndx
        public String name;
        public boolean base;       // VER_FLG_BASE
        public boolean hidden;     // appeared with hidden bit in versym
    }

    public static final class VerNeedEntry {
        public int other;          // vna_other (version index used by versym)
        public String name;        // vna_name
    }

    public static final class VerNeed {
        public String file;        // vn_file
        public List<VerNeedEntry> entries = new ArrayList<>();
    }

    public static final class Group {
        public int sectionIndex;
        public String signature;
        public boolean comdat;
        public List<Integer> memberSections = new ArrayList<>();
    }

    public static final class Reloc {
        public int targetSection;  // section the relocations apply to (sh_info)
        public long offset;
        public long type;
        public int symIndex;       // index into the linked symbol table
        public long addend;
        public boolean rela;
        public boolean dynamic;    // linked against .dynsym
    }

    public static final class ElfFile {
        public String sourceName = "";
        public int elfClass;       // 1 = 32-bit, 2 = 64-bit
        public boolean littleEndian = true;
        public int machine;
        public int elfType;
        public List<Section> sections = new ArrayList<>();
        public List<Symbol> symbols = new ArrayList<>();      // .symtab
        public List<Symbol> dynSymbols = new ArrayList<>();   // .dynsym
        public Map<Integer, VerDef> verDefs = new LinkedHashMap<>(); // ndx -> def
        public List<VerNeed> verNeeds = new ArrayList<>();
        public List<Group> groups = new ArrayList<>();
        public List<Reloc> relocs = new ArrayList<>();
        public List<String> neededLibs = new ArrayList<>();
        public String soname;

        public String typeName() {
            return switch (elfType) {
                case ET_REL -> "REL";
                case ET_EXEC -> "EXEC";
                case ET_DYN -> "DYN";
                default -> "type#" + elfType;
            };
        }

        /** Symbols participating in linking: dynsym for shared objects, symtab otherwise. */
        public List<Symbol> linkSymbols() {
            return elfType == ET_DYN && !dynSymbols.isEmpty() ? dynSymbols : symbols;
        }

        public Symbol symbolFor(Reloc r) {
            List<Symbol> table = r.dynamic ? dynSymbols : symbols;
            if (r.symIndex <= 0 || r.symIndex >= table.size()) return null;
            return table.get(r.symIndex);
        }
    }

    private Elf() {}
}
