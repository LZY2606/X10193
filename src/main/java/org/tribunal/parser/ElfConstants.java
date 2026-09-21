package org.tribunal.parser;

final class ElfConstants {
    static final int SHT_NULL = 0;
    static final int SHT_PROGBITS = 1;
    static final int SHT_SYMTAB = 2;
    static final int SHT_STRTAB = 3;
    static final int SHT_RELA = 4;
    static final int SHT_HASH = 5;
    static final int SHT_DYNAMIC = 6;
    static final int SHT_NOTE = 7;
    static final int SHT_NOBITS = 8;
    static final int SHT_REL = 9;
    static final int SHT_DYNSYM = 11;
    static final int SHT_GROUP = 17;
    static final int SHT_SYMTAB_SHNDX = 18;

    static final int SHF_ALLOC = 0x2;
    static final int SHF_WRITE = 0x1;
    static final int SHF_GROUP = 0x200;

    static final int SHN_UNDEF = 0;
    static final int SHN_ABS = 0xfff1;
    static final int SHN_COMMON = 0xfff2;
    static final int SHN_XINDEX = 0xffff;

    static final int STB_LOCAL = 0;
    static final int STB_GLOBAL = 1;
    static final int STB_WEAK = 2;

    static final int STT_NOTYPE = 0;
    static final int STT_OBJECT = 1;
    static final int STT_FUNC = 2;
    static final int STT_SECTION = 3;
    static final int STT_COMMON = 5;
    static final int STT_GNU_IFUNC = 10;

    static final int STV_DEFAULT = 0;
    static final int STV_INTERNAL = 1;
    static final int STV_HIDDEN = 2;
    static final int STV_PROTECTED = 3;

    static final int ET_REL = 1;
    static final int ET_EXEC = 2;
    static final int ET_DYN = 3;

    static final int DT_NEEDED = 1;
    static final int DT_STRTAB = 5;
    static final int DT_SONAME = 14;
    static final int DT_VERNEED = 0x6ffffffe;
    static final int DT_VERNEEDNUM = 0x6fffffff;
    static final int DT_VERDEF = 0x6ffffffc;
    static final int DT_VERDEFNUM = 0x6ffffffd;
    static final int DT_VERSYM = 0x6ffffff0;

    static final int VER_NDX_LOCAL = 0;
    static final int VER_NDX_GLOBAL = 1;
    static final int VER_NDX_GNU_ELIMINATED = 2;
    static final int VERSYM_HIDDEN = 0x8000;
    static final int VER_FLG_BASE = 0x1;
    static final int VER_FLG_WEAK = 0x2;

    static final int EM_386 = 3;
    static final int EM_ARM = 40;
    static final int EM_X86_64 = 62;
    static final int EM_AARCH64 = 183;
    static final int EM_RISCV = 243;

    private ElfConstants() {
    }
}
