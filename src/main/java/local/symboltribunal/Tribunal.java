package local.symboltribunal;

import java.util.List;

public final class Tribunal {
    private Tribunal() {
    }

    public enum ElfClass {
        ELF32, ELF64
    }

    public enum ElfData {
        LSB, MSB
    }

    public enum ObjectKind {
        REL, EXEC, DYN, CORE, UNKNOWN
    }

    public enum Binding {
        LOCAL, GLOBAL, WEAK, GNU_UNIQUE, OS, PROC;

        public static Binding from(int value) {
            return switch (value) {
                case 0 -> LOCAL;
                case 1 -> GLOBAL;
                case 2 -> WEAK;
                case 10 -> GNU_UNIQUE;
                case 13, 14 -> PROC;
                default -> value >= 10 && value <= 12 ? OS : GLOBAL;
            };
        }
    }

    public enum Visibility {
        DEFAULT, INTERNAL, HIDDEN, PROTECTED;

        public static Visibility from(int value) {
            return switch (value & 3) {
                case 1 -> INTERNAL;
                case 2 -> HIDDEN;
                case 3 -> PROTECTED;
                default -> DEFAULT;
            };
        }
    }

    public enum SymbolType {
        NOTYPE, OBJECT, FUNC, SECTION, FILE, COMMON, TLS, IFUNC, OTHER;

        public static SymbolType from(int value) {
            return switch (value) {
                case 0 -> NOTYPE;
                case 1 -> OBJECT;
                case 2 -> FUNC;
                case 3 -> SECTION;
                case 4 -> FILE;
                case 5 -> COMMON;
                case 6 -> TLS;
                case 10 -> IFUNC;
                default -> OTHER;
            };
        }
    }

    public record VersionRef(
            int rawIndex,
            String name,
            boolean defaultVersion,
            boolean definition,
            boolean requirement
    ) {
        public static VersionRef none() {
            return new VersionRef(0, null, true, false, false);
        }

        public String key() {
            return name == null ? "<unversioned>" : name;
        }
    }

    public record SectionGroup(
            String signature,
            boolean comdat,
            List<Integer> sections
    ) {
    }

    public record ElfSymbol(
            int index,
            String name,
            Binding binding,
            Visibility visibility,
            SymbolType type,
            int sectionIndex,
            long value,
            long size,
            VersionRef version,
            boolean dynamicTable
    ) {
        public boolean defined() {
            return name != null && !name.isEmpty() && sectionIndex != 0 && sectionIndex != 0xfff1;
        }

        public boolean common() {
            return sectionIndex == 0xfff2;
        }

        public boolean weak() {
            return binding == Binding.WEAK;
        }
    }

    public record Relocation(
            String sectionName,
            int targetSectionIndex,
            long offset,
            int type,
            String typeName,
            int symbolIndex,
            String symbolName,
            long addend,
            boolean rela,
            boolean copy
    ) {
    }

    public record ParsedObject(
            String objectId,
            String displayName,
            String path,
            String archiveMember,
            String contentSha,
            ObjectKind kind,
            ElfClass elfClass,
            ElfData elfData,
            int machine,
            int type,
            String soname,
            List<String> needed,
            List<ElfSymbol> symbols,
            List<Relocation> relocations,
            List<SectionGroup> groups,
            List<String> issues,
            boolean corrupt
    ) {
        public boolean staticRelocatable() {
            return kind == ObjectKind.REL;
        }

        public boolean dynamicComponent() {
            return kind == ObjectKind.DYN || kind == ObjectKind.EXEC;
        }
    }

    public record ArchiveMember(
            String name,
            String contentSha,
            byte[] data,
            ParsedObject object
    ) {
    }

    public record LoadedBinary(
            String path,
            String contentSha,
            byte[] data,
            boolean archive,
            List<ArchiveMember> members,
            ParsedObject directObject,
            List<String> issues
    ) {
    }

    public record LoadEntry(
            String path,
            String contentSha,
            byte[] data,
            int explicitOrder
    ) {
    }

    public enum TiePolicy {
        FIRST_LOAD_ORDER, PRESERVE_AMBIGUITY
    }

    public record ResolutionPolicy(
            TiePolicy tiePolicy,
            int maxArchivePasses,
            boolean permitCopyRelocation
    ) {
        public static ResolutionPolicy gnu() {
            return new ResolutionPolicy(TiePolicy.FIRST_LOAD_ORDER, 32, true);
        }

        public static ResolutionPolicy strict() {
            return new ResolutionPolicy(TiePolicy.PRESERVE_AMBIGUITY, 32, true);
        }
    }

    public record SymbolReference(
            String id,
            String stableKey,
            String objectId,
            String objectName,
            String objectSha,
            String archiveMember,
            String symbolName,
            VersionRef requiredVersion,
            boolean weak,
            long offset,
            int relocationType,
            String relocationTypeName,
            String relocationSection,
            long addend,
            boolean copy,
            String scope
    ) {
    }

    public record Candidate(
            String providerId,
            String objectId,
            String objectName,
            String objectSha,
            String archiveMember,
            int symbolIndex,
            String symbolName,
            Binding binding,
            Visibility visibility,
            SymbolType symbolType,
            VersionRef version,
            int sectionIndex,
            String sectionGroup,
            long value,
            long size,
            long alignment,
            boolean common,
            boolean dynamic,
            boolean archiveProvider,
            boolean extracted,
            int loadOrder,
            int rank,
            boolean selected,
            List<String> eliminatedReasons
    ) {
    }

    public enum DecisionStatus {
        RESOLVED, LOCAL_RESOLVED, COPY, AMBIGUOUS, DUPLICATE, VERSION_UNSATISFIED, UNRESOLVED, WEAK_UNRESOLVED, COPY_POLICY_REJECTED
    }

    public record Decision(
            String id,
            String stableKey,
            String scope,
            String symbolName,
            VersionRef requiredVersion,
            DecisionStatus status,
            String reason,
            Candidate selected,
            Candidate copyDonor,
            Long modeledAddress,
            List<Candidate> candidates,
            List<SymbolReference> affectedRelocations
    ) {
    }

    public record DependencyNode(
            String id,
            String label,
            String kind,
            boolean present,
            boolean corrupt
    ) {
    }

    public record DependencyEdge(
            String from,
            String to,
            String label
    ) {
    }

    public record VersionNode(
            String id,
            String objectId,
            String version,
            String kind,
            boolean defaultVersion,
            boolean satisfied
    ) {
    }

    public record VersionEdge(
            String from,
            String to,
            String label
    ) {
    }

    public record TimelineEvent(
            int step,
            String kind,
            String title,
            String detail,
            String symbol,
            String objectId,
            String before,
            String after
    ) {
    }

    public record ResolutionReport(
            String scenarioName,
            int schemeVersion,
            ResolutionPolicy policy,
            List<ParsedObject> objects,
            List<DependencyNode> dependencyNodes,
            List<DependencyEdge> dependencyEdges,
            List<VersionNode> versionNodes,
            List<VersionEdge> versionEdges,
            List<Decision> decisions,
            List<TimelineEvent> timeline,
            List<String> issues
    ) {
    }

    public record ReferenceDifference(
            String stableKey,
            String symbolName,
            String kind,
            String fromProvider,
            String toProvider,
            Long fromAddress,
            Long toAddress,
            String detail
    ) {
    }

    public record SchemeComparison(
            int fromSchemeVersion,
            int toSchemeVersion,
            List<ReferenceDifference> differences,
            int unchangedReferences
    ) {
    }
}
