package org.tribunal.model;

import java.util.List;

public record Scheme(Long id, String label, String policy, List<Long> orderedBinaryIds,
                     ResolutionResult result) {
}
