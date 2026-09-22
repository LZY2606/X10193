package court.symbol.model;

import java.util.List;

public record ResolutionDecision(String id, String name, String requestedVersion, String status,
                                 List<SymbolProvider> chosen, List<CandidateRejection> rejected,
                                 List<SymbolReference> references, String rationale) {
}
