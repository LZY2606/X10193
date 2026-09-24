package arb.model;

public record Reloc(
        long offset,
        long type,
        int symbolIndex,
        String symbolName,
        long addend,
        boolean relativeSection,
        String sectionName,
        boolean copy
) {
}
