package local.symbolcourt.engine;

import java.util.List;
import local.symbolcourt.elf.ElfModels.BinaryKind;
import local.symbolcourt.elf.ElfModels.ElfSymbol;
import local.symbolcourt.elf.ElfModels.ParsedBinary;
import local.symbolcourt.elf.ElfModels.Relocation;
import local.symbolcourt.elf.ElfModels.VersionEdge;

public final class EngineModels {
    private EngineModels() {}

    public enum InputKind { OBJECT, SHARED, ARCHIVE, EXECUTABLE }
    public enum TiePolicy { STRICT_AMBIGUOUS, FIRST_IN_LOAD_ORDER }

    public record InputRef(long binaryId, String name, String sha256, InputKind kind,
                           int loadOrder, boolean asNeeded) {}

    public record PlatformPolicy(String name, int machine, TiePolicy tiePolicy,
                                 boolean copyRelocations, boolean allowInterposition) {
        public static PlatformPolicy x8664Linux(TiePolicy tiePolicy) {
            return new PlatformPolicy("x86_64-linux", 62, tiePolicy, true, true);
        }
        public static PlatformPolicy aarch64Linux(TiePolicy tiePolicy) {
            return new PlatformPolicy("aarch64-linux", 183, tiePolicy, true, true);
        }
    }

    public record ActiveUnit(String unitId, String displayName, String fileName,
                             String sha256, BinaryKind kind, int loadOrder,
                             int archiveOrder, String archiveName, boolean extracted,
                             ParsedBinary binary) {}

    public record Candidate(String candidateId, String unitId, String displayName,
                            String symbolName, int symbolIndex, String version,
                            int rank, int loadOrder, int archiveOrder,
                            String binding, String visibility, String type,
                            boolean common, boolean defined, String section,
                            String comdatSignature, long address, long size,
                            List<String> eliminationReasons) {}

    public record Reference(String referenceId, String unitId, String fileName,
                            Relocation relocation, ElfSymbol requestedSymbol) {}

    public record Decision(String referenceId, String symbol, String requestedVersion,
                           boolean nonDefaultVersion, String selectedCandidateId,
                           List<Candidate> candidates, List<String> reasons,
                           String status, String detail) {}

    public record ExtractionEvent(int round, String archive, String member,
                                  List<String> symbols, String reason) {}

    public record ObjectDependency(String unitId, String displayName,
                                   String dependency, String kind) {}

    public record AdjudicationResult(List<InputRef> inputs, PlatformPolicy policy,
                                     List<ActiveUnit> activeUnits,
                                     List<ActiveUnit> isolatedUnits,
                                     List<ObjectDependency> dependencies,
                                     List<VersionEdge> versionGraph,
                                     List<Decision> decisions,
                                     List<Relocation> relocations,
                                     List<ExtractionEvent> extractionEvents,
                                     List<String> diagnostics) {}

    public record ReferenceChange(String reference, String fromProvider, String toProvider,
                                  String kind, String beforeStatus, String afterStatus) {}

    public record SchemeComparison(List<ReferenceChange> changes,
                                   List<String> providerSwitches,
                                   List<String> addressOnlyChanges,
                                   List<String> unchanged) {}
}
