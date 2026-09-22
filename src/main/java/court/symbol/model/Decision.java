package court.symbol.model;

import java.util.List;

/**
 * The verdict for one reference: all candidates considered, why the losers
 * were rejected, and who finally provides the symbol (or why not).
 */
public record Decision(Reference reference,
                       List<CandidateView> candidates,
                       SymbolCandidate winner,
                       List<SymbolCandidate> ties,
                       String status,   // RESOLVED, AMBIGUOUS, UNRESOLVED, COMMON_MERGE
                       String rationale,
                       long simulatedAddress) {

    public static final String RESOLVED = "RESOLVED";
    public static final String AMBIGUOUS = "AMBIGUOUS";
    public static final String UNRESOLVED = "UNRESOLVED";
    public static final String COMMON_MERGE = "COMMON_MERGE";

    /**
     * A candidate as seen during one decision: alive or rejected, with the
     * exact reason it lost.
     */
    public record CandidateView(SymbolCandidate candidate,
                                String state,       // WINNER, TIE, REJECTED
                                String reason,
                                long simulatedAddress) {
    }
}
