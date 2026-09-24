package court.elf;

/** ELF constants used by parser and engine. */
public final class ElfConst {
    private ElfConst() {}

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

    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

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

    public static final short SHN_UNDEF = 0;
    public static final short SHN_ABS = (short) 0xfff1;
    public static final short SHN_COMMON = (short) 0xfff2;
    public static final short SHN_XINDEX = (short) 0xffff;

    public static final int GRP_COMDAT = 0x1;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_VERSYM = 0x6fffffff;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;

    public static String machineName(int machine) {
        return switch (machine) {
            case 3 -> "x86";
            case 8 -> "MIPS";
            case 20 -> "PowerPC";
            case 40 -> "ARM";
            case 62 -> "x86-64";
            case 183 -> "AArch64";
            case 243 -> "RISC-V";
            default -> "EM_" + machine;
        };
    }

    public static String typeName(int type) {
        return switch (type) {
            case ET_NONE -> "NONE";
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            case ET_CORE -> "CORE";
            default -> "ET_" + type;
        };
    }
}
