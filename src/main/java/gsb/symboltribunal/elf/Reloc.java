package gsb.symboltribunal.elf;

public record Reloc(
        long offset,
        long addend,
        int type,
        String typeName,
        int symIndex,
        String symName,
        int symBind,
        int symType,
        int symVersionIndex,
        boolean symVersionHidden,
        String sectionName,
        int sectionIndex,
        boolean rela) {

    public boolean referencesSymbol() {
        return symName != null && !symName.isEmpty();
    }
}
