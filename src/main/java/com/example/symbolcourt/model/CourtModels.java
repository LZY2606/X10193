package com.example.symbolcourt.model;

import java.util.List;

public final class CourtModels {
    private CourtModels() {
    }

    public enum InputKind { OBJECT, ARCHIVE, UNKNOWN, CORRUPT }

    public record InputBinary(String name, byte[] data, String sha256) {
    }

    public record AnalysisRequest(String name, List<InputBinary> files, PlatformPolicy policy) {
    }

    public record PlatformPolicy(String platform, boolean strictDuplicates, boolean resolveDynamicByLoadOrder) {
        public static PlatformPolicy linux() {
            return new PlatformPolicy("X86_64", false, true);
        }

        public static PlatformPolicy from(String platform, String policy) {
            String normalizedPlatform = platform == null || platform.isBlank() ? "X86_64" : platform.trim().toUpperCase();
            boolean strict = "STRICT".equalsIgnoreCase(policy);
            return new PlatformPolicy(normalizedPlatform, strict, !strict);
        }
    }

    public static class SectionInfo {
        public int index;
        public int nameOffset;
        public String name = "";
        public int type;
        public long address;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long alignment;
        public boolean discardedGroup;
    }

    public static class SectionGroup {
        public int groupSectionIndex;
        public String signature = "";
        public List<Integer> memberSections = List.of();
    }

    public static class SymbolDef {
        public int index;
        public String name = "";
        public long value;
        public long size;
        public int info;
        public int other;
        public int sectionIndex;
        public int bind;
        public int type;
        public int visibility;
        public boolean defined;
        public boolean common;
        public boolean dynamic;
        public int rawVersionIndex = 1;
        public int versionIndex = 1;
        public String versionName = "(*global*)";
        public boolean nonDefaultVersion;
        public String sectionName = "";
        public String groupSignature = "";
    }

    public static class Relocation {
        public String id;
        public int sectionIndex;
        public String sectionName = "";
        public long offset;
        public long addend;
        public int type;
        public int symbolIndex;
        public String symbolName = "";
        public boolean copy;
    }

    public static class VersionDefinition {
        public int index;
        public int flags;
        public List<String> names = List.of();
    }

    public static class VersionRequirement {
        public int index;
        public String fileName = "";
        public String versionName = "";
    }

    public static class LoadedObject {
        public String id;
        public String displayName;
        public String fileName;
        public String archiveName;
        public String memberName;
        public int loadOrder;
        public long memberOffset;
        public boolean archiveMember;
        public boolean extracted = true;
        public boolean active = true;
        public boolean shared;
        public boolean relocatable;
        public int elfType;
        public int machine;
        public boolean elf64 = true;
        public boolean littleEndian = true;
        public String contentSha;
        public InputKind kind = InputKind.OBJECT;
        public String error;
        public List<SectionInfo> sections = List.of();
        public List<SectionGroup> groups = List.of();
        public List<SymbolDef> symbols = List.of();
        public List<Relocation> relocations = List.of();
        public List<VersionDefinition> versionDefinitions = List.of();
        public List<VersionRequirement> versionRequirements = List.of();
        public List<String> needed = List.of();
    }

    public record ArchiveIndexEntry(String symbol, long offset, String memberName) {
    }

    public static class ArchiveMember {
        public String name;
        public long headerOffset;
        public long dataOffset;
        public long size;
    }

    public static class ParsedArchive {
        public String id;
        public String fileName;
        public int loadOrder;
        public String error;
        public List<ArchiveIndexEntry> index = List.of();
        public List<ArchiveMember> members = List.of();
    }

    public record InputNode(String id, String name, int position, InputKind kind, String error) {
    }

    public record DependencyEdge(String from, String to, String type, String detail) {
    }

    public record VersionNode(String id, String label, String kind) {
    }

    public record VersionEdge(String from, String to, String label) {
    }

    public record CandidateView(
            String objectId,
            String objectName,
            int symbolIndex,
            String binding,
            String symbolType,
            String visibility,
            String version,
            boolean nonDefaultVersion,
            boolean defined,
            boolean common,
            boolean active,
            boolean extracted,
            long loadOrder,
            long memberOffset,
            long address,
            long size,
            String section,
            String groupSignature,
            int score,
            String status,
            String reason
    ) {
    }

    public record RelocationView(
            String id,
            String objectId,
            String objectName,
            String section,
            long offset,
            long addend,
            int type,
            String symbol,
            String version,
            boolean copy
    ) {
    }

    public record ResolutionView(
            String key,
            String name,
            String version,
            boolean nonDefaultVersion,
            String context,
            String state,
            String selectedObjectId,
            String selectedObjectName,
            String selectedProviderKey,
            Integer selectedSymbolIndex,
            Long selectedAddress,
            boolean ambiguous,
            String summary,
            List<CandidateView> candidates,
            List<RelocationView> relocations
    ) {
    }

    public record PlaybackEvent(int step, String phase, String title, String detail, String objectId) {
    }

    public record CourtReport(
            String id,
            String name,
            String platform,
            String policy,
            long createdAt,
            String requestFingerprint,
            List<InputNode> inputs,
            List<LoadedObject> objects,
            List<ParsedArchive> archives,
            List<DependencyEdge> dependencies,
            List<VersionNode> versionNodes,
            List<VersionEdge> versionEdges,
            List<ResolutionView> resolutions,
            List<RelocationView> allRelocations,
            List<PlaybackEvent> events,
            List<String> errors
    ) {
    }

    public record RelocationChange(
            String relocationId,
            String symbol,
            String oldProvider,
            String newProvider,
            String oldState,
            String newState,
            boolean providerChanged,
            boolean addressOnlyChanged
    ) {
    }

    public record ComparisonReport(
            String oldSchemeId,
            String newSchemeId,
            List<RelocationChange> changes,
            List<String> providerSwitches,
            List<String> addressOnlyChanges,
            List<String> unchanged
    ) {
    }
}
