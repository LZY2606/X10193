package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

public final class ElfModels {
    private ElfModels() {}

    public record VersionNode(
            String name,
            String parent,
            boolean definition,
            boolean baseNode,
            int localIndex,
            boolean weakRef,
            List<String> aliases,
            boolean defaultForName,
            String defaultFor) {
    }

    public record SectionInfo(
            int index,
            String name,
            int type,
            long flags,
            long address,
            long size,
            int link,
            int info,
            long alignment) {
    }

    public record ElfSymbol(
            int index,
            String name,
            Enums.SymbolBinding binding,
            Enums.SymbolType type,
            Enums.SymbolVisibility visibility,
            Enums.SymbolShndx shndxKind,
            int shndx,
            long value,
            long size,
            String sectionName,
            Integer versionLocalIndex,
            String versionName,
            boolean versionHidden,
            boolean versionDefault,
            String comdatSignature,
            boolean inDiscardedGroup) {

        public boolean isDefinition() {
            return shndxKind != Enums.SymbolShndx.UNDEF;
        }

        public boolean isStrongDefined() {
            return isDefinition()
                    && binding != Enums.SymbolBinding.LOCAL
                    && shndxKind != Enums.SymbolShndx.COMMON
                    && visibility != Enums.SymbolVisibility.HIDDEN
                    && visibility != Enums.SymbolVisibility.INTERNAL
                    && !inDiscardedGroup
                    && !versionHidden;
        }

        public boolean isCommon() {
            return shndxKind == Enums.SymbolShndx.COMMON;
        }
    }

    public record ElfRelocation(
            long offset,
            long addend,
            int type,
            String typeName,
            int symbolIndex,
            String symbolName,
            String sectionName,
            boolean isCopy,
            boolean isJumpSlot,
            boolean isRelative) {
    }

    public record SectionGroup(
            String signature,
            Enums.GroupSelection selection,
            List<Integer> sectionIndices,
            boolean discarded,
            String ownerObjectLabel) {
    }

    public record ElfObject(
            String label,
            String binaryId,
            String archiveName,
            String memberName,
            byte[] hash,
            Enums.ElfClass elfClass,
            Enums.DataEncoding dataEncoding,
            Enums.MachineType machine,
            Enums.ObjectKind kind,
            String soname,
            List<String> needed,
            List<SectionInfo> sections,
            List<ElfSymbol> symbols,
            List<ElfRelocation> relocations,
            List<VersionNode> versionNodes,
            List<SectionGroup> groups,
            String parseError) {

        public boolean parseFailed() {
            return parseError != null && !parseError.isBlank();
        }

        public ElfRelocation relocationAt(long offset) {
            for (ElfRelocation r : relocations) {
                if (r.offset == offset) {
                    return r;
                }
            }
            return null;
        }
    }

    public static List<ElfObject> memberList(ElfObject archiveIndexOrNull) {
        return new ArrayList<>();
    }
}
