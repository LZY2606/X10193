package tribunal.elf;

/** Constants and small enumerations shared by the hand-written ELF reader. */
public final class Elf {
    private Elf() {}

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;

    public static final int SHF_ALLOC = 0x2;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_GNU_UNIQUE = 10;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_FILE = 4;
    public static final int STT_TLS = 6;
    public static final int STT_GNU_IFUNC = 10;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int GRP_COMDAT = 0x1;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_STRTAB = 5;
    public static final int DT_SONAME = 14;
    public static final int DT_FLAGS_1 = 0x6ffffffb;

    public static final int DF_1_NOW = 0x1;
    public static final int DF_1_PIE = 0x08000000;

    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final short VERSYM_HIDDEN = (short) 0x8000;
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    public enum Bind {
        LOCAL, GLOBAL, WEAK, GNU_UNIQUE, OTHER;
        public static Bind of(int stBind) {
            switch (stBind) {
                case STB_LOCAL: return LOCAL;
                case STB_GLOBAL: return GLOBAL;
                case STB_WEAK: return WEAK;
                case STB_GNU_UNIQUE: return GNU_UNIQUE;
                default: return OTHER;
            }
        }
        public boolean strongGlobal() { return this == GLOBAL || this == GNU_UNIQUE; }
    }

    public enum Kind { NOTYPE, OBJECT, FUNC, SECTION, FILE, TLS, IFUNC, OTHER }

    public static Kind kindOf(int stType) {
        switch (stType) {
            case STT_NOTYPE: return Kind.NOTYPE;
            case STT_OBJECT: return Kind.OBJECT;
            case STT_FUNC: return Kind.FUNC;
            case STT_SECTION: return Kind.SECTION;
            case STT_FILE: return Kind.FILE;
            case STT_TLS: return Kind.TLS;
            case STT_GNU_IFUNC: return Kind.IFUNC;
            default: return Kind.OTHER;
        }
    }

    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED }

    public static Visibility visibilityOf(int stOther) {
        switch (stOther & 3) {
            case STV_INTERNAL: return Visibility.INTERNAL;
            case STV_HIDDEN: return Visibility.HIDDEN;
            case STV_PROTECTED: return Visibility.PROTECTED;
            default: return Visibility.DEFAULT;
        }
    }
}
