package com.example.tribunal.elf;

import java.util.List;
import java.util.Map;

public final class Models {
    public static final int SHT_NULL = 0;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;
    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_COMMON = 5;
    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    private Models() {
    }

    public record RawObject(String id, String name, byte[] data) {
    }

    public record ElfSection(int index, int nameIndex, String name, int type, long flags, long address,
                             long offset, long size, int link, int info, long alignment, long entrySize) {
        public boolean containsFileOffset(long position) {
            return position >= offset && position < offset + size;
        }
    }

    public record VersionName(int index, String name, boolean defaultVersion, String parentName) {
    }

    public record VersionNeed(int versionIndex, String file, String version) {
    }

    public record GroupEntry(String signature, List<Integer> members, boolean retained) {
    }

    public record ElfSymbol(int index, String name, long value, long size, int binding, int type,
                            int visibility, int sectionIndex, int versionIndex, boolean dynamic,
                            String sectionName) {
        public boolean defined() {
            return sectionIndex != SHN_UNDEF;
        }
        public boolean common() {
            return type == STT_COMMON || sectionIndex == SHN_COMMON;
        }
        public String bindingName() {
            return switch (binding) {
                case STB_LOCAL -> "LOCAL";
                case STB_GLOBAL -> "GLOBAL";
                case STB_WEAK -> "WEAK";
                default -> "OTHER(" + binding + ")";
            };
        }
        public String visibilityName() {
            return switch (visibility) {
                case STV_DEFAULT -> "DEFAULT";
                case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN";
                case STV_PROTECTED -> "PROTECTED";
                default -> "OTHER(" + visibility + ")";
            };
        }
    }

    public record ElfRelocation(int index, String symbolName, int symbolIndex, int type, long offset,
                                long addend, boolean addendPresent, String sectionName, boolean copyRelocation) {
    }

    public record ParsedElf(String id, String name, boolean elf64, boolean littleEndian, int type,
                            int machine, int flags, List<ElfSection> sections, List<ElfSymbol> symbols,
                            List<ElfRelocation> relocations, Map<Integer, VersionName> versions,
                            List<VersionNeed> versionNeeds, List<GroupEntry> groups,
                            List<String> dependencies, String error) {
        public boolean usable() {
            return error == null;
        }
    }

    public record ArchiveMember(String archiveId, String archiveName, String memberName,
                                byte[] data, int memberIndex) {
        public String id() {
            return archiveId + "!member" + memberIndex;
        }
    }

    public record ParseFailure(String id, String name, String error) {
    }

    public record LoadedBinary(ParsedElf elf, List<ArchiveMember> unparsedMembers, List<ParseFailure> failures) {
        public boolean archive() {
            return !unparsedMembers.isEmpty();
        }
    }
}
