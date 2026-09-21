package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed ELF64 (little-endian) object model. */
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
    public static final int SHN_COMMON = 0xfff2;

    public String name = "";
    public int elfType;
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Sym> symbols = new ArrayList<>();
    public final List<Sym> dynSymbols = new ArrayList<>();
    public int[] versym;
    /** version index -> defined version name (from .gnu.version_d). */
    public final Map<Integer, String> verdefs = new LinkedHashMap<>();
    /** version index (vna_other) -> needed version (from .gnu.version_r). */
    public final Map<Integer, Need> verneeds = new LinkedHashMap<>();
    public final List<Reloc> relocs = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;

    public String typeName() {
        return switch (elfType) {
            case ET_REL -> "ET_REL";
            case ET_EXEC -> "ET_EXEC";
            case ET_DYN -> "ET_DYN";
            default -> "ET_" + elfType;
        };
    }

    /** Symbols that participate in linkage for this object kind. */
    public List<Sym> linkSymbols() {
        if (elfType == ET_REL) {
            return symbols;
        }
        return dynSymbols.isEmpty() ? symbols : dynSymbols;
    }

    public static final class Section {
        public int index;
        public String name;
        public int type;
        public long flags;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long entsize;
    }

    public static final class Sym {
        public int index;
        public String name;
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        public boolean dyn;

        public boolean isUndefined() {
            return shndx == SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == SHN_COMMON;
        }

        public boolean isDefined() {
            return shndx != SHN_UNDEF;
        }

        public String bindName() {
            return switch (bind) {
                case STB_LOCAL -> "LOCAL";
                case STB_GLOBAL -> "GLOBAL";
                case STB_WEAK -> "WEAK";
                default -> "BIND_" + bind;
            };
        }

        public String visName() {
            return switch (visibility) {
                case STV_DEFAULT -> "DEFAULT";
                case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN";
                case STV_PROTECTED -> "PROTECTED";
                default -> "VIS_" + visibility;
            };
        }
    }

    public static final class Reloc {
        public String section;
        public long offset;
        public int symIndex;
        public boolean dynSym;
        public int type;
        public long addend;
    }

    public static final class Group {
        public String signature;
        public boolean comdat;
        public List<Integer> members = new ArrayList<>();
    }

    public static final class Need {
        public String file;
        public String version;

        public Need(String file, String version) {
            this.file = file;
            this.version = version;
        }
    }
}
