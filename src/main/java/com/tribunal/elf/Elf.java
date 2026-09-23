package com.tribunal.elf;

/** ELF ABI constants. Only the subset needed by the parser and the resolver is listed. */
public final class Elf {
    private Elf() {
    }

    public static final int ELFMAG0 = 0x7f;
    public static final byte ELFMAG1 = 'E';
    public static final byte ELFMAG2 = 'L';
    public static final byte ELFMAG3 = 'F';

    public static final int EI_NIDENT = 16;
    public static final int EI_CLASS = 4;
    public static final int EI_DATA = 5;
    public static final int EI_VERSION = 6;
    public static final int EI_OSABI = 7;

    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;

    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;

    // e_type
    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

    // e_machine
    public static final int EM_NONE = 0;
    public static final int EM_386 = 3;
    public static final int EM_ARM = 40;
    public static final int EM_X86_64 = 62;
    public static final int EM_AARCH64 = 183;
    public static final int EM_RISCV = 243;

    // special section indices
    public static final int SHN_UNDEF = 0;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_XINDEX = 0xffff;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_ABS = 0xfff1;

    // section types
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
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;
    public static final int SHT_GNU_versym = 0x6fffffff;

    // section flags
    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_GROUP = 0x200L;

    // group flags
    public static final int GRP_COMDAT = 0x1;

    // symbol bindings
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_GNU_UNIQUE = 10;

    // symbol types
    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_COMMON = 5;
    public static final int STT_TLS = 6;

    // visibility
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    // d_tag
    public static final long DT_NULL = 0;
    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;
    public static final long DT_VERNEEDNUM = 0x6fffffff - 5; // 0x6fffffa
    public static final long DT_VERDEFNUM = 0x6ffffffc;

    // verdef flags
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_FLG_INFO = 0x4;

    // versym special values
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VER_NDX_HIDDEN = 0x8000;
    public static final int VERSYM_VERSION_MASK = 0x7fff;

    public static boolean isReservedShndx(int shndx) {
        return shndx >= SHN_LORESERVE;
    }

    public static String bindingName(int binding) {
        return switch (binding) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            case STB_GNU_UNIQUE -> "GNU_UNIQUE";
            default -> "UNKNOWN(" + binding + ")";
        };
    }

    public static String typeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_SECTION -> "SECTION";
            case STT_COMMON -> "COMMON";
            case STT_TLS -> "TLS";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    public static String visibilityName(int visibility) {
        return switch (visibility) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "UNKNOWN(" + visibility + ")";
        };
    }

    public static String typeName(short eType) {
        return switch (eType) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            case ET_CORE -> "CORE";
            default -> "NONE";
        };
    }

    public static String machineName(short machine) {
        return switch (machine) {
            case EM_386 -> "i386";
            case EM_ARM -> "ARM";
            case EM_X86_64 -> "x86-64";
            case EM_AARCH64 -> "AArch64";
            case EM_RISCV -> "RISC-V";
            case EM_NONE -> "NONE";
            default -> "EM(" + machine + ")";
        };
    }
}
