package com.example.tribunal.core;

public record ElfRelocation(
        int index,
        String ownerSection,
        long offset,
        int type,
        long addend,
        int symbolIndex,
        String symbolName,
        boolean copyRelocation
) {
}
