package org.symbolcourt.elf;

public final class ElfConstants {
    private ElfConstants() {}

    public static final int EI_CLASS = 4;
    public static final int EI_DATA = 5;
    public static final int EI_NIDENT = 16;
    public static final byte ELFCLASS32 = 1;
    public static final byte ELFCLASS64 = 2;
    public static final byte ELFDATA2LSB = 1;
    public static final byte ELFDATA2MSB = 2;

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_DYNAMIC = 6;

    public static final long SHF_GROUP = 0x200L;

    public static final int GRP_COMDAT = 0x1;

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

    public static final short SHN_UNDEF = 0;
    public static final short SHN_ABS = (short) 0xfff1;
    public static final short SHN_COMMON = (short) 0xfff2;

    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_NULL = 0;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_386_COPY = 6;
    public static final int R_386_GLOB_DAT = 7;
    public static final int R_386_JMP_SLOT = 8;
    public static final int R_ARM_COPY = 20;
    public static final int R_ARM_GLOB_DAT = 21;
    public static final int R_ARM_JUMP_SLOT = 22;
    public static final int R_AARCH64_COPY = 1024;
    public static final int R_AARCH64_GLOB_DAT = 1025;
    public static final int R_AARCH64_JUMP_SLOT = 1026;
}
