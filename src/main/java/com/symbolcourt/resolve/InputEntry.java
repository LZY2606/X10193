package com.symbolcourt.resolve;

/** One file in the user-supplied, explicit load order. */
public final class InputEntry {
    public final String label;
    public final byte[] content;

    public InputEntry(String label, byte[] content) {
        this.label = label;
        this.content = content;
    }
}
