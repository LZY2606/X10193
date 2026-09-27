package com.symbolcourt.elf;

/** st_info lower nibble: STT_*. */
public enum SymbolType {
    NOTYPE(0),
    OBJECT(1),
    FUNC(2),
    SECTION(3),
    FILE(4),
    COMMON(5),
    TLS(6),
    GNU_IFUNC(10);

    private final int value;

    SymbolType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static SymbolType fromValue(int value) {
        for (SymbolType candidate : values()) {
            if (candidate.value == value) {
                return candidate;
            }
        }
        return NOTYPE;
    }
}
