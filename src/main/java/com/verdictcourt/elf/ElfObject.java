package com.verdictcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed in-memory model of one ELF object. Pure data, JSON-serializable. */
public class ElfObject {
    public String name;
    public int elfClass;          // 1 = 32-bit, 2 = 64-bit
    public int elfType;           // ET_*
    public int machine;
    public boolean littleEndian = true;
    public List<Sec> sections = new ArrayList<>();
    public List<Sym> symbols = new ArrayList<>();     // SHT_SYMTAB
    public List<Sym> dynSymbols = new ArrayList<>();  // SHT_DYNSYM
    public List<Rel> relocations = new ArrayList<>();
    public List<VerDef> verDefs = new ArrayList<>();
    public List<VerNeed> verNeeds = new ArrayList<>();
    public List<Group> groups = new ArrayList<>();
    public List<String> needed = new ArrayList<>();   // DT_NEEDED
    public String soname;

    public List<Sym> allSymbols() {
        List<Sym> all = new ArrayList<>(symbols);
        all.addAll(dynSymbols);
        return all;
    }

    /** Symbols considered for linking: dynsym for shared objects, symtab otherwise (fallback to whichever exists). */
    public List<Sym> linkSymbols() {
        if (elfType == ElfConst.ET_DYN && !dynSymbols.isEmpty()) return dynSymbols;
        if (!symbols.isEmpty()) return symbols;
        return dynSymbols;
    }

    public static class Sec {
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
    }

    public static class Sym {
        public String name = "";
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        public int tableIndex;
        public boolean dynamic;
        public int versionIndex = -1;   // raw versym value (with hidden bit), -1 = no versym section
        public String versionName;      // resolved version name, null if unversioned
        public String versionFrom;      // for verneed: the file the version is needed from
        public boolean versionHidden;   // non-default version (name@ver, not name@@ver)
        public boolean discarded;       // dropped by COMDAT dedup
        public String discardedReason;

        public boolean isUndef() { return shndx == ElfConst.SHN_UNDEF; }
        public boolean isCommon() { return shndx == ElfConst.SHN_COMMON; }
        public boolean isDef() { return !isUndef() && !isCommon(); }

        public String displayName() {
            if (versionName == null) return name;
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static class Rel {
        public String section;      // name of section the relocation applies to
        public long offset;
        public int symIndex;        // index into the linked symbol table
        public boolean symDynamic;  // linked table was dynsym
        public int type;
        public long addend;
        public boolean hasAddend;
        public String symName;      // resolved for display
    }

    public static class VerDef {
        public int index;           // vd_ndx
        public String name;         // first aux name
        public boolean base;        // VER_FLG_BASE
        public List<String> aliases = new ArrayList<>();
    }

    public static class VerNeed {
        public String file;
        public List<VerNeedAux> aux = new ArrayList<>();
    }

    public static class VerNeedAux {
        public int other;           // vna_other: version index used by versym
        public String name;
    }

    public static class Group {
        public String signature;
        public boolean comdat;
        public List<Integer> sectionIndices = new ArrayList<>();
    }
}
