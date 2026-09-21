package org.tribunal.model;

public record RelocationInfo(String unitId, long offset, int type, String typeName, int symbolIndex,
                             String symbol, String requestedVersion, long addend, boolean hasAddend,
                             String sectionName, boolean copyRelocation) {
}
