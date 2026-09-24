package court.elf;

/** ELF constants needed by the parser, resolver and the test fixture builder. */
public final class ElfConstants {
    private ElfConstants() {
    }

    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;
    public static final int ET_CORE = 4;

    public static final int EM_386 = 3;
    public static final int EM_ARM = 40;
    public static final int EM_X86_64 = 62;
    public static final int EM_AARCH64 = 183;
    public static final int EM_RISCV = 243;

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
    public static final int SHT_GNU_verdef = 0x6ffffffd;
    public static final int SHT_GNU_verneed = 0x6ffffffe;
    public static final int SHT_GNU_versym = 0x6fffffff;

    public static final long SHF_ALLOC = 0x2L;
    public static final long SHF_GROUP = 0x200L;

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
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VER_NDX_GLOBAL_64 = 1;
    public static final int VER_NDX_HIDDEN = 0x8000;
    public static final int VER_NDX_HIDDEN_64 = 0x8000;

    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_NEED_WEAK = 0x2;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_STRTAB = 5;
    public static final int DT_SONAME = 14;
    public static final int DT_VERNEEDNUM = 0x6fffffff;

    public static final int GRP_COMDAT = 0x1;

    public static String stBindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND_" + bind;
        };
    }

    public static String stTypeName(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_SECTION -> "SECTION";
            case STT_FILE -> "FILE";
            case STT_COMMON -> "COMMON";
            case STT_TLS -> "TLS";
            default -> "TYPE_" + type;
        };
    }

    public static String visibilityName(int visibility) {
        return switch (visibility) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS_" + visibility;
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

    private static final int[] X86_64_RELOC = {0, 2, 4, 6, 8, 9, 10};
    private static final String[] X86_64_NAMES = {"R_X86_64_NONE", "R_X86_64_PC32", "R_X86_64_PLT32",
            "R_X86_64_GLOB_DAT", "R_X86_64_JUMP_SLOT", "R_X86_64_RELATIVE", "R_X86_64_COPY"};

    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_X86_64_COPY = 5;

    public static final int R_386_COPY = 5;
    public static final int R_386_GLOB_DAT = 6;
    public static final int R_386_JMP_SLOT = 7;
    public static final int R_386_RELATIVE = 8;

    public static final int R_AARCH64_COPY = 1024;
    public static final int R_AARCH64_GLOB_DAT = 1025;
    public static final int R_AARCH64_JUMP_SLOT = 1026;
    public static final int R_AARCH64_RELATIVE = 1027;

    public static String relocTypeName(int machine, int type) {
        switch (machine) {
            case EM_X86_64 -> {
                return switch (type) {
                    case 0 -> "R_X86_64_NONE";
                    case 2 -> "R_X86_64_PC32";
                    case 4 -> "R_X86_64_PLT32";
                    case 5 -> "R_X86_64_COPY";
                    case 6 -> "R_X86_64_GLOB_DAT";
                    case 7 -> "R_X86_64_JUMP_SLOT";
                    case 8 -> "R_X86_64_RELATIVE";
                    case 9 -> "R_X86_64_RELATIVE64";
                    case 37 -> "R_X86_64_IRELATIVE";
                    default -> "R_X86_64_" + type;
                };
            }
            case EM_386 -> {
                return switch (type) {
                    case 5 -> "R_386_COPY";
                    case 6 -> "R_386_GLOB_DAT";
                    case 7 -> "R_386_JMP_SLOT";
                    case 8 -> "R_386_RELATIVE";
                    default -> "R_386_" + type;
                };
            }
            case EM_AARCH64 -> {
                return switch (type) {
                    case 1024 -> "R_AARCH64_COPY";
                    case 1025 -> "R_AARCH64_GLOB_DAT";
                    case 1026 -> "R_AARCH64_JUMP_SLOT";
                    case 1027 -> "R_AARCH64_RELATIVE";
                    default -> "R_AARCH64_" + type;
                };
            }
            default -> {
                return "R_" + type;
            }
        }
    }

    public static boolean isCopyReloc(int machine, int type) {
        return (machine == EM_X86_64 && type == R_X86_64_COPY)
                || (machine == EM_386 && type == R_386_COPY)
                || (machine == EM_AARCH64 && type == R_AARCH64_COPY);
    }

    public static boolean isDynamicSlotReloc(int machine, int type) {
        if (machine == EM_X86_64) {
            return type == R_X86_64_GLOB_DAT || type == R_X86_64_JUMP_SLOT;
        }
        if (machine == EM_386) {
            return type == R_386_GLOB_DAT || type == R_386_JMP_SLOT;
        }
        if (machine == EM_AARCH64) {
            return type == R_AARCH64_GLOB_DAT || type == R_AARCH64_JUMP_SLOT;
        }
        return false;
    }

    static {
        assert X86_64_RELOC.length == X86_64_NAMES.length;
    }
}
