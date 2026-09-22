package com.symbolcourt.engine;

public record SymbolTableRow(
        String symbol,
        String version,
        boolean defaultVersion,
        String binding,
        String visibility,
        String ownerObjectId,
        String ownerName,
        String memberName,
        String section,
        long value,
        long size,
        String kind) {
}
