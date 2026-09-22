package court.symbol.model;

import court.symbol.elf.ElfSymbol;

public record SymbolProvider(Unit unit, ElfSymbol symbol, String versionName, boolean defaultVersion,
                             boolean versionRequirement, String versionLibrary, String groupSignature,
                             int score, String rankReason) {
    public String id() {
        return unit.id() + "#" + symbol.index + "#" + (versionName == null ? "" : versionName);
    }

    public String label() {
        String suffix = versionName == null || versionName.isEmpty() ? "" : "@" + versionName;
        return unit.displayName() + "::" + symbol.name + suffix;
    }
}
