package com.tribunal.elf;

import java.util.ArrayList;
import java.util.List;

/** 解析后的 ELF 对象模型（只读视图）。 */
public class ElfFile {
    public String name;                 // 逻辑名（文件名 / archive member 名）
    public int elfClass;                // 1=32, 2=64
    public int type;                    // ET_*
    public int machine;
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();      // .symtab
    public final List<Symbol> dynSymbols = new ArrayList<>();   // .dynsym
    public final List<Verdef> verdefs = new ArrayList<>();
    public final List<Verneed> verneeds = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<Reloc> relocs = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();       // DT_NEEDED
    public String soname;

    public boolean isShared() { return type == Elf.ET_DYN; }
    public boolean isRelocatable() { return type == Elf.ET_REL; }
    public boolean isExecutable() { return type == Elf.ET_EXEC; }

    public static class Section {
        public int index;
        public long nameOff;
        public String name;
        public int type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long entsize;
    }

    public static class Symbol {
        public int tableIndex;          // 在所属符号表中的下标
        public boolean dynamic;         // 来自 .dynsym
        public String name;
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        public int versionIndex = -1;   // versym 原始值（去 hidden 位）
        public boolean versionHidden;   // 非默认版本（@ 而非 @@）
        public String versionName;      // 还原后的版本名

        public boolean isUndefined() { return shndx == Elf.SHN_UNDEF; }
        public boolean isCommon() { return shndx == Elf.SHN_COMMON; }
        public boolean isLocal() { return bind == Elf.STB_LOCAL; }
        public boolean isWeak() { return bind == Elf.STB_WEAK; }

        /** 动态符号的语义键：名称 + 版本（若有）。 */
        public String key() {
            return versionName == null ? name : name + "@" + versionName;
        }

        public String display() {
            String v = versionName == null ? "" : (versionHidden ? "@" : "@@") + versionName;
            return name + v;
        }
    }

    public static class Verdef {
        public int index;               // vd_ndx
        public int flags;
        public String name;             // 第一 verdaux
        public final List<String> aliases = new ArrayList<>();
    }

    public static class Verneed {
        public String file;             // vn_file
        public final List<Vernaux> entries = new ArrayList<>();
    }

    public static class Vernaux {
        public String name;
        public int other;               // vna_other -> versym 索引
        public int flags;
    }

    public static class Group {
        public String signature;
        public boolean comdat;
        public final List<Integer> sectionIndices = new ArrayList<>();
    }

    public static class Reloc {
        public String sectionName;      // 重定位节名（如 .rela.text）
        public int targetSection;       // sh_info
        public long offset;
        public int symIndex;            // r_info 中的符号下标
        public long relocType;
        public Long addend;             // RELA 有值，REL 为 null
        public boolean dynamic;         // 符号来自 .dynsym
    }
}
