package dev.tribunal.elf;

/** ELF constants used by the parser and resolver. */
public final class ElfConstants {
    private ElfConstants() {}

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;

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
    public static final int SHN_XINDEX = 0xffff;

    public static final int GRP_COMDAT = 0x1;

    public static final long VER_NDX_LOCAL = 0L;
    public static final long VER_NDX_GLOBAL = 1L;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final long VER_FLG_BASE = 0x1L;
    public static final long VER_FLG_WEAK = 0x2L;
    public static final long VER_DEF_IDX = 0x1;
    public static final long VER_NDX = 0x2;
    public static final long VER_NEED_AUX = 0x1;
    public static final long VER_NEED_HIDDEN = 0x4;

    // x86_64 relocation types
    public static final long R_X86_64_COPY = 5;
    public static final long R_X86_64_GLOB_DAT = 6;
    public static final long R_X86_64_JUMP_SLOT = 7;
    public static final long R_X86_64_RELATIVE = 8;
    public static final long R_X86_64_64 = 1;
    public static final long R_X86_64_PC32 = 2;
    public static final long R_X86_64_PLT32 = 4;

    // dynamic tags
    public static final long DT_NULL = 0;
    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;
    public static final long DT_VERSYM = 0x6fffffff;
    public static final long DT_VERDEF = 0x6ffffffc;
    public static final long DT_VERDEFNUM = 0x6ffffffd;
    public static final long DT_VERNEED = 0x6ffffffe;
    public static final long DT_VERNEEDNUM = 0x6fffffff;

    public static String bindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND" + bind;
        };
    }

    public static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS" + vis;
        };
    }
}
