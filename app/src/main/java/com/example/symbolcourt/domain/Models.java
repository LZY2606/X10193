package com.example.symbolcourt.domain;

import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {
    }

    public record InputBinary(String id, String displayName, String path, byte[] content, Kind kind) {
        public enum Kind { ELF, ARCHIVE, UNKNOWN }
    }

    public record Section(int index, String name, long nameOffset, int type, long address, long offset,
                          long size, long link, long info, long flags, long entrySize) {
    }

    public record SectionGroup(String signature, boolean comdat, List<Integer> memberSections) {
    }

    public record VersionNode(String id, String objectId, int index, String name, Kind kind,
                              boolean defaultVersion, boolean weak, List<String> aliases,
                              String parentId, String dependencyName) {
        public enum Kind { DEFINITION, REQUIREMENT, ALIAS }
    }

    public record VersionEdge(String sourceId, String targetId, String label) {
    }

    public record ElfSymbol(String objectId, String objectName, String archiveName, String memberName,
                            int symbolIndex, String name, int versionIndex, String versionName,
                            List<String> versionAliases, boolean defaultVersion, boolean hiddenVersion,
                            String binding, String symbolType, String visibility, int sectionIndex,
                            String sectionName, long value, long size, boolean dynamic,
                            boolean defined, boolean common, boolean exported) {
        public String providerId() {
            return objectId + "#" + (memberName == null ? "" : memberName + "#") + sectionIndex + ":" + symbolIndex;
        }
    }

    public record SymbolReference(String id, String objectId, String objectName, String archiveName,
                                  String memberName, String symbolName, String requestedVersion,
                                  String binding, boolean strong, String relocationType, int typeCode,
                                  int symbolIndex, String sectionName, long offset, long addend,
                                  boolean copy, boolean dynamic) {
    }

    public record ParsedObject(String id, String displayName, String archiveName, String memberName,
                               String path, int bits, String endianness, String machine, int machineCode,
                               String elfType, List<Section> sections, List<ElfSymbol> symbols,
                               List<SymbolReference> references, List<SectionGroup> groups,
                               List<String> neededDependencies, List<VersionNode> versionNodes,
                               List<VersionEdge> versionEdges, String parseError, boolean archiveMember,
                               boolean staticObject) {
        public boolean healthy() {
            return parseError == null;
        }
    }

    public record ArchiveMember(String archiveId, String archiveName, String memberName, long offset,
                                ParsedObject object) {
    }

    public record ArchiveFile(String id, String displayName, String path, List<ArchiveMember> members,
                              String parseError) {
    }

    public record LoadOrderEntry(String binaryId, String displayName, int position, String role,
                                 String archiveMember) {
    }

    public record Candidate(String providerId, String objectId, String objectName, String archiveName,
                            String memberName, String symbolName, String versionName,
                            List<String> versionAliases, boolean defaultVersion, String binding,
                            String visibility, String symbolType, String sectionName, int sectionIndex,
                            long value, long size, int loadOrder, boolean common, boolean accepted,
                            String eliminationReason, Map<String, Object> evidence) {
    }

    public record AffectedRelocation(String referenceId, String objectName, String memberName,
                                     String sectionName, long offset, long addend, String type,
                                     String symbolName, String requestedVersion, boolean copy) {
    }

    public record ResolutionDecision(String key, String symbolName, String requestedVersion,
                                     String kind, String status, List<Candidate> candidates,
                                     List<String> chosenProviderIds, String reason,
                                     List<AffectedRelocation> affectedRelocations) {
    }

    public record PlayEvent(int sequence, String type, String title, String detail,
                            Map<String, Object> state) {
    }

    public record DependencyEdge(String fromObjectId, String fromName, String toObjectId,
                                 String toName, String requestedName, boolean satisfied) {
    }

    public record ResolutionResult(String planId, String policy, boolean rescanArchives,
                                   List<ParsedObject> objects, List<ArchiveMember> extractedMembers,
                                   List<ResolutionDecision> decisions, List<PlayEvent> events,
                                   List<DependencyEdge> dependencyEdges, List<VersionNode> versionNodes,
                                   List<VersionEdge> versionEdges, List<String> notices) {
    }

    public record Plan(String id, String name, String policy, String createdAt,
                       List<LoadOrderEntry> loadOrder, ResolutionResult result) {
    }

    public record ReferenceBinding(String referenceId, String objectName, String memberName,
                                   String symbolName, String requestedVersion, String status,
                                   String providerId, String providerName, long address,
                                   String relocationType, boolean copy) {
    }

    public record BindingDifference(String change, ReferenceBinding oldBinding,
                                    ReferenceBinding newBinding, String explanation) {
    }

    public record PlanComparison(boolean sameBindings, List<BindingDifference> differences,
                                 List<String> addressOnlyChanges) {
    }
}
