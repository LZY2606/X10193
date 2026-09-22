package com.symbolcourt.model;

/**
 * One provider offered for an undefined reference. A candidate keeps the
 * reason it was eliminated during arbitration, so every verdict can explain
 * the full candidate set, not only the winner.
 */
public final class Candidate {
    public enum Status { WON, ELIMINATED, TIED, NOT_CONSIDERED }

    public enum Rank {
        STRONG_DEFINITION,
        COMMON,
        WEAK_DEFINITION,
        UNDEFINED
    }

    private final String unitId;
    private final String unitDisplayName;
    private final int loadIndex;
    private final int bind;
    private final int type;
    private final int visibility;
    private final String versionName;
    private final boolean defaultVersion;
    private final boolean common;
    private final long commonSize;
    private final long commonAlignment;
    private final boolean comdatProvided;
    private final long address;
    private final Rank rank;
    private Status status = Status.NOT_CONSIDERED;
    private String eliminationReason;

    public Candidate(String unitId, String unitDisplayName, int loadIndex, int bind, int type,
                     int visibility, String versionName, boolean defaultVersion,
                     boolean common, long commonSize, long commonAlignment,
                     boolean comdatProvided, long address, Rank rank) {
        this.unitId = unitId;
        this.unitDisplayName = unitDisplayName;
        this.loadIndex = loadIndex;
        this.bind = bind;
        this.type = type;
        this.visibility = visibility;
        this.versionName = versionName;
        this.defaultVersion = defaultVersion;
        this.common = common;
        this.commonSize = commonSize;
        this.commonAlignment = commonAlignment;
        this.comdatProvided = comdatProvided;
        this.address = address;
        this.rank = rank;
    }

    public String getUnitId() { return unitId; }
    public String getUnitDisplayName() { return unitDisplayName; }
    public int getLoadIndex() { return loadIndex; }
    public int getBind() { return bind; }
    public int getType() { return type; }
    public int getVisibility() { return visibility; }
    public String getVersionName() { return versionName; }
    public boolean isDefaultVersion() { return defaultVersion; }
    public boolean isCommon() { return common; }
    public long getCommonSize() { return commonSize; }
    public long getCommonAlignment() { return commonAlignment; }
    public boolean isComdatProvided() { return comdatProvided; }
    public long getAddress() { return address; }
    public Rank getRank() { return rank; }
    public Status getStatus() { return status; }
    public String getEliminationReason() { return eliminationReason; }

    public void win() { this.status = Status.WON; }
    public void tie() { this.status = Status.TIED; }
    public void eliminate(String reason) {
        this.status = Status.ELIMINATED;
        this.eliminationReason = reason;
    }
}
