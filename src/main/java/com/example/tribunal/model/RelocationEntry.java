package com.example.tribunal.model;

public record RelocationEntry(
        String id,
        long offset,
        int sectionIndex,
        String sectionName,
        int typeCode,
        String typeName,
        int symbolIndex,
        String symbolName,
        String binding,
        String visibility,
        String versionName,
        boolean versionDefault,
        long addend,
        boolean copy,
        boolean relative
) {
}
