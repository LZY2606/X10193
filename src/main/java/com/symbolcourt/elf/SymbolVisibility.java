package com.symbolcourt.elf;

/** st_other & 0x3: STV_*. */
public enum SymbolVisibility {
    DEFAULT(0),
    INTERNAL(1),
    HIDDEN(2),
    PROTECTED(3);

    private final int value;

    SymbolVisibility(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static SymbolVisibility fromValue(int value) {
        for (SymbolVisibility candidate : values()) {
            if (candidate.value == value) {
                return candidate;
            }
        }
        return DEFAULT;
    }
}
