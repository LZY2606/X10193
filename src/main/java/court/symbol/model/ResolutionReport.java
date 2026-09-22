package court.symbol.model;

import java.util.List;

/** Complete, immutable adjudication result for one plan version. */
public record ResolutionReport(String planId,
                               int version,
                               PlatformPolicy policy,
                               String targetPlatform,
                               List<DependencyNode> dependencyGraph,
                               List<VersionNode> versionGraph,
                               List<Decision> decisions,
                               List<StepEvent> steps,
                               List<String> parseErrors,
                               long createdAt) {

    public List<Decision> unresolved() {
        return decisions.stream()
                .filter(d -> Decision.UNRESOLVED.equals(d.status()))
                .toList();
    }

    public List<Decision> ambiguous() {
        return decisions.stream()
                .filter(d -> Decision.AMBIGUOUS.equals(d.status()))
                .toList();
    }
}
