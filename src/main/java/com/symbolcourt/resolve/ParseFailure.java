package com.symbolcourt.resolve;

/** A corrupt object is isolated: recorded here, never aborts the whole link. */
public final class ParseFailure {
    public final String label;
    public final String reason;

    public ParseFailure(String label, String reason) {
        this.label = label;
        this.reason = reason;
    }
}
