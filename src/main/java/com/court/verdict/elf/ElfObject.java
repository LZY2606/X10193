package com.court.verdict.elf;

import java.util.ArrayList;
import java.util.List;

/** 解析后的 ELF 对象模型（只读数据，不持有原字节）。 */
public final class ElfObject {
    public String name;
    public int elfType;
    public int machine;
    public boolean is64;
    public boolean littleEndian;
    public final List<Section> sections = new ArrayList<>();
    public final List<Sym> symtab = new ArrayList<>();
    public final List<Sym> dynsym = new ArrayList<>();
    public final List<Reloc> relocations = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;
    public final List<VerDef> verDefs = new ArrayList<>();
    public final List<VerNeed> verNeeds = new ArrayList<>();

    public String kindName() {
        return switch (elfType) {
            case ElfConstants.ET_REL -> "REL";
            case ElfConstants.ET_EXEC -> "EXEC";
            case ElfConstants.ET_DYN -> "DYN";
            default -> "OTHER(" + elfType + ")";
        };
    }

    public boolean isShared() {
        return elfType == ElfConstants.ET_DYN;
    }

    public static final class Section {
        public String name;
        public int type;
        public long flags;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long addralign;
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
        public boolean dynamic;
        public int versionRaw = -1;
        public String versionName;
        public boolean versionHidden;
        public String versionFile;

        public boolean isUndefined() {
            return shndx == ElfConstants.SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == ElfConstants.SHN_COMMON;
        }

        public boolean isHidden() {
            return visibility == ElfConstants.STV_HIDDEN || visibility == ElfConstants.STV_INTERNAL;
        }

        public boolean isProtected() {
            return visibility == ElfConstants.STV_PROTECTED;
        }

        public String displayName() {
            if (versionName == null) {
                return name;
            }
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static final class Reloc {
        public String section;
        public long offset;
        public long type;
        public int symIndex;
        public String symName;
        public long addend;
        public boolean rela;
        public boolean dynamic;
    }

    public static final class Group {
        public String signature;
        public long flags;
        public final List<String> sectionNames = new ArrayList<>();
    }

    public static final class VerDef {
        public int index;
        public String name;
        public boolean base;
    }

    public static final class VerNeed {
        public String file;
        public final List<Vernaux> versions = new ArrayList<>();
    }

    public static final class Vernaux {
        public int other;
        public String name;
    }
}
