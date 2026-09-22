package com.tribunal.model;

public record ElfRelocation(
        String section,
        long offset,
        long addend,
        int symbolIndex,
        String symbolName,
        int type,
        String typeName,
        boolean copy,
        int machine
) {}
