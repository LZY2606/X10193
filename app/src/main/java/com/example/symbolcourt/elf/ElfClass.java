package com.example.symbolcourt.elf;

public enum ElfClass { ELF32(1), ELF64(2);
    public final int code;
    ElfClass(int code) { this.code = code; }
    public static ElfClass of(int code) throws ElfException {
        return switch (code) {
            case 1 -> ELF32;
            case 2 -> ELF64;
            default -> throw new ElfException("unknown EI_CLASS " + code);
        };
    }
}
