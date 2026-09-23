package com.example.symbolcourt.domain;

import java.util.List;
import java.util.Map;

public final class Domain {
    private Domain() {
    }

    public enum Binding { LOCAL, GLOBAL, WEAK, OTHER }
    public enum SymbolType { NOTYPE, OBJECT, FUNC, SECTION, FILE, OTHER }
    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED, OTHER }

    public record Section(int index, String name, long type, long flags, long address, long offset, long size,
                          int link, int info, long alignment, long entrySize) {
    }

    public record SymbolVersion(int index, String name, boolean nonDefault, long hash) {
        public static final SymbolVersion LOCAL = new SymbolVersion(0, "LOCAL", false, 0);
        public static final SymbolVersion GLOBAL = new SymbolVersion(1, "GLOBAL", false, 0);
    }

    public record ElfSymbol(int index, String name, long value, long size, Binding binding, SymbolType type,
                            Visibility visibility, int sectionIndex, boolean dynamic, SymbolVersion version) {
        public boolean isDefined() {
            return sectionIndex != 0;
        }

        public boolean isCommon() {
            return sectionIndex == 0xfff5 || sectionIndex == 0xfff2;
        }

        public boolean isSpecialIndex() {
            return sectionIndex >= 0xff00;
        }
    }

    public record Relocation(int sectionIndex, long offset, int type, int symbolIndex, long addend, boolean relative) {
    }

    public record ComdatGroup(int sectionIndex, String signature, int flags, List<Integer> members) {
    }

    public record VersionNode(int index, String name, long hash, boolean definition, boolean nonDefault,
                              List<String> parents, List<String> aliases) {
    }

    public record ElfObject(String key, String displayName, String archivePath, String memberName,
                            String inputPath, int elfClass, boolean littleEndian, int machine, int type,
                            String soname, List<String> needed, List<Section> sections, List<ElfSymbol> symbols,
                            List<Relocation> relocations, List<ComdatGroup> groups, List<VersionNode> versions,
                            boolean executable) implements InputBinary {
        public String objectType() {
            return switch (type) {
                case 1 -> "REL";
                case 2 -> "EXEC";
                case 3 -> "DYN";
                case 4 -> "CORE";
                default -> "UNKNOWN";
            };
        }
    }

    public record ArchiveMember(String name, byte[] content) {
    }

    public record Archive(String key, String displayName, String path, List<ArchiveMember> members) implements InputBinary {
    }

    public interface InputBinary {
        String key();
        String displayName();
        String path();
    }

    public record InputEntry(String key, String path, String displayName, int order, boolean archive) {
    }

    public record LinkPolicy(String target, boolean singlePassArchive, boolean forbidDuplicateStrong,
                             boolean preferFirstSharedDefinition, boolean enableCopyRelocation) {
        public static LinkPolicy gnuLike() {
            return new LinkPolicy("x86_64-linux-gnu", false, false, false, true);
        }
    }

    public record Candidate(String id, String objectKey, String objectLabel, String memberName, int symbolIndex,
                            String name, String version, boolean nonDefaultVersion, Binding binding,
                            SymbolType type, Visibility visibility, long value, long size, int sectionIndex,
                            boolean dynamic, boolean shared, boolean common, boolean executableDefinition,
                            String groupSignature) {
        public String stableIdentity() {
            return objectKey + "#" + (memberName == null ? "" : memberName + "!") + symbolIndex;
        }
    }

    public record AffectedRelocation(String objectKey, String objectLabel, int sectionIndex, long offset,
                                     int type, long addend) {
    }

    public record SymbolReference(String key, String objectKey, String objectLabel, String sectionName,
                                  int sectionIndex, long offset, String name, String requestedVersion,
                                  Binding binding, SymbolType type, boolean copyRelocation,
                                  List<AffectedRelocation> relocations) {
    }

    public record CandidateElimination(String candidateId, String objectLabel, String reason) {
    }

    public enum DecisionStatus { RESOLVED, UNRESOLVED, AMBIGUOUS, DUPLICATE_STRONG, UNMET_VERSION, COPY_RESOLVED }

    public record Decision(String referenceKey, String name, String requestedVersion, DecisionStatus status,
                           List<String> selectedCandidateIds, List<CandidateElimination> eliminations,
                           List<AffectedRelocation> affectedRelocations, String reason, boolean copyRelocation) {
    }

    public record GroupDecision(String signature, String winnerObjectKey, String winnerLabel,
                                List<String> candidateObjectKeys, String reason) {
    }

    public record ExtractionEvent(int sequence, String archiveLabel, String member, String reason,
                                  List<String> resolvedReferences) {
    }

    public record SymbolTableEvent(String kind, String objectLabel, String symbol, String version, String detail) {
    }

    public record DependencyEdge(String fromObjectKey, String fromLabel, String toName, String resolvedObjectKey,
                                 boolean resolved, String reason) {
    }

    public record ResolutionResult(List<ElfObject> objects, List<DependencyEdge> dependencies,
                                   List<Candidate> candidates, List<SymbolReference> references,
                                   List<Decision> decisions, List<GroupDecision> groupDecisions,
                                   List<ExtractionEvent> extractionEvents, List<SymbolTableEvent> symbolEvents,
                                   List<String> warnings, Map<String, Object> policy) {
    }

    public record Scheme(String id, String label, String target, String policyJson, String inputJson,
                         String resultJson, String createdAt) {
    }

    public record SchemeSummary(String id, String label, String target, String createdAt) {
    }

    public record ComparisonGroup(List<String> providerChanged, List<String> addressOnlyChanged,
                                  List<String> newlyResolved, List<String> becameUnresolved,
                                  List<String> statusChanged, List<String> unchanged) {
    }
}
