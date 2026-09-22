package com.example.symbolcourt.elf;

/** Platforms for which relocation semantics differ. */
public enum ElfMachine {
    NONE(0), X86(3), PPC(20), ARM(40), X86_64(62), AARCH64(183), OTHER(-1);

    public final int code;
    ElfMachine(int code) { this.code = code; }

    public static ElfMachine of(int code) {
        return switch (code) {
            case 0 -> NONE;
            case 3 -> X86;
            case 20 -> PPC;
            case 40 -> ARM;
            case 62 -> X86_64;
            case 183 -> AARCH64;
            default -> OTHER;
        };
    }

    /** Relocation type numbers that act as copy relocations on this platform. */
    public int copyRelocationType() {
        return switch (this) {
            case X86 -> 5;       // R_386_COPY
            case X86_64 -> 8;    // R_X86_64_COPY
            case PPC -> 19;     // R_PPC_COPY
            case ARM -> 20;     // R_ARM_COPY
            case AARCH64 -> 1024; // R_AARCH64_COPY
            default -> -1;
        };
    }
}
