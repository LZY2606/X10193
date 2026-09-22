package gsb.symboltribunal.elf;

import java.util.List;
import java.util.Map;

/** Parsed view of an ELF relocatable object or shared object. */
public record ElfFile(
        String unitName,
        boolean elf64,
        boolean littleEndian,
        int machine,
        String machineName,
        int type,
        String typeName,
        List<Section> sections,
        List<Sym> staticSymbols,
        List<Sym> dynSymbols,
        List<Reloc> relocations,
        List<Group> groups,
        Map<Integer, VersionNode> versionNodes,
        List<String> needed,
        String soname) {

    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

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
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;

    public static final int SHF_WRITE = 0x1;
    public static final int SHF_ALLOC = 0x2;
    public static final int SHF_EXECINSTR = 0x4;
    public static final int SHF_GROUP = 0x200;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public static final int GRP_COMDAT = 0x1;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VER_NDX_HIDDEN_BIT = 0x8000;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;

    public static final int R_COPY = 5;
    public static final int R_GLOB_DAT = 6;
    public static final int R_JUMP_SLOT = 7;
    public static final int R_RELATIVE = 8;

    public List<Sym> allDefinitions() {
        return dynSymbols.isEmpty() ? staticSymbols : dynSymbols;
    }

    public static String bindName(int bind) {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_GLOBAL -> "GLOBAL";
            case STB_WEAK -> "WEAK";
            default -> "BIND(" + bind + ")";
        };
    }

    public static String typeNameOf(int type) {
        return switch (type) {
            case STT_NOTYPE -> "NOTYPE";
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_COMMON -> "COMMON";
            default -> "TYPE(" + type + ")";
        };
    }

    public static String visibilityName(int vis) {
        return switch (vis) {
            case STV_DEFAULT -> "DEFAULT";
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "VIS(" + vis + ")";
        };
    }

    public static String shndxName(int shndx) {
        return switch (shndx) {
            case SHN_UNDEF -> "UND";
            case SHN_ABS -> "ABS";
            case SHN_COMMON -> "COMMON";
            default -> String.valueOf(shndx);
        };
    }

    public static String typeOfFile(int type) {
        return switch (type) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            default -> "NONE";
        };
    }

    public static String machineName(int machine) {
        return switch (machine) {
            case 3 -> "i386";
            case 62 -> "x86_64";
            case 40 -> "ARM";
            case 183 -> "aarch64";
            case 243 -> "riscv";
            default -> "MACHINE(" + machine + ")";
        };
    }
}
