package court.symbol.model;

import java.util.List;
import java.util.Map;

/**
 * One replayable step of the adjudication: archive extraction, COMDAT
 * elimination, symbol table mutation, or the final sweep of decisions.
 */
public record StepEvent(int sequence,
                        String kind,          // SCAN, ARCHIVE_EXTRACT, COMDAT, DECISION, FINISH
                        String title,
                        String detail,
                        String providerId,
                        List<String> activeProviders,
                        Map<String, Integer> tableCounts,
                        Decision decision) {
}
