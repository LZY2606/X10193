package court.link;

import java.util.Objects;

/**
 * A resolution key. Same name does NOT mean same candidate: a versioned reference
 * (foo@VERS_1.1) is distinct from an unversioned one (foo@) and from other versions.
 */
public final class SymbolKey {
    public final String name;
    public final String version; // resolved version name; "" for unversioned/base
    public final boolean weakRef;

    public SymbolKey(String name, String version, boolean weakRef) {
        this.name = name == null ? "" : name;
        this.version = version == null ? "" : version;
        this.weakRef = weakRef;
    }

    public static SymbolKey unversioned(String name) { return new SymbolKey(name, "", false); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof SymbolKey)) return false;
        SymbolKey k = (SymbolKey) o;
        return weakRef == k.weakRef && name.equals(k.name) && version.equals(k.version);
    }
    @Override public int hashCode() { return Objects.hash(name, version, weakRef); }
    @Override public String toString() {
        return name + (version.isEmpty() ? "@" : "@" + version) + (weakRef ? " [weak-ref]" : "");
    }
}
