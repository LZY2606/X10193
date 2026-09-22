package com.symbolcourt.elf;

/** ELF format constants (subset sufficient for the symbol tribunal). */
public final class ElfConstants {
    private ElfConstants() {
    }

    public static final byte ELFMAG0 = 0x7f;
    public static final byte ELFMAG1 = 'E';
    public static final byte ELFMAG2 = 'L';
    public static final byte ELFMAG3 = 'F';

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
    public static final int SHT_GROUP = 17;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;
    public static final int SHT_GNU_versym = 0x6fffffff;

    public static final int SHF_ALLOC = 0x2;
    public static final int SHF_EXECINSTR = 0x4;
    public static final int SHF_WRITE = 0x1;
    public static final int SHF_GROUP = 0x200;

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

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = (0xffff0000 | 0xfff1);
    public static final int SHN_COMMON = (0xffff0000 | 0xfff2);

    public static final int GRP_COMDAT = 0x1;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;

    public static final int R_X86_64_NONE = 0;
    public static final int R_X86_64_64 = 1;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_X86_64_32 = 10;
    public static final int R_X86_64_PC32 = 2;
    public static final int R_X86_64_PLT32 = 4;

    public static final int R_386_32 = 1;
    public static final int R_386_PC32 = 2;
    public static final int R_386_GLOB_DAT = 6;
    public static final int R_386_JMP_SLOT = 7;
    public static final int R_386_RELATIVE = 8;
    public static final int R_386_COPY = 5;

    public static final int EM_386 = 3;
    public static final int EM_X86_64 = 62;
    public static final int EM_AARCH64 = 183;
    public static final int EM_RISCV = 243;

    public static String stbName(int binding) {
        return switch (binding) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "UNKNOWN(" + binding + ")";
        };
    }

    public static String sttName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_SECTION -> "SECTION";
            case STT_FILE -> "FILE";
            case STT_COMMON -> "COMMON";
            case STT_TLS -> "TLS";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    public static String stvName(int visibility) {
        return switch (visibility) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "UNKNOWN(" + visibility + ")";
        };
    }
}
