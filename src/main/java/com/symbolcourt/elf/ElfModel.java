package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** In-memory model of a parsed ELF file. Only what the symbol court needs. */
public final class ElfModel {

    public static final int SHT_SYMTAB = 2;
    public static final int SHT_RELA = 4;
    public static final int SHT_VERDEF = 0x6ffffffd;
    public static final int SHT_VERNEED = 0x6ffffffe;
    public static final int SHT_VERSYM = 0x6fffffff;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_COMMON = 5;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_LORESERVE = 0xff00;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public static final int GRP_COMDAT = 1;

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public record Section(String name, int type, long flags, long offset, long size,
                          int link, int info, long entsize) {}

    /**
     * @param version       resolved version name (from verdef/verneed), or null when unversioned
     * @param versionHidden true when the versym hidden bit was set (non-default "sym@VER" definition)
     */
    public record Symbol(String name, int bind, int type, int visibility, int shndx,
                         long value, long size, boolean dynamic,
                         String version, boolean versionHidden, int index) {
        public boolean isUndefined() {
            return shndx == SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == SHN_COMMON;
        }

        public boolean isLocal() {
            return bind == STB_LOCAL;
        }

        public boolean isWeak() {
            return bind == STB_WEAK;
        }

        public String bindName() {
            return switch (bind) {
                case STB_LOCAL -> "LOCAL";
                case STB_GLOBAL -> "GLOBAL";
                case STB_WEAK -> "WEAK";
                default -> "BIND#" + bind;
            };
        }

        public String visibilityName() {
            return switch (visibility) {
                case STV_DEFAULT -> "DEFAULT";
                case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN";
                case STV_PROTECTED -> "PROTECTED";
                default -> "VIS#" + visibility;
            };
        }
    }

    public record Reloc(String section, long offset, int symIndex, int type, Long addend) {}

    public record VerDef(int index, String name, boolean hidden) {}

    public record VerNeed(String file, List<Entry> entries) {
        public record Entry(int other, String name, boolean hidden) {}
    }

    /** A section group; signature is the symbol name identifying the group (COMDAT key). */
    public record Group(String signature, boolean comdat, List<Integer> memberSections) {}

    public int elfClass; // 1 = 32-bit, 2 = 64-bit
    public int machine;
    public int type;   // ET_*
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();      // .symtab
    public final List<Symbol> dynSymbols = new ArrayList<>();   // .dynsym (versions attached)
    public final List<Reloc> relocations = new ArrayList<>();
    public final List<VerDef> verDefs = new ArrayList<>();
    public final List<VerNeed> verNeeds = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();

    public String typeName() {
        return switch (type) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            default -> "TYPE#" + type;
        };
    }
}
