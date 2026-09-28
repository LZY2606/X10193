package org.symcourt.elf;

/**
 * ELF constants used by the symbol arbitration engine.
 * Values follow the generic ABI and the x86-64 / i386 processor supplements.
 */
public final class Elf {
    private Elf() {}

    public static final byte[] MAG = {0x7f, 'E', 'L', 'F'};

    // e_ident offsets
    public static final int EI_CLASS = 4;
    public static final int EI_DATA = 5;
    public static final int EI_VERSION = 6;
    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;

    // e_type
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

    // e_machine
    public static final int EM_386 = 3;
    public static final int EM_X86_64 = 62;
    public static final int EM_AARCH64 = 183;
    public static final int EM_RISCV = 243;

    // section types
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_REL = 9;
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;
    public static final int SHT_GNU_versym = 0x6fffffff;

    // section flags
    public static final long SHF_WRITE = 0x1L;
    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_EXECINSTR = 0x4L;
    public static final long SHF_GROUP = 0x200L;

    // special section indices
    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    // symbol bindings / types / visibility
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 0x3;
    public static final int STT_FILE = 0x4;
    public static final int STT_COMMON = 0xb;
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    // dynamic tags
    public static final long DT_NULL = 0;
    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;
    public static final long DT_SYMBOLIC = 16;
    public static final long DT_FLAGS = 30;
    public static final long DF_SYMBOLIC = 0x2;
    public static final long DT_FLAGS_1 = 0x6ffffffb;
    public static final long DF_1_NOW = 0x1;
    public static final long DF_1_NODELETE = 0x8;
    public static final long DT_VERNEEDNUM = 0x6fffffff;
    public static final long DT_VERDEFNUM = 0x6ffffffc;

    // group flags
    public static final int GRP_COMDAT = 0x1;

    // version flags (verdef)
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;

    // version symbol special indices
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;

    // relocation types (x86-64)
    public static final int R_X86_64_NONE = 0;
    public static final int R_X86_64_64 = 1;
    public static final int R_X86_64_PC32 = 2;
    public static final int R_X86_64_PLT32 = 4;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_32 = 10;

    // relocation types (i386)
    public static final int R_386_32 = 1;
    public static final int R_386_PC32 = 2;
    public static final int R_386_GLOB_DAT = 6;
    public static final int R_386_JMP_SLOT = 7;
    public static final int R_386_RELATIVE = 8;
    public static final int R_386_COPY = 5;

    public static String machineName(int machine) {
        return switch (machine) {
            case EM_386 -> "i386";
            case EM_X86_64 -> "x86-64";
            case EM_AARCH64 -> "AArch64";
            case EM_RISCV -> "RISC-V";
            default -> "EM_" + machine;
        };
    }

    public static String typeName(int type) {
        return switch (type) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            case ET_CORE -> "CORE";
            default -> "ET_" + type;
        };
    }

    public static String bindingName(int binding) {
        return switch (binding) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "STB_" + binding;
        };
    }

    public static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "STV_" + vis;
        };
    }

    public static String symbolTypeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_SECTION -> "SECTION";
            case STT_FILE -> "FILE";
            case STT_COMMON -> "COMMON";
            default -> "STT_" + type;
        };
    }
}
