package gsb.elf;

import java.util.ArrayList;
import java.util.List;

/** In-memory model of a parsed ELF object. Pure data; the original bytes stay untouched. */
public final class ElfModel {
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
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public String name = "?";
    public int elfClass; // 1 = 32-bit, 2 = 64-bit
    public int elfType;
    public boolean littleEndian = true;
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();      // SHT_SYMTAB
    public final List<Symbol> dynSymbols = new ArrayList<>();   // SHT_DYNSYM
    public final List<VersionDef> versionDefs = new ArrayList<>();
    public final List<VersionNeed> versionNeeds = new ArrayList<>();
    public final List<Reloc> relocations = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();

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
        public int index;          // index inside its own table
        public boolean dynamic;    // from .dynsym
        public String name;
        public int binding;
        public int type;
        public int visibility;
        public int shndx;
        public long value;
        public long size;
        /** Raw version index from .gnu.version (0=local, 1=global, >=2 real version). */
        public int versionIndex = -1;
        /** Version name restored from VERDEF/VERNEED; never left as a bare index. */
        public String versionName;
        /** true when the versym hidden bit (0x8000) is set: a non-default "@vers" definition. */
        public boolean versionHidden;

        public boolean isDefined() {
            return shndx != SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == SHN_COMMON || type == STT_COMMON;
        }

        public String bindingName() {
            return switch (binding) {
                case STB_LOCAL -> "LOCAL";
                case STB_GLOBAL -> "GLOBAL";
                case STB_WEAK -> "WEAK";
                default -> "BIND#" + binding;
            };
        }

        public String visibilityName() {
            return switch (visibility) {
                case STV_DEFAULT -> "DEFAULT";
                case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN";
                case STV_PROTECTED -> "PROTECTED";
                default -> "VIS#" + visibility;
            };
        }

        /** Display name with version restored, e.g. foo@@GLIBC_2.5 or foo@V1. */
        public String displayName() {
            if (versionName == null) return name;
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public static final class VersionDef {
        public int index;       // vd_ndx
        public String name;     // first verdaux name
        public boolean hidden;  // from versym hidden bit consumers
        public String parent;   // second verdaux, if any
    }

    public static final class VersionNeed {
        public String file;             // vn_file: needed soname
        public final List<Entry> entries = new ArrayList<>();
        public static final class Entry {
            public int other;   // vna_other: version index used by versym
            public String name; // vna_name
        }
    }

    public static final class Reloc {
        public String section;      // section the relocations apply to
        public long offset;
        public int type;
        public int symbolIndex;     // index into the linked symbol table
        public boolean dynamicTable; // symbol index refers to .dynsym
        public long addend;
        public boolean hasAddend;
    }

    public static final class Group {
        public String signature;    // group signature symbol name
        public boolean comdat;
        public final List<Integer> memberSections = new ArrayList<>();
    }
}
