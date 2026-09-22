package com.symbolcourt.engine;

public record AffectedRelocation(
        String objectId,
        String objectName,
        String memberName,
        String section,
        long offset,
        long type,
        String relocKind,
        String symbol,
        long addend,
        boolean copy,
        String referenceBinding) {
}
