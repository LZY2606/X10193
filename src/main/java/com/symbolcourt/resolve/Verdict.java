package com.symbolcourt.resolve;

import java.util.ArrayList;
import java.util.List;

/** The adjudication of one undefined symbol reference. */
public final class Verdict {
    public final String key;             // name + required version
    public final String symbolName;
    public final String requiredVersion;
    public final String referrerId;
    public final String referrerLabel;
    public final boolean weakReference;

    public final List<Candidate> candidates = new ArrayList<>();
    /** Winners; more than one means an unresolved ambiguity. */
    public final List<Provider> winners = new ArrayList<>();
    public boolean ambiguous;
    public boolean unresolved;
    public boolean copyRelocation;
    public String resolution;            // RESOLVED / AMBIGUOUS / UNRESOLVED / COPY
    public long resolvedAddress;
    public String note = "";
    public final List<AffectedRelocation> affectedRelocations = new ArrayList<>();

    public Verdict(String symbolName, String requiredVersion, String referrerId,
                   String referrerLabel, boolean weakReference) {
        this.symbolName = symbolName;
        this.requiredVersion = requiredVersion;
        this.referrerId = referrerId;
        this.referrerLabel = referrerLabel;
        this.weakReference = weakReference;
        this.key = referrerId + "|" + symbolName
                + (requiredVersion == null ? "" : "@" + requiredVersion);
    }

    public Provider soleWinner() {
        return winners.size() == 1 ? winners.get(0) : null;
    }
}
