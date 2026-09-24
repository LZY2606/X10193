package com.symtribunal.elf;

/** ELF constants used by the parser and resolver. */
public final class ElfConstants {
    private ElfConstants() {
    }

    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;

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

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    public static final int GRP_COMDAT = 0x1;

    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_VERDEFNUM = 0x6ffffdfd;
    public static final int DT_VERDEF = 0x6ffffffc;
    public static final int DT_VERNEEDNUM = 0x6ffffeff;
    public static final int DT_VERNEED = 0x6ffffffe;
    public static final int DT_VERSYM = 0x6ffffff0;

    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_386_COPY = 5;
    public static final int R_386_GLOB_DAT = 6;
    public static final int R_386_JMP_SLOT = 7;
}
