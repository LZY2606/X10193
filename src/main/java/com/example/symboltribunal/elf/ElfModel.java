package com.example.symboltribunal.elf;

import java.util.List;

public final class ElfModel {
    private ElfModel() {
    }

    public enum ElfClass { ELF_32, ELF_64, UNKNOWN }

    public record ElfSection(int index, int nameIndex, String name, int type, long flags, long address,
                             long offset, long size, int link, int info, long addressAlignment,
                             long entrySize) {
    }

    public record ElfSymbol(int index, int nameIndex, String name, long value, long size, int binding,
                            int type, int other, int visibility, int sectionIndex, String sectionName,
                            String groupSignature, boolean comdatGroup, boolean dynamic) {
        public boolean isLocal() {
            return binding == ElfConstants.STB_LOCAL;
        }

        public boolean isWeak() {
            return binding == ElfConstants.STB_WEAK;
        }

        public boolean isCommon() {
            return sectionIndex == ElfConstants.SHN_COMMON;
        }

        public boolean isUndefined() {
            return sectionIndex == ElfConstants.SHN_UNDEF;
        }

        public boolean isDefined() {
            return sectionIndex != ElfConstants.SHN_UNDEF
                    && sectionIndex != ElfConstants.SHN_COMMON
                    && sectionIndex != ElfConstants.SHN_ABS
                    && sectionIndex >= 0;
        }

        public boolean isHiddenOrInternal() {
            return visibility == ElfConstants.STV_HIDDEN || visibility == ElfConstants.STV_INTERNAL;
        }
    }

    public record ElfRelocation(long offset, long symbolIndex, int type, Long addend, boolean rela,
                                int sectionIndex, String sectionName) {
    }

    public record VersionSlot(long symbolIndex, int versionIndex, boolean hidden) {
    }

    public record VersionDefinition(String name, int index, boolean base, boolean nonDefault,
                                    int hash, List<String> aliases) {
        public boolean matchesAlias(String candidate) {
            return name.equals(candidate) || aliases.contains(candidate);
        }
    }

    public record VersionRequirement(String fileName, String name, int index, int hash) {
    }

    public record NeededEntry(int order, String name) {
    }

    public record ElfObject(String sourceName, String objectName, int inputOrder, int memberOrder,
                            String archiveName, boolean explicit, boolean elf64, boolean littleEndian,
                            int type, int machine, List<ElfSection> sections, List<ElfSymbol> symbols,
                            List<ElfRelocation> relocations, List<VersionSlot> versionSlots,
                            List<VersionDefinition> versionDefinitions,
                            List<VersionRequirement> versionRequirements, List<NeededEntry> needed,
                            String soname, boolean corrupt, String error) {
        public String identity() {
            return sourceName;
        }

        public boolean relocatable() {
            return type == ElfConstants.ET_REL;
        }

        public boolean sharedObject() {
            return type == ElfConstants.ET_DYN;
        }

        public boolean executable() {
            return type == ElfConstants.ET_EXEC;
        }

        public VersionSlot slotFor(long symbolIndex) {
            return versionSlots.stream()
                    .filter(slot -> slot.symbolIndex() == symbolIndex)
                    .findFirst()
                    .orElse(null);
        }
    }

    public record ArchiveMember(String name, int order, ElfObject object, boolean corrupt, String error) {
    }

    public record LoadedInput(String name, int order, Kind kind, ElfObject object,
                              List<ArchiveMember> members, String error) {
        public enum Kind { ELF, ARCHIVE, CORRUPT }

        public static LoadedInput elf(String name, int order, ElfObject object) {
            return new LoadedInput(name, order, Kind.ELF, object, List.of(), null);
        }

        public static LoadedInput archive(String name, int order, List<ArchiveMember> members) {
            return new LoadedInput(name, order, Kind.ARCHIVE, null, List.copyOf(members), null);
        }

        public static LoadedInput corrupt(String name, int order, String error) {
            return new LoadedInput(name, order, Kind.CORRUPT, null, List.of(), error);
        }
    }
}
