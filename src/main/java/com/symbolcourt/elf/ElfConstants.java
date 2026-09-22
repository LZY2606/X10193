package com.symbolcourt.elf;

import java.util.Map;

/** ELF constants used by the parser/resolver. */
public final class ElfConstants {

    private ElfConstants() {
    }

    public static final byte[] ELF_MAGIC = {0x7f, 'E', 'L', 'F'};
    public static final byte[] AR_MAGIC = "!<arch>\n".getBytes();

    public static final int EI_CLASS = 4;
    public static final int EI_DATA = 5;
    public static final byte ELFCLASS32 = 1;
    public static final byte ELFCLASS64 = 2;
    public static final byte ELFDATA2LSB = 1;
    public static final byte ELFDATA2MSB = 2;

    // e_type
    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

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
    public static final int SHT_SHLIB = 10;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    // special section indices
    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    // sh_flags
    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_WRITE = 0x1L;

    // st_info
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

    // st_other
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    // versym special bits
    public static final int VERSYM_VERSION = 0x7fff;
    public static final int VERSYM_HIDDEN = 0x8000;

    // verdef/verneed flags
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    // group flags
    public static final int GRP_COMDAT = 0x1;

    // dynamic tags
    public static final long DT_NULL = 0;
    public static final long DT_NEEDED = 1;
    public static final long DT_SONAME = 14;
    public static final long DT_STRTAB = 5;
    public static final long DT_STRSZ = 10;
    public static final long DT_VERDEF = 0x6ffffffcL;
    public static final long DT_VERDEFNUM = 0x6ffffffdL;
    public static final long DT_VERNEED = 0x6ffffffeL;
    public static final long DT_VERNEEDNUM = 0x6fffffffL;

    // COPY relocation types per machine (EM_* -> relocation type id).
    public static final int EM_386 = 3;
    public static final int EM_X86_64 = 62;
    public static final int EM_ARM = 40;
    public static final int EM_AARCH64 = 183;
    public static final int EM_RISCV = 243;
    public static final int EM_PPC = 20;
    public static final int EM_PPC64 = 21;

    public static final Map<Integer, Integer> COPY_RELOC_TYPES = Map.of(
            EM_386, 5,        // R_386_COPY
            EM_X86_64, 5,     // R_X86_64_COPY
            EM_ARM, 20,       // R_ARM_COPY
            EM_AARCH64, 1024, // R_AARCH64_COPY
            EM_RISCV, 4,      // R_RISCV_COPY
            EM_PPC, 20,       // R_PPC_COPY
            EM_PPC64, 22      // R_PPC64_COPY
    );

    public static int bind(int stInfo) {
        return stInfo >>> 4;
    }

    public static int type(int stInfo) {
        return stInfo & 0xf;
    }

    public static int visibility(int stOther) {
        return stOther & 0x3;
    }

    public static String bindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "UNKNOWN(" + bind + ")";
        };
    }

    public static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "UNKNOWN(" + vis + ")";
        };
    }

    public static String typeName(int type) {
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
}
