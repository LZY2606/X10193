package tribunal.elf;

/**
 * One symbol table entry after resolving section-index escapes.
 *
 * @param versionIndex raw versym index when a dynamic version table exists;
 *                     null for static symbol tables.
 */
public record ElfSymbol(
        int index,
        String name,
        Elf.Bind bind,
        Elf.Kind kind,
        Elf.Visibility visibility,
        long value,
        long size,
        int sectionIndex,
        boolean defined,
        boolean common,
        Integer versionIndex,
        boolean hiddenVersion
) {
    public boolean isStrongDef() {
        return defined && bind.strongGlobal() && !common;
    }

    public boolean isWeakDef() {
        return defined && bind == Elf.Bind.WEAK;
    }

    public boolean isUndefined() {
        return !defined && !common;
    }
}
