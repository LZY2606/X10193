package com.symbolcourt.resolve;

import java.util.Objects;

/** A symbol name together with its requested/provided version.
 *  Same name but different version => different key, by design. */
public final class SymbolKey {
    public final String name;
    public final String version;

    public SymbolKey(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public static SymbolKey unversioned(String name) {
        return new SymbolKey(name, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolKey k)) return false;
        return Objects.equals(name, k.name) && Objects.equals(version, k.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }

    @Override
    public String toString() {
        return version == null ? name : name + "@" + version;
    }
}
