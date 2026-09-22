package com.symbolcourt.elf;

public final class Constants {
    private Constants() {}

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

    public static final int SHT_NULL = 0;
    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;

    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_GROUP = 0x200L;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 10;
    public static final int STT_FILE = 4;
    public static final int STT_COMMON = 5;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = (1 << 16) - 1;
    public static final int SHN_COMMON = (1 << 16) - 2;
    public static final int SHN_XINDEX = (1 << 16) - 1;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;

    public static final int GRP_COMDAT = 0x1;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VER_NDX_HIDDEN_FLAG = 0x8000;
}
