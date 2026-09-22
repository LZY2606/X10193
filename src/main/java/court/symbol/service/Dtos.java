package court.symbol.service;

import java.util.List;

/** Request/response DTOs for the court API. */
public final class Dtos {

    private Dtos() {
    }

    public record EntrySpec(String sha256, int order, String label) {
    }

    public record CreatePlanRequest(String name,
                                    String targetPlatform,
                                    String linkModel,
                                    String tiePolicy,
                                    boolean allowInterposition,
                                    List<EntrySpec> entries) {
    }

    public record PlanSummary(String id, String name, String targetPlatform,
                              int latestVersion, long createdAt) {
    }

    public record VersionSummary(String planId, int version, String linkModel,
                                 String tiePolicy, boolean allowInterposition,
                                 long createdAt, int decisions, int unresolved,
                                 int ambiguous) {
    }
}
