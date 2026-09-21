package court.elf;

/** ELF constants used by the symbol court. Names mirror the ELF spec / GNU elf.h. */
public final class Const {
    private Const() {}

    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;

    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

    public static final int SHT_NULL = 0;
    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_NOTE = 7;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;

    public static final int SHF_WRITE = 0x1;
    public static final int SHF_ALLOC = 0x2;
    public static final int SHF_EXECINSTR = 0x4;
    public static final int SHF_GROUP = 0x200;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_FILE = 4;
    public static final int STT_COMMON = 5;
    public static final int STT_TLS = 6;
    public static final int STT_GNU_IFUNC = 10;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int STN_UNDEF = 0;

    // special version section indices (.gnu.version)
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    // ver_flags
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;

    // .dynamic tags we care about
    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_VERDEF = 0x6ffffffc;
    public static final int DT_VERDEFNUM = 0x6ffffffd;
    public static final int DT_VERNEED = 0x6ffffffe;
    public static final int DT_VERNEEDNUM = 0x6fffffff;
    public static final int DT_VERSYM = 0x6ffffff0;

    public static final int GRP_COMDAT = 0x1;

    public static String bindingName(int b) {
        switch (b) {
            case STB_LOCAL: return "LOCAL";
            case STB_GLOBAL: return "GLOBAL";
            case STB_WEAK: return "WEAK";
            default: return "STB?" + b;
        }
    }

    public static String visibilityName(int v) {
        switch (v) {
            case STV_DEFAULT: return "DEFAULT";
            case STV_INTERNAL: return "INTERNAL";
            case STV_HIDDEN: return "HIDDEN";
            case STV_PROTECTED: return "PROTECTED";
            default: return "STV?" + v;
        }
    }

    public static String typeName(int t) {
        switch (t) {
            case STT_NOTYPE: return "NOTYPE";
            case STT_OBJECT: return "OBJECT";
            case STT_FUNC: return "FUNC";
            case STT_SECTION: return "SECTION";
            case STT_FILE: return "FILE";
            case STT_COMMON: return "COMMON";
            case STT_TLS: return "TLS";
            case STT_GNU_IFUNC: return "GNU_IFUNC";
            default: return "STT?" + t;
        }
    }
}
