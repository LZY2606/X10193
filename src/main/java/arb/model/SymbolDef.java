package arb.model;

public record SymbolDef(
        int index,
        String name,
        long value,
        long size,
        Enums.Binding binding,
        Enums.SymbolType type,
        Enums.Visibility visibility,
        int sectionIndex,
        boolean dynamic,
        int versionIndex,
        String version
) {
    public boolean isUndefined() {
        return sectionIndex == 0;
    }

    public boolean isCommon() {
        return sectionIndex == 0xFFF2;
    }

    public boolean defined() {
        return !isUndefined();
    }

    public boolean isCopyReloc() {
        return type == Enums.SymbolType.COMMON
                || (dynamic && name != null && name.startsWith("__copy__"));
    }
}
