package com.symcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** 解析后的 ELF 对象模型（只读视图，原二进制内容不落修改）。 */
public class ElfFile {
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_COMMON = 5;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public int elfClass; // 1=32, 2=64
    public boolean littleEndian = true;
    public int elfType;
    public int machine;
    public List<Section> sections = new ArrayList<>();
    public List<Symbol> symtab = new ArrayList<>();
    public List<Symbol> dynsym = new ArrayList<>();
    public List<VersionDef> verdefs = new ArrayList<>();
    public List<VersionNeed> verneeds = new ArrayList<>();
    public List<RelocSection> relocs = new ArrayList<>();
    public List<Group> groups = new ArrayList<>();
    public List<String> needed = new ArrayList<>();
    public String soname;

    public String typeName() {
        return switch (elfType) {
            case ET_REL -> "ET_REL";
            case ET_EXEC -> "ET_EXEC";
            case ET_DYN -> "ET_DYN";
            default -> "ET_" + elfType;
        };
    }

    public Section section(int index) {
        if (index < 0 || index >= sections.size()) return null;
        return sections.get(index);
    }

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

        public boolean groupMember() {
            return (flags & 0x200) != 0;
        }
    }

    public static final class Symbol {
        public int index;
        public String table; // "symtab" | "dynsym"
        public String name;
        public int bind;
        public int type;
        public int visibility;
        public int shndx; // 原始 st_shndx（已还原 XINDEX）
        public long value;
        public long size;
        // 版本还原结果（仅 dynsym）
        public String version;     // 版本名，无版本为 null
        public boolean versionHidden; // 非默认版本（单个 @）
        public String versionFile; // verneed 来源文件（引用侧）

        public boolean defined() {
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
                default -> "BIND_" + bind;
            };
        }

        public String typeName() {
            return switch (type) {
                case STT_NOTYPE -> "NOTYPE";
                case STT_OBJECT -> "OBJECT";
                case STT_FUNC -> "FUNC";
                case STT_SECTION -> "SECTION";
                case STT_COMMON -> "COMMON";
                default -> "TYPE_" + type;
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

        public String displayName() {
            if (version == null) return name;
            return name + (versionHidden ? "@" : "@@") + version;
        }
    }

    public static final class VersionDef {
        public int ndx;
        public String name;
        public int flags;
        public boolean base() { return (flags & 1) != 0; }
    }

    public static final class VersionNeed {
        public String file;
        public List<Entry> entries = new ArrayList<>();

        public static final class Entry {
            public int other;
            public String name;
        }
    }

    public static final class RelocSection {
        public String name;         // 重定位节名（如 .rela.text）
        public int targetSection;   // sh_info：作用目标节
        public String targetName;
        public boolean rela;
        public List<Reloc> relocs = new ArrayList<>();
    }

    public static final class Reloc {
        public long offset;
        public int symIndex;
        public String symName;   // 解析后的符号名（symIndex=0 时为空）
        public String symVersion; // 引用符号的版本（若有）
        public long type;
        public long addend;
        public boolean local;    // 引用的是 LOCAL 符号
    }

    public static final class Group {
        public String signature;
        public boolean comdat;
        public List<Integer> members = new ArrayList<>(); // section indices
        public List<String> memberNames = new ArrayList<>();
    }
}
