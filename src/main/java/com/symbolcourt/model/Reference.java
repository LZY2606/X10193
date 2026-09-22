package com.symbolcourt.model;

/**
 * An undefined reference made by one unit: identified by its origin unit and
 * relocation location, carrying the requested name/version and binding.
 */
public final class Reference {
    private final String referenceId;
    private final String unitId;
    private final String symbolName;
    private final SymbolKey key;
    private final int bind;
    private final boolean copyRelocation;
    private final String sectionName;
    private final long offset;
    private final long addend;
    private final int relocType;
    private final String requiredFromFile; // DT_NEEDED context for verneed refs
    private final boolean weakUnversioned;

    public Reference(String referenceId, String unitId, String symbolName, SymbolKey key, int bind,
                     boolean copyRelocation, String sectionName, long offset, long addend,
                     int relocType, String requiredFromFile, boolean weakUnversioned) {
        this.referenceId = referenceId;
        this.unitId = unitId;
        this.symbolName = symbolName;
        this.key = key;
        this.bind = bind;
        this.copyRelocation = copyRelocation;
        this.sectionName = sectionName;
        this.offset = offset;
        this.addend = addend;
        this.relocType = relocType;
        this.requiredFromFile = requiredFromFile;
        this.weakUnversioned = weakUnversioned;
    }

    public String getReferenceId() { return referenceId; }
    public String getUnitId() { return unitId; }
    public String getSymbolName() { return symbolName; }
    public SymbolKey getKey() { return key; }
    public int getBind() { return bind; }
    public boolean isCopyRelocation() { return copyRelocation; }
    public String getSectionName() { return sectionName; }
    public long getOffset() { return offset; }
    public long getAddend() { return addend; }
    public int getRelocType() { return relocType; }
    public String getRequiredFromFile() { return requiredFromFile; }
    public boolean isWeakUnversioned() { return weakUnversioned; }
}
