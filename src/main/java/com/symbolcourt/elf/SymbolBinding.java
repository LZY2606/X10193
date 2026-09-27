package com.symbolcourt.elf;

/** st_info upper nibble: STB_*. */
public enum SymbolBinding {
    LOCAL(0),
    GLOBAL(1),
    WEAK(2),
    GNU_UNIQUE(10);

    private final int value;

    SymbolBinding(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static SymbolBinding fromValue(int value) {
        for (SymbolBinding candidate : values()) {
            if (candidate.value == value) {
                return candidate;
            }
        }
        return SymbolBinding.GLOBAL;
    }

    public boolean isStrongGlobal() {
        return this == GLOBAL || this == GNU_UNIQUE;
    }
}
