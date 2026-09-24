package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ELF 解析结果模型（不可变语义：解析后不再修改，除裁决期 discarded 标记）。 */
public final class ElfModel {

    public enum Kind { REL, DYN, EXEC, ARCHIVE, UNKNOWN }

    public enum Bind { LOCAL, GLOBAL, WEAK, OTHER }

    public enum Vis { DEFAULT, INTERNAL, HIDDEN, PROTECTED }

    public enum SymType { NOTYPE, OBJECT, FUNC, SECTION, TLS, OTHER }

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public static final class Sym {
        public int index;
        public String name;
        public Bind bind = Bind.LOCAL;
        public SymType type = SymType.NOTYPE;
        public Vis visibility = Vis.DEFAULT;
        public int shndx;
        public long value;
        public long size;
        public boolean dyn;
        public Integer versionIndex;
        public boolean versionHidden; // true => name@ver（非默认），false => name@@ver
        public String versionName;
        public boolean discarded; // COMDAT 去重被丢弃

        public boolean defined() {
            return shndx != SHN_UNDEF;
        }

        public boolean common() {
            return shndx == SHN_COMMON;
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
        public int symIndex;
        public String symbol;
        public long type;
        public long addend;
        public boolean dyn;

        public String describe(String objectName) {
            return objectName + ":" + section + "+0x" + Long.toHexString(offset)
                    + " type=" + type + " sym=" + (symbol == null ? ("#" + symIndex) : symbol);
        }
    }

    public static final class Group {
        public String signature;
        public boolean comdat;
        public List<Integer> sections = new ArrayList<>();
    }

    public static final class VerNeed {
        public String file;
        public String version;
        public int index;
    }

    public static final class ElfObject {
        public String name;
        public Kind kind = Kind.UNKNOWN;
        public boolean is64;
        public String machine = "";
        public List<Sym> symbols = new ArrayList<>();     // .symtab
        public List<Sym> dynSymbols = new ArrayList<>();  // .dynsym
        public List<Reloc> relocs = new ArrayList<>();
        public Map<Integer, String> verDefs = new LinkedHashMap<>();      // 版本索引 -> 版本名（本对象定义）
        public Map<Integer, String> verNeedByIndex = new LinkedHashMap<>(); // 版本索引 -> 版本名（本对象需求）
        public List<VerNeed> verNeeds = new ArrayList<>();
        public List<Group> groups = new ArrayList<>();
        public List<ElfObject> members = new ArrayList<>(); // archive 成员
        public boolean extracted;                           // archive 成员是否被抽取
        public boolean corrupt;
        public String error;
        public String sha256;
        public transient byte[] raw;

        /** 参与全局符号表的符号视图：DYN 用 dynsym，其余用 symtab。 */
        public List<Sym> globalView() {
            return kind == Kind.DYN && !dynSymbols.isEmpty() ? dynSymbols : symbols;
        }

        public List<Sym> allSymbols() {
            List<Sym> all = new ArrayList<>(symbols);
            for (Sym s : dynSymbols) {
                if (!all.contains(s)) {
                    all.add(s);
                }
            }
            return all;
        }
    }

    private ElfModel() {
    }
}
