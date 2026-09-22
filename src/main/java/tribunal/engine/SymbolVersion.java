package tribunal.engine;

/**
 * Versioned identity of a reference or definition. Same name + different version
 * means different candidates.
 */
public record SymbolVersion(String name, String version, boolean nonDefault) {
    public static SymbolVersion unversioned(String name) {
        return new SymbolVersion(name, null, false);
    }

    public String display() {
        if (version == null) return name;
        return name + (nonDefault ? "@" : "@@") + version;
    }
}
