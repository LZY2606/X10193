package com.court.model;

public record Relocation(
        int sectionIndex,
        long offset,
        int symbolIndex,
        String symbolName,
        int type,
        Long addend,
        boolean rela,
        boolean dynamicRef) {

    public static final int R_X86_64_64 = 1;
    public static final int R_X86_64_PC32 = 2;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    public static final int R_X86_64_32 = 10;
    public static final int DYNREF = -1;

    public String typeName() {
        if (dynamicRef) return "DYNAMIC_REF";
        return switch (type) {
            case 1 -> "R_X86_64_64";
            case 2 -> "R_X86_64_PC32";
            case 6 -> "R_X86_64_GLOB_DAT";
            case 5 -> "R_X86_64_COPY";
            case 7 -> "R_X86_64_JUMP_SLOT";
            case 8 -> "R_X86_64_RELATIVE";
            case 10 -> "R_X86_64_32";
            default -> "R_" + type;
        };
    }
}
