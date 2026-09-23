package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** 解析后的 ELF 文件模型（只读数据）。 */
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
    public static final int STT_COMMON = 5;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_COMMON = 0xfff2;

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_VERDEF = 0x6ffffffd;
    public static final int SHT_VERNEED = 0x6ffffffe;
    public static final int SHT_VERSYM = 0x6fffffff;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_GROUP = 17;

    public int elfClass; // 1=32, 2=64
    public int type;
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();      // .symtab
    public final List<Symbol> dynSymbols = new ArrayList<>();   // .dynsym
    public final List<Reloc> relocations = new ArrayList<>();
    public final List<VersionDef> versionDefs = new ArrayList<>();
    public final List<VersionNeed> versionNeeds = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<String> neededLibs = new ArrayList<>();
    public String soname;

    public boolean isShared() { return type == ET_DYN; }
    public boolean isRelocatable() { return type == ET_REL; }

    public String typeName() {
        return switch (type) {
            case ET_REL -> "OBJECT";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "SHARED";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    public static class Section {
        public int index;
        public String name;
        public int type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long addralign;
        public long entsize;
    }

    public static class Symbol {
        public int index;          // 在其所属表中的索引
        public boolean dyn;        // true=dynsym, false=symtab
        public String name;
        public int bind;
        public int symType;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        public int versionIndex = -1;   // versym 还原（仅 dynsym）
        public boolean versionHidden;   // versym 第 15 位：非默认版本(@)
        public String versionName;      // 由 verdef/verneed 还原的版本名

        public boolean isUndefined() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON; }
        public boolean isLocal() { return bind == STB_LOCAL; }
        public boolean isWeak() { return bind == STB_WEAK; }

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

        /** 展示名：name@ver 或 name@@ver */
        public String displayName() {
            if (versionName == null) return name;
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static class Reloc {
        public String sectionName; // 重定位作用的目标节
        public long offset;
        public int type;
        public int symIndex;
        public boolean dynSym;     // 符号索引指向 dynsym 还是 symtab
        public long addend;
        public boolean hasAddend;
        public String symbolName;  // 解析后的符号名（含版本）
    }

    public static class VersionDef {
        public int index;
        public String name;
        public boolean base;
    }

    public static class VersionNeed {
        public String file;
        public int index;
        public String name;
    }

    public static class Group {
        public String signature;      // 组签名符号名
        public boolean comdat;
        public String sectionName;    // SHT_GROUP 节名
        public List<Integer> members = new ArrayList<>(); // 成员节索引
    }
}
