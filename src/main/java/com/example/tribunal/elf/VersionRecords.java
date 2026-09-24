package com.example.tribunal.elf;

import java.util.List;

/**
 * Version metadata for an ELF object: GNU version definitions, version needs
 * and the per-symbol version index array.
 */
public final class VersionRecords {

    /** versym index -> resolved version name; null means local/none. */
    private final List<String> symbolVersionNames;
    /** versym index -> true when the non-default bit (0x8000) is set. */
    private final List<Boolean> symbolNonDefault;
    private final List<VersionDef> definitions;
    private final List<VersionNeed> needs;

    public VersionRecords(List<String> symbolVersionNames,
                          List<Boolean> symbolNonDefault,
                          List<VersionDef> definitions,
                          List<VersionNeed> needs) {
        this.symbolVersionNames = symbolVersionNames;
        this.symbolNonDefault = symbolNonDefault;
        this.definitions = definitions;
        this.needs = needs;
    }

    public List<String> getSymbolVersionNames() {
        return symbolVersionNames;
    }

    public List<Boolean> getSymbolNonDefault() {
        return symbolNonDefault;
    }

    public List<VersionDef> getDefinitions() {
        return definitions;
    }

    public List<VersionNeed> getNeeds() {
        return needs;
    }

    public String versionNameFor(int symbolIndex) {
        if (symbolIndex < 0 || symbolIndex >= symbolVersionNames.size()) {
            return null;
        }
        return symbolVersionNames.get(symbolIndex);
    }

    public boolean isNonDefault(int symbolIndex) {
        if (symbolIndex < 0 || symbolIndex >= symbolNonDefault.size()) {
            return false;
        }
        return Boolean.TRUE.equals(symbolNonDefault.get(symbolIndex));
    }

    public record VersionDef(int index, String name, boolean base, boolean hidden,
                             List<String> aliases) {
    }

    public record VersionNeed(String fileName, List<Entry> entries) {
        public record Entry(int hash, int index, String name, boolean weak) {
        }
    }
}
