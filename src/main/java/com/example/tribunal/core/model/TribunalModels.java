package com.example.tribunal.core.model;

import java.util.List;

public final class TribunalModels {
    private TribunalModels() {}

    public enum InputKind { ELF_OBJECT, ARCHIVE, CORRUPT }
    public enum ModuleKind { EXPLICIT_OBJECT, EXPLICIT_SHARED, ARCHIVE_MEMBER, SHARED_DEPENDENCY }
    public enum CandidateClass { LOCAL, STRONG, WEAK, COMMON, SHARED, UNDEFINED, DISCARDED }
    public enum Outcome { RESOLVED, AMBIGUOUS, UNRESOLVED, LOCAL, BLOCKED_COPY, NOT_EXTRACTED }
    public enum EliminationReason {
        KEPT, NAME_MISMATCH, VERSION_MISMATCH, NON_DEFAULT_VERSION, HIDDEN_NOT_EXPORTED,
        LOCAL_NOT_VISIBLE, DEFINITION_ORDER, DISCARDED_COMDAT, LOWER_PRECEDENCE,
        ARCHIVE_NOT_EXTRACTED, COPY_NOT_OBJECT, COPY_PROTECTED, PLATFORM_POLICY, NOT_YET_LOADED
    }

    public record LoadedInput(String id, String fileName, InputKind kind, byte[] bytes, int order,
                              String parseError, List<String> memberNames) {}
    public record PlatformPolicy(boolean allowCopyRelocation, boolean allowCopyFromProtected,
                                 boolean bindNow, String name) {
        public static PlatformPolicy gnu() { return new PlatformPolicy(true, false, false, "gnu"); }
    }

    public record SymbolKey(String name, String version) {
        public String display() { return version == null || version.isBlank() ? name : name + "@" + version; }
    }

    public record ModuleKey(String inputId, String member, boolean shared) {
        public String display() { return member == null || member.isBlank() ? inputId : inputId + "(" + member + ")"; }
    }

    public record Module(ModuleKey key, String displayName, ModuleKind kind, int loadOrder, String soname,
                        List<String> needed, boolean archiveExtracted, String archiveInputId, int memberOrder) {}

    public record Candidate(String id, ModuleKey module, String symbolName, String versionName, boolean defaultVersion,
                            String binding, String visibility, String kind, CandidateClass candidateClass,
                            long value, long size, int sectionIndex, String sectionName, boolean versionDefinedHere,
                            EliminationReason eliminationReason, String eliminationDetail, int rank) {}

    public record RelocationRef(String id, ModuleKey module, int sectionIndex, String sectionName, long offset,
                                int type, long addend, int symbolIndex, String symbolName, boolean copy, boolean pcrel) {}

    public record SymbolResolution(SymbolKey key, Outcome outcome, Candidate selected, List<Candidate> candidates,
                                   List<RelocationRef> relocations, String reason, List<String> aliasTried) {}

    public record Event(String kind, String title, String detail, String module, String symbol, int step) {}

    public record VersionNode(String id, String label, String kind) {}
    public record VersionEdge(String from, String to, String label) {}
    public record VersionGraph(List<VersionNode> nodes, List<VersionEdge> edges) {}

    public record Scheme(String id, String name, String createdAt, String policyName, int loadOrderSeed,
                         List<LoadedInput> inputs, List<Module> modules, List<SymbolResolution> resolutions,
                         List<Event> events, VersionGraph versionGraph) {}

    public record ReferenceChange(SymbolKey key, String change, RelocationRef relocation,
                                  String fromProvider, String toProvider, String detail) {}
    public record SchemeComparison(List<ReferenceChange> changes, int changedProviders, int addressOnly) {}

    public record StoredScheme(String id, String name, String createdAt, String policyName, int loadOrderSeed, String json) {}
}
