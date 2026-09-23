package com.gsb.symbolcourt.elf;

public record Relocation(
        long offset,
        int type,
        int symbolIndex,
        String symbolName,
        boolean addendPresent,
        long addend,
        int applySectionIndex,
        String applySectionName
) {
    public boolean isCopy() {
        return type == ElfConstants.R_X86_64_COPY;
    }

    public boolean isDynamicSlot() {
        return type == ElfConstants.R_X86_64_JUMP_SLOT
                || type == ElfConstants.R_X86_64_GLOB_DAT
                || type == ElfConstants.R_X86_64_COPY;
    }

    public String typeName() {
        return switch (type) {
            case ElfConstants.R_X86_64_COPY -> "R_X86_64_COPY";
            case ElfConstants.R_X86_64_JUMP_SLOT -> "R_X86_64_JUMP_SLOT";
            case ElfConstants.R_X86_64_GLOB_DAT -> "R_X86_64_GLOB_DAT";
            case ElfConstants.R_X86_64_RELATIVE -> "R_X86_64_RELATIVE";
            case ElfConstants.R_X86_64_64 -> "R_X86_64_64";
            case ElfConstants.R_X86_64_PC32 -> "R_X86_64_PC32";
            case ElfConstants.R_X86_64_PLT32 -> "R_X86_64_PLT32";
            case ElfConstants.R_X86_64_32 -> "R_X86_64_32";
            default -> "R_" + type;
        };
    }
}
