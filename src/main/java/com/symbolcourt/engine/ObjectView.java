package com.symbolcourt.engine;

import java.util.List;

public record ObjectView(
        String id,
        String name,
        String kind,
        String format,
        int machine,
        String soname,
        int loadIndex,
        boolean extracted,
        String extractedFromArchiveId,
        List<String> members,
        List<String> needed,
        List<String> exportedSymbols,
        boolean corrupt,
        String corruptReason) {
}
