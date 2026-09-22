package com.example.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed view of one ELF object/shared object, or an ar archive containing members. */
public final class ElfFile {

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

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
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;

    public static final int SHF_GROUP = 0x200;

    public String name;
    public int elfClass; // 1 = ELF32, 2 = ELF64
    public int endian; // 1 = little, 2 = big
    public int elfType;
    public int machine;
    public boolean archive;
    public String parseError;

    public final List<Section> sections = new ArrayList<>();
    public final List<Sym> symtab = new ArrayList<>();
    public final List<Sym> dynsym = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<VerDef> verDefs = new ArrayList<>();
    public final List<VerNeed> verNeeds = new ArrayList<>();
    public int[] versym = new int[0];
    public final List<Reloc> relocs = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;

    public final List<ElfFile> members = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public String kindName() {
        if (archive) return "ARCHIVE";
        switch (elfType) {
            case ET_REL: return "OBJECT";
            case ET_EXEC: return "EXECUTABLE";
            case ET_DYN: return "SHARED";
            default: return "UNKNOWN";
        }
    }

    public static final class Section {
        public int index;
        public String name = "";
        public long type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public long link;
        public long info;
        public long addralign;
        public long entsize;

        public boolean isGroupMember() {
            return (flags & SHF_GROUP) != 0;
        }
    }

    public static final class Sym {
        public int index;
        public String name = "";
        public long value;
        public long size;
        public int info;
        public int other;
        public int shndx;
        public boolean dynamic;

        public int bind() {
            return info >> 4;
        }

        public int type() {
            return info & 0xf;
        }

        public int visibility() {
            return other & 0x3;
        }

        public boolean isUndef() {
            return shndx == SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == SHN_COMMON;
        }

        public boolean isDefined() {
            return !isUndef();
        }

        public String bindName() {
            switch (bind()) {
                case STB_LOCAL: return "LOCAL";
                case STB_GLOBAL: return "GLOBAL";
                case STB_WEAK: return "WEAK";
                default: return "BIND" + bind();
            }
        }

        public String visibilityName() {
            switch (visibility()) {
                case STV_DEFAULT: return "DEFAULT";
                case STV_INTERNAL: return "INTERNAL";
                case STV_HIDDEN: return "HIDDEN";
                case STV_PROTECTED: return "PROTECTED";
                default: return "VIS" + visibility();
            }
        }

        public String typeName() {
            switch (type()) {
                case STT_NOTYPE: return "NOTYPE";
                case STT_OBJECT: return "OBJECT";
                case STT_FUNC: return "FUNC";
                default: return "TYPE" + type();
            }
        }
    }

    public static final class Group {
        public int sectionIndex;
        public String signature = "";
        public long flags; // 1 = COMDAT
        public final List<Integer> memberSections = new ArrayList<>();
    }

    public static final class VerDef {
        public int ndx;
        public String name = "";
        public int count;
    }

    public static final class VerNeed {
        public String file = "";
        public final List<VerNeedAux> aux = new ArrayList<>();
    }

    public static final class VerNeedAux {
        public int other; // version index referencing the remote verdef
        public String name = "";
    }

    public static final class Reloc {
        public int relocSectionIndex;
        public int sectionIndex;
        public String sectionName = "";
        public long offset;
        public long type;
        public int symIndex;
        public long addend;
        public boolean rela;

        public String typeName(int machine) {
            return RelocTypes.name(machine, type);
        }
    }
}
