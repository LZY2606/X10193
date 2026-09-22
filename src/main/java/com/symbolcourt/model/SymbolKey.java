package com.symbolcourt.model;

import java.util.Objects;

/**
 * A symbol name is not an identity by itself: the versioned name distinguishes
 * candidates. The key carries the requested version (or null for unversioned),
 * a "default-only" flag (unversioned refs bind only to the default node) and
 * the defining/shared object context for version matching.
 */
public final class SymbolKey {
    private final String name;
    private final String version;
    private final boolean defaultOnly;

    public SymbolKey(String name, String version, boolean defaultOnly) {
        this.name = name;
        this.version = version;
        this.defaultOnly = defaultOnly;
    }

    public static SymbolKey unversioned(String name) {
        return new SymbolKey(name, null, true);
    }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public boolean isDefaultOnly() { return defaultOnly; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolKey other)) return false;
        return Objects.equals(name, other.name) && Objects.equals(version, other.version)
                && defaultOnly == other.defaultOnly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, defaultOnly);
    }

    @Override
    public String toString() {
        return version == null ? name : name + "(" + version + (defaultOnly ? ",default" : "") + ")";
    }
}
