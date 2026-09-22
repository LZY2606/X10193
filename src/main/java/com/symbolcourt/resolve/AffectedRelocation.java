package com.symbolcourt.resolve;

public final class AffectedRelocation {
    public final String unitId;
    public final String unitLabel;
    public final String section;
    public final String type;
    public final long offset;
    public final long addend;
    public final long resolvedAddress;
    public final boolean copyRelocation;

    public AffectedRelocation(String unitId, String unitLabel, String section, String type,
                              long offset, long addend, long resolvedAddress,
                              boolean copyRelocation) {
        this.unitId = unitId;
        this.unitLabel = unitLabel;
        this.section = section;
        this.type = type;
        this.offset = offset;
        this.addend = addend;
        this.resolvedAddress = resolvedAddress;
        this.copyRelocation = copyRelocation;
    }
}
