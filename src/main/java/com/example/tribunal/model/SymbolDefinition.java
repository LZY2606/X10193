package com.example.tribunal.model;

public record SymbolDefinition(
        int symbolIndex,
        String name,
        String binding,
        String type,
        String visibility,
        boolean defined,
        int sectionIndex,
        String sectionName,
        long value,
        long size,
        int rawVersionIndex,
        String versionName,
        boolean versionDefault,
        boolean versionLocal,
        boolean versionGlobal,
        boolean dynamic,
        boolean exported,
        String comdatSignature,
        boolean common,
        boolean syntheticCopy
) {
    public String qualifiedVersion() {
        if (versionName == null || versionName.isBlank()) {
            return "";
        }
        return versionName + (versionDefault ? "@" : "@@");
    }
}
