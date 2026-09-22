package com.symbolcourt.engine;

import java.util.List;

public record ResolutionEntry(
        String key,
        String symbol,
        String requiredVersion,
        String status,
        String statusDetail,
        List<Candidate> candidates,
        List<String> winnerObjectIds,
        List<AffectedRelocation> relocations) {
}
