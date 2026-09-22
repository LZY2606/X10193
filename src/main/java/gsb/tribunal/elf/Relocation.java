package gsb.tribunal.elf;

public record Relocation(
        int sectionIndex,
        String sectionName,
        long offset,
        long addend,
        int type,
        String typeName,
        int symIndex,
        String symbol,
        boolean dynamic,
        boolean copy) {
}
