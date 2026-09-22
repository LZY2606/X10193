package com.symbolcourt.elf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

public class ElfFile {
    public boolean is64;
    public boolean littleEndian;
    public int elfType;
    public int machine;
    public List<Section> sections = new ArrayList<>();
    public List<Sym> symtab = new ArrayList<>();
    public List<Sym> dynsym = new ArrayList<>();
    public List<VersionDef> versionDefs = new ArrayList<>();
    public List<VersionNeed> versionNeeds = new ArrayList<>();
    public List<Reloc> relocs = new ArrayList<>();
    public List<Group> groups = new ArrayList<>();

    @JsonIgnore
    public String typeName() {
        return switch (elfType) {
            case 1 -> "REL";
            case 2 -> "EXEC";
            case 3 -> "DYN";
            default -> "TYPE" + elfType;
        };
    }

    public static class Section {
        public int index;
        public String name = "";
        public long type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long entsize;
    }

    public static class Sym {
        public int index;
        public String name = "";
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        public boolean dyn;
        public String version;
        public boolean versionHidden;
        public String versionFile;

        @JsonIgnore
        public boolean isUndef() { return shndx == 0; }
        @JsonIgnore
        public boolean isCommon() { return shndx == 0xfff2; }
        @JsonIgnore
        public boolean isAbs() { return shndx == 0xfff1; }
        public String bindName() {
            return switch (bind) { case 0 -> "LOCAL"; case 1 -> "GLOBAL"; case 2 -> "WEAK"; default -> "B" + bind; };
        }
        public String typeName() {
            return switch (type) {
                case 0 -> "NOTYPE"; case 1 -> "OBJECT"; case 2 -> "FUNC"; case 3 -> "SECTION";
                case 4 -> "FILE"; case 5 -> "COMMON"; case 6 -> "TLS"; default -> "T" + type;
            };
        }
        public String visName() {
            return switch (visibility) {
                case 0 -> "DEFAULT"; case 1 -> "INTERNAL"; case 2 -> "HIDDEN"; case 3 -> "PROTECTED";
                default -> "V" + visibility;
            };
        }
    }

    public static class Reloc {
        public String section = "";
        public long offset;
        public int symIndex;
        public String symName = "";
        public long type;
        public long addend;
        public boolean rela;
        public boolean dyn;
    }

    public static class VersionDef {
        public int index;
        public String name = "";
        public int flags;
        public boolean base;
    }

    public static class VersionNeed {
        public String file = "";
        public List<Entry> entries = new ArrayList<>();

        public static class Entry {
            public int other;
            public String name = "";
            public int flags;
            public String file = "";
        }
    }

    public static class Group {
        public String signature = "";
        public boolean comdat;
        public List<Integer> members = new ArrayList<>();
    }
}
