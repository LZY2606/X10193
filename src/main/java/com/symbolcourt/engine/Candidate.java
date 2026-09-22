package com.symbolcourt.engine;

public record Candidate(
        String objectId,
        String objectName,
        String memberName,
        String section,
        String binding,
        String visibility,
        String version,
        boolean defaultVersion,
        long value,
        long size,
        boolean selected,
        String rejectedReason) {
}
