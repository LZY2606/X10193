package com.symbolcourt.engine;

import java.util.List;

public record TimelineEvent(
        int sequence,
        String kind,
        String title,
        String detail,
        String objectId,
        String objectName,
        String memberName,
        String symbol,
        String before,
        String after,
        List<SymbolTableRow> addedSymbols,
        List<String> removedSections) {
}
