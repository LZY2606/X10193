package com.gsb.tribunal.elf;

public record Relocation(
        String targetSection,
        long offset,
        int type,
        long addend,
        int symbolIndex,
        String symbolName,
        boolean relative) {
}
