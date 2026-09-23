package com.example.symbolcourt.elf;

import java.util.List;

public final class ElfModels {
    private ElfModels() {}

    public enum ObjectKind { REL, EXECUTABLE, SHARED, ARCHIVE, CORRUPT }
    public enum RelocationKind { REL, RELA, COPY }
    public enum VersionOrigin { DEFINITION, REQUIREMENT, IMPLICIT }

    public record ElfIdentity(int elfClass, int dataEncoding, int machine, boolean littleEndian) {}

    public record VersionAlias(String name, boolean nonDefault) {}

    public record VersionDefinition(int index, String name, boolean nonDefault, List<VersionAlias> aliases) {}

    public record VersionRequirement(String fileName, int index, String versionName) {}

    public record ElfSymbol(
            int symbolIndex,
            String name,
            int binding,
            int type,
            int visibility,
            int sectionIndex,
            String sectionName,
            long value,
            long size,
            boolean dynamic,
            Integer versionIndex,
            String versionName,
            boolean nonDefaultVersion,
            String groupSignature
    ) {
        public boolean isLocal() { return binding == 0; }
        public boolean isGlobal() { return binding == 1; }
        public boolean isWeak() { return binding == 2; }
        public boolean isUndefined() { return sectionIndex == 0; }
        public boolean isCommon() { return sectionIndex == 0xfff2; }
        public boolean isDefined() { return !isUndefined() && !isCommon(); }
        public boolean isHiddenOrInternal() { return visibility == 1 || visibility == 2; }
        public boolean isProtected() { return visibility == 3; }
        public String bindingName() { return switch (binding) { case 1 -> "GLOBAL"; case 2 -> "WEAK"; default -> "LOCAL"; }; }
        public String visibilityName() { return switch (visibility) { case 1 -> "INTERNAL"; case 2 -> "HIDDEN"; case 3 -> "PROTECTED"; default -> "DEFAULT"; }; }
    }

    public record ElfRelocation(
            long offset,
            int type,
            int symbolIndex,
            String symbolName,
            String sectionName,
            Long addend,
            boolean copy,
            String symbolVersion,
            boolean dynamicSymbol
    ) {}

    public record ObjectUnit(
            String id,
            String name,
            String path,
            String archiveName,
            ObjectKind kind,
            int explicitOrder,
            String sha256,
            ElfIdentity identity,
            List<String> neededLibraries,
            List<ElfSymbol> symbols,
            List<ElfRelocation> relocations,
            List<VersionDefinition> versionDefinitions,
            List<VersionRequirement> versionRequirements,
            List<String> groupSignatures,
            List<String> parseErrors,
            boolean corrupt,
            boolean archiveMember,
            boolean extracted
    ) {
        public ObjectUnit withExtracted(boolean value) {
            return new ObjectUnit(id, name, path, archiveName, kind, explicitOrder, sha256, identity, neededLibraries,
                    symbols, relocations, versionDefinitions, versionRequirements, groupSignatures, parseErrors,
                    corrupt, archiveMember, value);
        }
    }
}
