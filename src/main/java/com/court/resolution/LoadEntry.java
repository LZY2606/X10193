package com.court.resolution;

/**
 * One explicit position in the load/search order.
 * Kind: ELF -> parsed directly; ARCHIVE -> lazy member extraction.
 */
public class LoadEntry {
    public final int order;
    public final String label;
    public final Kind kind;
    public final byte[] content;

    public enum Kind { ELF, ARCHIVE }

    public LoadEntry(int order, String label, Kind kind, byte[] content) {
        this.order = order;
        this.label = label;
        this.kind = kind;
        this.content = content;
    }
}
