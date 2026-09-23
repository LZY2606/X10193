package com.tribunal.engine;

import com.tribunal.elf.ElfModel.ElfFile;

import java.util.List;

/** Result model produced by {@link Resolver}. */
public final class VerdictModel {
    private VerdictModel() {
    }

    /** Location of one loaded unit (object / shared / archive member / executable). */
    public record UnitId(String archive, String member, String unit) {
        public String label() {
            return archive == null ? unit : archive + "(" + member + ")";
        }

        public static UnitId of(String unit) {
            return new UnitId(null, null, unit);
        }

        public static UnitId member(String archive, String member) {
            return new UnitId(archive, member, archive + "(" + member + ")");
        }
    }

    public enum EliminationReason {
        LOCAL_NOT_EXPORTED("符号是 LOCAL，不参与跨对象裁决"),
        HIDDEN_NOT_EXPORTED("符号为 HIDDEN/INTERNAL，不对其他单元可见"),
        UNDEFINED("自身是未定义引用，无法提供定义"),
        WRONG_VERSION("版本名不匹配（版本索引已还原）"),
        COMDAT_DISCARDED("所属 COMDAT section group 在去重中被丢弃"),
        NOT_DEFAULT_UNVERSIONED("非默认版本不能满足无版本引用"),
        PROTECTED_BIND_LOCAL("受保护定义只能满足本库内部引用"),
        LOWER_BINDING("绑定强度低于中选候选"),
        LOAD_ORDER("加载顺序靠后，先加载的同等候选胜出"),
        ARCHIVE_NOT_NEEDED("archive member 未被抽取（惰性抽取）"),
        VERSYM_HIDDEN("versym 隐藏位标记为非默认导出"),
        DUPLICATE_AMBIGUITY("同等强度候选并列，全部保留为歧义");

        private final String text;

        EliminationReason(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    /** A single provider candidate for one reference bucket. */
    public record Candidate(UnitId unit, String symbolName, String versionName,
                            boolean defaultVersion, int binding, int symbolType,
                            int visibility, long value, long size, boolean common,
                            boolean fromArchive, boolean extracted,
                            EliminationReason eliminated, String detail) {
        public String describeVersion() {
            if (versionName == null) {
                return "（无版本）";
            }
            return versionName + (defaultVersion ? " [默认]" : " [非默认/隐藏]");
        }

        public boolean alive() {
            return eliminated == null;
        }
    }

    /** A relocation that references the bucket's symbol. */
    public record RelocRef(UnitId unit, int sectionIndex, String sectionName, long offset,
                           long addend, String typeName, String bind, String note) {
    }

    public enum VerdictStatus {
        RESOLVED,
        RESOLVED_WEAK_NULL,
        UNRESOLVED,
        UNRESOLVED_VERSION,
        AMBIGUOUS,
        COPY,
        COPY_BLOCKED_PROTECTED
    }

    /**
     * One adjudication: per symbol name (and requested version) the candidate
     * pool, eliminations, final winner(s) and affected relocations.
     */
    public record Conclusion(String symbolName, String requestedVersion,
                             String requestedFrom, VerdictStatus status,
                             List<Candidate> candidates, List<UnitId> winners,
                             List<RelocRef> relocations, String explanation) {
        public boolean ambiguous() {
            return status == VerdictStatus.AMBIGUOUS;
        }
    }

    /** COMDAT section group deduplication decision. */
    public record ComdatDecision(String signature, UnitId keptUnit, int keptGroupSection,
                                List<DiscardedGroup> discarded) {
        public record DiscardedGroup(UnitId unit, int groupSection) {
        }
    }

    /** Archive lazy extraction record. */
    public record Extraction(String archive, String member, UnitId unit, boolean extracted,
                             String reason, int stepIndex) {
    }

    /** Player step for archive extraction / symbol table animation. */
    public record StepEvent(int index, String kind, String title, String detail,
                            String unit, List<String> symbolTableSnapshot,
                            List<String> activeUnits) {
    }

    /** Parse failure for one input, isolated to that object/member. */
    public record InputError(UnitId unit, String stage, String message) {
    }

    /** Version graph node / edge for the UI. */
    public record VersionNode(String id, String label, String kind) {
    }

    public record VersionEdge(String from, String to, String label) {
    }

    public record LoadedUnit(UnitId id, InputKind kind, String elfType, String machine,
                             String soname, List<String> needed, boolean active,
                             String inactivityReason, ElfFile elf) {
    }

    public record ResolutionResult(List<LoadedUnit> units, List<Conclusion> conclusions,
                                   List<ComdatDecision> comdatDecisions,
                                   List<Extraction> extractions, List<StepEvent> steps,
                                   List<InputError> errors,
                                   List<VersionNode> versionNodes,
                                   List<VersionEdge> versionEdges,
                                   ResolvePolicy policy, List<String> loadOrder) {
        public List<Conclusion> byName(String name) {
            return conclusions.stream().filter(c -> c.symbolName().equals(name)).toList();
        }
    }
}
