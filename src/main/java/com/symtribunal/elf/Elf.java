package com.symtribunal.elf;

/** ELF constants used by the parser, fixture builder and resolver. */
public final class Elf {
    private Elf() {}

    public static final int EI_CLASS = 4;
    public static final int EI_DATA = 5;
    public static final int EI_NIDENT = 16;
    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;

    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int EM_386 = 3;
    public static final int EM_ARM = 40;
    public static final int EM_X86_64 = 62;
    public static final int EM_AARCH64 = 183;

    public static final int SHT_NULL = 0;
    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_versym = 0x6fffffff;
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;

    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_EXCLUDE = 0x80000000L;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_XINDEX = 0xffff;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_GNU_UNIQUE = 10;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_COMMON = 5;
    public static final int STT_GNU_IFUNC = 10;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final long DT_NULL = 0;
    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;
    public static final long DT_VERSYM = 0x6ffffff0L;
    public static final long DT_VERDEF = 0x6ffffffbL;
    public static final long DT_VERDEFNUM = 0x6ffffffcL;
    public static final long DT_VERNEED = 0x6ffffffeL;
    public static final long DT_VERNEEDNUM = 0x6fffffffL;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_VERSION = 0x7fff;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;

    public static final int GRP_COMDAT = 0x1;

    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_AARCH64_COPY = 1024;
    public static final int R_AARCH64_GLOB_DAT = 1025;
    public static final int R_AARCH64_JUMP_SLOT = 1026;

    public static String bindingName(int binding) {
        return switch (binding) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            case STB_GNU_UNIQUE -> "UNIQUE";
            default -> "BIND(" + binding + ")";
        };
    }

    public static String typeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_COMMON -> "COMMON";
            case STT_GNU_IFUNC -> "IFUNC";
            default -> "TYPE(" + type + ")";
        };
    }

    public static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS(" + vis + ")";
        };
    }

    public static String machineName(int machine) {
        return switch (machine) {
            case EM_386 -> "i386";
            case EM_ARM -> "ARM";
            case EM_X86_64 -> "x86-64";
            case EM_AARCH64 -> "AArch64";
            default -> "MACHINE(" + machine + ")";
        };
    }

    public static String typeOfObject(int type) {
        return switch (type) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            default -> "NONE";
        };
    }

    public static boolean isCopyReloc(int machine, int type) {
        return (machine == EM_X86_64 && type == R_X86_64_COPY)
                || (machine == EM_AARCH64 && type == R_AARCH64_COPY);
    }
}
