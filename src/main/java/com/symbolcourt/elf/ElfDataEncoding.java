package com.symbolcourt.elf;

/** ELF data encoding (endianness). */
public enum ElfDataEncoding {
    NONE(0),
    LSB(1),
    MSB(2);

    private final int value;

    ElfDataEncoding(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static ElfDataEncoding fromValue(int value) {
        for (ElfDataEncoding candidate : values()) {
            if (candidate.value == value) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown EI_DATA " + value);
    }
}
