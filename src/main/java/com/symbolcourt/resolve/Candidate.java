package com.symbolcourt.resolve;

/** A provider plus why it was rejected (null when it is the winner/tied). */
public final class Candidate {
    public final Provider provider;
    public final RejectReason reject;
    public final boolean tied;

    public Candidate(Provider provider, RejectReason reject, boolean tied) {
        this.provider = provider;
        this.reject = reject;
        this.tied = tied;
    }
}
