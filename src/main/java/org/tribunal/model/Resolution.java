package org.tribunal.model;

import java.util.List;

public record Resolution(String requestKey, String symbol, String requestedVersion,
                         String requesterId, String requesterName, boolean strong,
                         List<CandidateOutcome> candidates, List<String> selectedProviderKeys,
                         String status, String reason, List<RelocationInfo> affectedRelocations,
                         boolean copyRelocation) {
}
