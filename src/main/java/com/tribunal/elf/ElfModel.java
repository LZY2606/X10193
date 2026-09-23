package com.tribunal.elf;

import java.util.ArrayList;
import java.util.List;

public final class ElfModel {

    public static final int ET_REL = 1, ET_EXEC = 2, ET_DYN = 3;
    public static final int SHT_SYMTAB = 2, SHT_STRTAB = 3, SHT_RELA = 4, SHT_DYNAMIC = 6,
            SHT_NOBITS = 8, SHT_REL = 9, SHT_DYNSYM = 11, SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd, SHT_GNU_VERNEED = 0x6ffffffe, SHT_GNU_VERSYM = 0x6fffffff;
    public static final int STB_LOCAL = 0, STB_GLOBAL = 1, STB_WEAK = 2;
    public static final int STT_NOTYPE = 0, STT_OBJECT = 1, STT_FUNC = 2, STT_SECTION = 3, STT_COMMON = 5;
    public static final int STV_DEFAULT = 0, STV_INTERNAL = 1, STV_HIDDEN = 2, STV_PROTECTED = 3;
    public static final int SHN_UNDEF = 0, SHN_ABS = 0xfff1, SHN_COMMON = 0xfff2;
    public static final int GRP_COMDAT = 1;
    public static final long DT_NULL = 0, DT_NEEDED = 1, DT_SONAME = 14;
    public static final int VER_FLG_BASE = 1;

    public record Section(String name, int nameOff, int type, long flags, long addr, long offset,
                          long size, int link, int info, long addralign, long entsize, int index) {
        public boolean nobits() { return type == SHT_NOBITS; }
    }

    public record Symbol(String name, int bind, int type, int visibility, int shndx,
                         long value, long size, String table, int tableIndex,
                         Integer versionIndex, String versionName, boolean versionHidden) {
        public boolean isUndef() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON; }
        public boolean isAbs() { return shndx == SHN_ABS; }
        public boolean isDefinition() { return !isUndef(); }
        public String bindName() {
            return switch (bind) { case STB_LOCAL -> "LOCAL"; case STB_GLOBAL -> "GLOBAL";
                case STB_WEAK -> "WEAK"; default -> "BIND" + bind; };
        }
        public String visibilityName() {
            return switch (visibility) { case STV_DEFAULT -> "DEFAULT"; case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN"; case STV_PROTECTED -> "PROTECTED"; default -> "VIS" + visibility; };
        }
        public String typeName() {
            return switch (type) { case STT_NOTYPE -> "NOTYPE"; case STT_OBJECT -> "OBJECT";
                case STT_FUNC -> "FUNC"; case STT_SECTION -> "SECTION"; case STT_COMMON -> "COMMON";
                default -> "TYPE" + type; };
        }
    }

    public record Reloc(String section, long offset, long type, int symIndex, String symbolName,
                        long addend, boolean rela) {}

    public record Group(String signature, boolean comdat, List<Integer> memberSections) {}

    public record VerDef(int index, String name, int flags) {
        public boolean isBase() { return (flags & VER_FLG_BASE) != 0; }
    }

    public record VerNeed(String file, int index, String name) {}

    public static final class ParsedObject {
        public String name;
        public int elfClass;      // 1=32, 2=64
        public int elfType;       // ET_*
        public int machine;
        public boolean littleEndian;
        public final List<Section> sections = new ArrayList<>();
        public final List<Symbol> symbols = new ArrayList<>();          // 合并 symtab + dynsym
        public final List<Reloc> relocations = new ArrayList<>();
        public final List<Group> groups = new ArrayList<>();
        public final List<VerDef> verDefs = new ArrayList<>();
        public final List<VerNeed> verNeeds = new ArrayList<>();
        public final List<String> neededLibraries = new ArrayList<>();  // DT_NEEDED
        public String soname;

        public String kindName() {
            return switch (elfType) { case ET_REL -> "REL"; case ET_EXEC -> "EXEC";
                case ET_DYN -> "DYN"; default -> "TYPE" + elfType; };
        }
        public boolean isShared() { return elfType == ET_DYN; }
        public boolean hasVerDefs() { return !verDefs.isEmpty(); }
    }

    public static final class ArchiveMember {
        public String name;
        public byte[] data;
        public ParsedObject object;   // null 表示非 ELF 或损坏
        public String parseError;
    }

    public static final class ParsedArchive {
        public String name;
        public final List<ArchiveMember> members = new ArrayList<>();
    }

    private ElfModel() {}
}
