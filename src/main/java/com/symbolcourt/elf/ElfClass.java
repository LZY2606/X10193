package com.symbolcourt.elf;

/** ELF identification classes. */
public enum ElfClass {
    NONE(0),
    ELF32(1),
    ELF64(2);

    private final int value;

    ElfClass(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static ElfClass fromValue(int value) {
        for (ElfClass candidate : values()) {
            if (candidate.value == value) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown EI_CLASS " + value);
    }
}
