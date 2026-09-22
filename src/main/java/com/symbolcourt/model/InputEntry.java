package com.symbolcourt.model;

/** One entry of the explicit load order supplied by the user. */
public record InputEntry(int ordinal, String unitId, String displayName, Kind kind) {
    public enum Kind { OBJECT, SHARED, ARCHIVE }
}
