package com.example.tribunal.model;

import java.util.List;

public record BinaryObject(
        String objectKey,
        String artifactId,
        String fileName,
        String archiveName,
        String memberName,
        ElfHeader header,
        String soname,
        List<String> needed,
        List<SymbolDefinition> symbols,
        List<RelocationEntry> relocations,
        List<SectionGroup> groups,
        List<VersionDefinition> versionDefinitions,
        List<VersionNeed> versionNeeds,
        String parseError
) {
    public boolean valid() {
        return parseError == null && header != null && header.valid();
    }

    public String displayName() {
        if (archiveName != null) {
            return archiveName + "(" + memberName + ")";
        }
        return fileName;
    }
}
