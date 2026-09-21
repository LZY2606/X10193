package org.tribunal.model;

public record SymbolReference(String unitId, String unitName, String name, String binding,
                              String requestedVersion, String requestKey, boolean versionedRequest,
                              Integer rawVersionIndex, RelocationInfo relocation, boolean strong,
                              boolean copyRelocation) {
}
