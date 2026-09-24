package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed in-memory representation of one ELF object (read-only view). */
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
    public static final int STT_COMMON = 5;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public String name;
    public String archiveMemberOf;
    public int elfClass; // 1 = 32-bit, 2 = 64-bit
    public int elfType;
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();
    public final List<Symbol> dynSymbols = new ArrayList<>();
    public final List<VersionDef> versionDefs = new ArrayList<>();
    public final List<VersionNeed> versionNeeds = new ArrayList<>();
    public final List<RelocSection> relocations = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;

    public String kindName() {
        return switch (elfType) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            default -> "UNKNOWN(" + elfType + ")";
        };
    }

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
        public long entsize;
    }

    public static final class Symbol {
        public int index;
        public String table; // "symtab" or "dynsym"
        public String name;
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        /** Resolved version name, or null when unversioned. */
        public String version;
        /** True when the version is non-default (name@VER instead of name@@VER). */
        public boolean versionHidden;

        public boolean isUndefined() {
            return shndx == SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == SHN_COMMON;
        }

        public String bindName() {
            return switch (bind) {
                case STB_LOCAL -> "LOCAL";
                case STB_GLOBAL -> "GLOBAL";
                case STB_WEAK -> "WEAK";
                default -> "BIND(" + bind + ")";
            };
        }

        public String visibilityName() {
            return switch (visibility) {
                case STV_DEFAULT -> "DEFAULT";
                case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN";
                case STV_PROTECTED -> "PROTECTED";
                default -> "VIS(" + visibility + ")";
            };
        }
    }

    public static final class VersionDef {
        public int index;
        public String name;
        public String parent; // predecessor version, may be null
    }

    public static final class VersionNeed {
        public String file;
        public int index; // vna_other, the versym index
        public String name;
    }

    public static final class Reloc {
        public long offset;
        public long type;
        public int symIndex;
        public long addend;
        public boolean hasAddend;
    }

    public static final class RelocSection {
        public String name;       // relocation section name, e.g. .rela.text
        public String targetSection; // section the relocations apply to
        public boolean symtabLink; // true when linked to .symtab, false for .dynsym
        public final List<Reloc> relocs = new ArrayList<>();
    }

    public static final class Group {
        public String signature;
        public boolean comdat;
        public final List<Integer> memberSections = new ArrayList<>();
    }
}
