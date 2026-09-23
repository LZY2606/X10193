package com.example.symbolcourt.linker;

import com.example.symbolcourt.elf.ElfModels.ObjectUnit;
import com.example.symbolcourt.elf.ElfModels.ElfRelocation;

import java.util.List;
import java.util.Map;

public final class LinkerModels {
    private LinkerModels() {}

    public record PolicyProfile(
            String name,
            String targetPlatform,
            Integer targetMachine,
            boolean allowCommonMerge,
            boolean allowCopyRelocation,
            boolean extractArchiveForWeak,
            boolean firstLoadedTie,
            boolean allowProtectedDataOverride
    ) {
        public static PolicyProfile strictElf() {
            return new PolicyProfile("strict-elf", "ELF strict", null, true, false, false, false, false);
        }
        public static PolicyProfile gnuLinux() {
            return new PolicyProfile("gnu-linux", "GNU/Linux", null, true, true, false, true, false);
        }
    }

    public record LinkRequest(List<String> paths, List<String> objectNames, List<byte[]> embeddedObjects,
                              PolicyProfile policy, String label) {}

    public enum DecisionState { RESOLVED, AMBIGUOUS, UNRESOLVED, WEAK_UNRESOLVED, UNSATISFIED_VERSION, MULTIPLE_DEFINITION, COPY_RELOCATION_REJECTED }
    public enum RejectReason {
        NONE, NOT_EXTRACTED, LOCAL_SCOPE, VISIBILITY, VERSION_MISMATCH, PLATFORM_MISMATCH,
        STRONG_CONFLICT, STRONGER_PROVIDER, COMMON_MERGE_LOSER, WEAK_ORDER_LOSER,
        COMDAT_DEDUP, COPY_PROTECTED, COMMON_DISABLED, ARCHIVE_NOT_NEEDED, OUT_OF_SCOPE
    }

    public record SymbolRequest(
            String id,
            String unitId,
            String unitName,
            String name,
            String requestedVersion,
            boolean weak,
            boolean copyRelocation,
            String relocationKey,
            int order,
            int visibility,
            List<AffectedRelocation> relocations
    ) {}

    public record CandidateView(
            String candidateId,
            String unitId,
            String unitName,
            String archiveName,
            int order,
            String binding,
            String visibility,
            String section,
            String version,
            boolean nonDefaultVersion,
            long size,
            long value,
            long simulatedAddress,
            String groupSignature,
            boolean common,
            boolean archiveMember,
            boolean extracted,
            boolean eligible,
            RejectReason rejectReason,
            String reasonDetail,
            int rank
    ) {}

    public record AffectedRelocation(String unitId, String unitName, long offset, String section,
                                     int type, Long addend, boolean copy, String requestedVersion) {}

    public record SymbolDecision(
            String name,
            String requestedVersion,
            DecisionState state,
            String rationale,
            List<CandidateView> candidates,
            List<String> selectedCandidateIds,
            List<AffectedRelocation> relocations,
            boolean copyRelocation,
            String resolvedVersion
    ) {}

    public record TimelineEvent(int step, String type, String title, String detail,
                                String unitName, Map<String, Object> snapshot) {}

    public record DependencyEdge(String from, String to, String kind, boolean satisfied) {}

    public record ResolutionPlan(
            String id,
            String label,
            String inputHash,
            PolicyProfile policy,
            List<ObjectUnit> objects,
            List<DependencyEdge> dependencies,
            List<SymbolDecision> decisions,
            List<TimelineEvent> timeline,
            List<String> warnings,
            String createdAt
    ) {}

    public record ReferenceDifference(String unitName, long offset, String symbol, String requestedVersion,
                                      String fromProvider, String toProvider, String kind, String detail) {}

    public record PlanComparison(boolean equivalent, List<ReferenceDifference> providerChanges,
                                 List<ReferenceDifference> addressOnlyChanges, List<String> removedReferences) {}
}
