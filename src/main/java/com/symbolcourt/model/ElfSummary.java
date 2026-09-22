package com.symbolcourt.model;

import java.util.List;

/** Lightweight, JSON-serializable summary of a parsed ELF unit for the UI. */
public record ElfSummary(
        boolean elf64,
        String endian,
        int machine,
        String type,
        String soname,
        List<String> needed,
        List<String> comdatSignatures,
        int symbolCount,
        int dynamicSymbolCount,
        int relocationCount,
        int versionDefinitionCount,
        int versionRequirementCount) {
}
