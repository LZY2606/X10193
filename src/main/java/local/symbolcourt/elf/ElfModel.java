package local.symbolcourt.elf;

import java.util.List;
import java.util.Set;

public final class ElfModel {
    private ElfModel() {
    }

    public enum SymbolTableKind {
        STATIC,
        DYNAMIC
    }

    public enum RelocationKind {
        REL,
        RELA
    }

    public record ElfFile(
            String fileName,
            int elfClass,
            int objectType,
            int machine,
            List<ElfSection> sections,
            List<ElfSymbol> symbols,
            List<ElfRelocation> relocations,
            List<VersionDefinition> versionDefinitions,
            List<VersionNeed> versionNeeds,
            List<SectionGroup> groups,
            List<String> neededLibraries,
            String soname) {
        public boolean isSharedObject() {
            return objectType == 3;
        }

        public boolean isRelocatable() {
            return objectType == 1;
        }
    }

    public record ElfSection(
            int index,
            String name,
            long type,
            long flags,
            long address,
            long offset,
            long size,
            int link,
            int info,
            long alignment,
            long entrySize) {
    }

    public record ElfSymbol(
            SymbolTableKind tableKind,
            int tableSectionIndex,
            int index,
            String name,
            long value,
            long size,
            int bind,
            int type,
            int visibility,
            int sectionIndex,
            Integer versionIndex,
            String versionName,
            boolean versionHidden,
            Set<String> versionAliases) {
        public static final int BIND_LOCAL = 0;
        public static final int BIND_GLOBAL = 1;
        public static final int BIND_WEAK = 2;
        public static final int VIS_DEFAULT = 0;
        public static final int VIS_INTERNAL = 1;
        public static final int VIS_HIDDEN = 2;
        public static final int VIS_PROTECTED = 3;
        public static final int SHN_UNDEF = 0;
        public static final int SHN_COMMON = 0xfff2;
        public static final int SHN_ABS = 0xfff1;

        public boolean defined() {
            return sectionIndex != SHN_UNDEF;
        }

        public boolean common() {
            return sectionIndex == SHN_COMMON;
        }

        public boolean local() {
            return bind == BIND_LOCAL;
        }

        public boolean hidden() {
            return visibility == VIS_HIDDEN || visibility == VIS_INTERNAL;
        }

        public String displayName() {
            if (versionName == null || versionName.isBlank()) {
                return name;
            }
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public record ElfRelocation(
            int sectionIndex,
            String sectionName,
            long offset,
            long type,
            int symbolIndex,
            Long addend,
            RelocationKind kind,
            int symbolTableSectionIndex,
            String symbolName,
            String symbolVersion) {
    }

    public record VersionDefinition(
            int index,
            String name,
            List<String> aliases,
            int flags) {
        public List<String> allNames() {
            if (aliases == null || aliases.isEmpty()) {
                return List.of(name);
            }
            return aliases;
        }
    }

    public record VersionNeed(
            String file,
            List<VersionNeedEntry> entries) {
    }

    public record VersionNeedEntry(
            int index,
            String name,
            boolean hidden) {
    }

    public record SectionGroup(
            int sectionIndex,
            String name,
            long flags,
            String signature,
            List<Integer> memberSectionIndexes,
            List<String> memberSectionNames) {
        public boolean comdat() {
            return (flags & 0x1L) != 0;
        }
    }
}
