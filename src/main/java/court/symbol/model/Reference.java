package court.symbol.model;

/**
 * An undefined (or interposable) use of a symbol from one object.
 * One reference aggregates all relocations sharing its source target.
 */
public record Reference(String id,
                        String providerId,
                        String providerLabel,
                        int loadOrder,
                        String symbol,
                        String version,
                        boolean weakRef,
                        boolean copyReloc,
                        String requestedBy,
                        java.util.List<RelocRef> relocations) {

    public RefKey key() {
        return new RefKey(providerId, symbol, version);
    }

    public record RelocRef(String section, long offset, long addend, int type, String typeName) {
    }
}
