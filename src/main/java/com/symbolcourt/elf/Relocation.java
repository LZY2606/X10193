package com.symbolcourt.elf;

public record Relocation(
        String sectionName,
        long offset,
        long type,
        int symbolIndex,
        String symbolName,
        Long addend,
        boolean isRela) {

    public boolean isCopyRelocation(int machine) {
        return switch (machine) {
            case ElfConstants.EM_X86_64 -> type == ElfConstants.R_X86_64_COPY;
            case ElfConstants.EM_386 -> type == ElfConstants.R_386_COPY;
            default -> typeName(machine).contains("COPY");
        };
    }

    public boolean isRelative() {
        // relative relocations resolve without a symbol and are not adjudicated
        return false;
    }

    public String typeName(int machine) {
        if (machine == ElfConstants.EM_X86_64) {
            return switch ((int) type) {
                case ElfConstants.R_X86_64_NONE -> "R_X86_64_NONE";
                case ElfConstants.R_X86_64_64 -> "R_X86_64_64";
                case ElfConstants.R_X86_64_PC32 -> "R_X86_64_PC32";
                case ElfConstants.R_X86_64_GLOB_DAT -> "R_X86_64_GLOB_DAT";
                case ElfConstants.R_X86_64_JUMP_SLOT -> "R_X86_64_JUMP_SLOT";
                case ElfConstants.R_X86_64_RELATIVE -> "R_X86_64_RELATIVE";
                case ElfConstants.R_X86_64_COPY -> "R_X86_64_COPY";
                case ElfConstants.R_X86_64_32 -> "R_X86_64_32";
                case ElfConstants.R_X86_64_PLT32 -> "R_X86_64_PLT32";
                default -> "R_" + machine + "_" + type;
            };
        }
        if (machine == ElfConstants.EM_386) {
            return switch ((int) type) {
                case ElfConstants.R_386_32 -> "R_386_32";
                case ElfConstants.R_386_PC32 -> "R_386_PC32";
                case ElfConstants.R_386_GLOB_DAT -> "R_386_GLOB_DAT";
                case ElfConstants.R_386_JMP_SLOT -> "R_386_JMP_SLOT";
                case ElfConstants.R_386_RELATIVE -> "R_386_RELATIVE";
                case ElfConstants.R_386_COPY -> "R_386_COPY";
                default -> "R_386_" + type;
            };
        }
        return "R_M" + machine + "_" + type;
    }
}
