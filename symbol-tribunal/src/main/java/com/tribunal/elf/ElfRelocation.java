package com.tribunal.elf;

public final class ElfRelocation {
    public final long offset;
    public final long type;
    public final int symbolIndex;
    public final String symbolName;
    public final boolean addendPresent;
    public final long addend;
    public final String sectionName;

    public ElfRelocation(long offset, long type, int symbolIndex, String symbolName,
                         boolean addendPresent, long addend, String sectionName) {
        this.offset = offset;
        this.type = type;
        this.symbolIndex = symbolIndex;
        this.symbolName = symbolName;
        this.addendPresent = addendPresent;
        this.addend = addend;
        this.sectionName = sectionName;
    }

    public boolean isCopyReloc() {
        return type == ElfConstants.R_X86_64_COPY;
    }

    public String typeName() {
        return switch ((int) type) {
            case ElfConstants.R_X86_64_COPY -> "R_X86_64_COPY";
            case ElfConstants.R_X86_64_GLOB_DAT -> "R_X86_64_GLOB_DAT";
            case ElfConstants.R_X86_64_JUMP_SLOT -> "R_X86_64_JUMP_SLOT";
            case ElfConstants.R_X86_64_RELATIVE -> "R_X86_64_RELATIVE";
            default -> "R_TYPE_" + type;
        };
    }
}
