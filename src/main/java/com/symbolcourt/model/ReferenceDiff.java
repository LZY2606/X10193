package com.symbolcourt.model;

/** Semantic comparison of the same logical reference across two scheme versions. */
public final class ReferenceDiff {
    public enum Kind {
        SAME,              // same provider, same binding
        ADDRESS_CHANGED,   // same provider unit, only address/value moved
        REBOUND,           // provider changed to a different unit
        NEWLY_RESOLVED,    // unresolved -> resolved
        BECAME_UNRESOLVED, // resolved -> unresolved
        STATE_CHANGED,     // e.g. RESOLVED -> AMBIGUOUS/CONFLICT/VERSION_UNMET
        COPY_FLIPPED,      // COPY relocation legality changed
        NEW_REFERENCE,
        DISAPPEARED
    }

    private final String key;
    private final String symbol;
    private final String oldProvider;
    private final String newProvider;
    private final long oldAddress;
    private final long newAddress;
    private final ResolutionStatus oldStatus;
    private final ResolutionStatus newStatus;
    private final Kind kind;
    private final String detail;

    public ReferenceDiff(String key, String symbol, String oldProvider, String newProvider,
                         long oldAddress, long newAddress, ResolutionStatus oldStatus,
                         ResolutionStatus newStatus, Kind kind, String detail) {
        this.key = key;
        this.symbol = symbol;
        this.oldProvider = oldProvider;
        this.newProvider = newProvider;
        this.oldAddress = oldAddress;
        this.newAddress = newAddress;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.kind = kind;
        this.detail = detail;
    }

    public String getKey() { return key; }
    public String getSymbol() { return symbol; }
    public String getOldProvider() { return oldProvider; }
    public String getNewProvider() { return newProvider; }
    public long getOldAddress() { return oldAddress; }
    public long getNewAddress() { return newAddress; }
    public ResolutionStatus getOldStatus() { return oldStatus; }
    public ResolutionStatus getNewStatus() { return newStatus; }
    public Kind getKind() { return kind; }
    public String getDetail() { return detail; }
}
