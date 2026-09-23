package com.tribunal.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory model of a parsed ELF file or archive. Pure data, no I/O. */
public final class Elf {

    public enum Kind { REL, EXEC, SHARED, ARCHIVE }

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_ABS = 0xfff1;

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

    public static final int GRP_COMDAT = 1;

    public static String bindName(int b) {
        return switch (b) { case STB_LOCAL -> "LOCAL"; case STB_GLOBAL -> "GLOBAL"; case STB_WEAK -> "WEAK"; default -> "BIND#" + b; };
    }

    public static String visName(int v) {
        return switch (v) { case STV_DEFAULT -> "DEFAULT"; case STV_INTERNAL -> "INTERNAL"; case STV_HIDDEN -> "HIDDEN"; case STV_PROTECTED -> "PROTECTED"; default -> "VIS#" + v; };
    }

    public static String typeName(int t) {
        return switch (t) { case STT_NOTYPE -> "NOTYPE"; case STT_OBJECT -> "OBJECT"; case STT_FUNC -> "FUNC"; case STT_COMMON -> "COMMON"; default -> "TYPE#" + t; };
    }

    public static final class Section {
        public String name;
        public int type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long entsize;
        public int index;
    }

    public static final class Symbol {
        public int index;            // index within its table
        public String table;         // "symtab" or "dynsym"
        public String name;
        public int bind;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        public String versionName;   // resolved from versym/verdef/verneed, null if none
        public boolean versionHidden; // non-default version (@ instead of @@)

        public boolean isUndef() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON || type == STT_COMMON; }
        public boolean isLocal() { return bind == STB_LOCAL; }
        public boolean isWeak() { return bind == STB_WEAK; }

        public String displayName() {
            if (versionName == null) return name;
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static final class Relocation {
        public String section;       // relocation section name, e.g. .rela.text
        public long offset;
        public long type;
        public int symbolIndex;      // index into the linked symbol table
        public String symbolTable;   // "symtab" or "dynsym"
        public String symbolName;    // resolved name (may be versioned display name)
        public String versionName;
        public long addend;
        public boolean hasAddend;
    }

    public static final class Group {
        public String sectionName;
        public String signature;
        public boolean comdat;
        public List<Integer> memberSections = new ArrayList<>();
    }

    public static final class VersionDef {
        public int index;            // versym index (ndx)
        public String name;
        public boolean hidden;       // non-default
    }

    public static final class VersionNeed {
        public String library;       // vn_file
        public String name;          // vna_name
        public int other;            // versym index used by references
    }

    public static final class ElfFile {
        public String name;          // logical name (file or archive(member))
        public Kind kind;
        public int machine;
        public List<Section> sections = new ArrayList<>();
        public List<Symbol> symbols = new ArrayList<>();      // .symtab
        public List<Symbol> dynSymbols = new ArrayList<>();  // .dynsym
        public List<Relocation> relocations = new ArrayList<>();
        public List<Group> groups = new ArrayList<>();
        public Map<Integer, VersionDef> versionDefs = new LinkedHashMap<>();
        public List<VersionNeed> versionNeeds = new ArrayList<>();

        public List<Symbol> allSymbols() {
            List<Symbol> all = new ArrayList<>(symbols);
            all.addAll(dynSymbols);
            return all;
        }
    }

    public static final class ArchiveMember {
        public String name;
        public byte[] content;
    }

    public static final class ArchiveFile {
        public String name;
        public List<ArchiveMember> members = new ArrayList<>();
    }

    /** Result of parsing an uploaded blob: exactly one of file/archive is set. */
    public static final class Parsed {
        public ElfFile file;
        public ArchiveFile archive;
    }

    private Elf() {}
}
