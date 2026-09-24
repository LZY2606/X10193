package com.symbolcourt.elf;

/**
 * A relocation that carries a symbol reference.
 * {@code copy} is derived from the relocation type (R_*_COPY).
 */
public record RelocInfo(
        int index,
        int symbolIndex,
        String symbolName,
        int type,
        long offset,
        long addend,
        boolean sectionDynamic,
        boolean copy
) {
    // x86_64
    public static final int R_X86_64_COPY = 5;
    // i386
    public static final int R_386_COPY = 5;
}
