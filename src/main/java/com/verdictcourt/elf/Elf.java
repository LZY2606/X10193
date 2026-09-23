package com.verdictcourt.elf;

import java.util.List;

/** ELF 数据模型与常量。 */
public final class Elf {
    private Elf() {}

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;

    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public static final int GRP_COMDAT = 1;
    public static final int VER_FLG_BASE = 1;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VERSYM_IDX = 0x7fff;

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static String bindName(int b) {
        return switch (b) {
            case 0 -> "LOCAL"; case 1 -> "GLOBAL"; case 2 -> "WEAK"; case 10 -> "GNU_UNIQUE";
            default -> "BIND#" + b;
        };
    }

    public static String typeName(int t) {
        return switch (t) {
            case 0 -> "NOTYPE"; case 1 -> "OBJECT"; case 2 -> "FUNC"; case 3 -> "SECTION";
            case 4 -> "FILE"; case 5 -> "COMMON"; case 6 -> "TLS"; case 10 -> "GNU_IFUNC";
            default -> "TYPE#" + t;
        };
    }

    public static String visName(int v) {
        return switch (v) {
            case 0 -> "DEFAULT"; case 1 -> "INTERNAL"; case 2 -> "HIDDEN"; case 3 -> "PROTECTED";
            default -> "VIS#" + v;
        };
    }

    public record Section(String name, int type, long flags, long addr, long offset, long size,
                          int link, int info, long addralign, long entsize) {}

    public record Symbol(int index, String table, String name, int bind, int type, int visibility,
                         int shndx, long value, long size, String sectionName,
                         Integer versionIndex, String versionName, boolean versionHidden) {
        public boolean isUndef() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON; }
        public boolean isLocal() { return bind == 0; }
    }

    public record Reloc(String section, long offset, long info, int symIndex, long type,
                        Long addend, boolean rela) {}

    public record Verdef(int index, String name, boolean base) {}
    public record Verneed(String file, int index, String name) {}
    public record Group(String section, String signature, boolean comdat, List<String> members) {}

    public record ElfFile(String name, boolean is64, int elfType, int machine,
                          List<Section> sections, List<Symbol> symtab, List<Symbol> dynsym,
                          List<Reloc> relocations, List<Verdef> verdefs, List<Verneed> verneeds,
                          List<Group> groups, List<String> neededLibs) {
        public String typeName() {
            return switch (elfType) {
                case ET_REL -> "REL"; case ET_EXEC -> "EXEC"; case ET_DYN -> "DYN";
                default -> "ET#" + elfType;
            };
        }
    }
}
