package com.symbolcourt.resolve;

public class RefRecord {
    public final InputUnit unit;
    public final String name;
    public final String requestedVersion;
    public final boolean weak;
    public final boolean fromDynamic;

    public RefRecord(InputUnit unit, String name, String requestedVersion, boolean weak,
                     boolean fromDynamic) {
        this.unit = unit;
        this.name = name;
        this.requestedVersion = requestedVersion;
        this.weak = weak;
        this.fromDynamic = fromDynamic;
    }

    public SymbolKey key() {
        return new SymbolKey(name, requestedVersion);
    }
}
