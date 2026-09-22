package com.example.symbolcourt.elf;

public enum ElfData { LSB(1), MSB(2);
    public final int code;
    ElfData(int code) { this.code = code; }
    public static ElfData of(int code) throws ElfException {
        return switch (code) {
            case 1 -> LSB;
            case 2 -> MSB;
            default -> throw new ElfException("unknown EI_DATA " + code);
        };
    }
}
