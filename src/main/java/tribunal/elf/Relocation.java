package tribunal.elf;

/**
 * A relocation that may need a symbol.
 *
 * @param type   architecture specific relocation number
 * @param copy   true for copy relocations (e.g. R_ARM_COPY / R_X86_64_COPY / R_AARCH64_COPY)
 * @param jmpSlot true for PLT/GOT jump slots (lazy binding targets)
 */
public record Relocation(
        int sectionIndex,
        long offset,
        int type,
        long addend,
        int symbolIndex,
        boolean copy,
        boolean jmpSlot,
        int targetSection
) {}
