package com.tribunal.engine;

import com.tribunal.elf.Elf;
import com.tribunal.elf.ElfModel.ElfFile;
import com.tribunal.elf.ElfModel.Relocation;
import com.tribunal.elf.ElfModel.SectionGroup;
import com.tribunal.elf.ElfModel.Symbol;
import com.tribunal.engine.VerdictModel.Candidate;
import com.tribunal.engine.VerdictModel.ComdatDecision;
import com.tribunal.engine.VerdictModel.Conclusion;
import com.tribunal.engine.VerdictModel.EliminationReason;
import com.tribunal.engine.VerdictModel.Extraction;
import com.tribunal.engine.VerdictModel.InputError;
import com.tribunal.engine.VerdictModel.LoadedUnit;
import com.tribunal.engine.VerdictModel.RelocRef;
import com.tribunal.engine.VerdictModel.StepEvent;
import com.tribunal.engine.VerdictModel.VerdictStatus;
import com.tribunal.engine.VerdictModel.UnitId;
import com.tribunal.engine.VerdictModel.VersionEdge;
import com.tribunal.engine.VerdictModel.VersionNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core symbol adjudication engine. No external linker is invoked: every
 * decision is derived purely from parsed ELF data and the explicit, ordered
 * input list plus the selected {@link ResolvePolicy}.
 */
public final class Resolver {

    private final List<ParsedUnit> allUnits = new ArrayList<>();
    private final List<InputLoader.ArchiveUnits> archives = new ArrayList<>();
    private final List<InputError> errors = new ArrayList<>();
    private final List<StepEvent> steps = new ArrayList<>();
    private final List<Extraction> extractions = new ArrayList<>();
    private final List<ComdatDecision> comdatDecisions = new ArrayList<>();
    private final Set<String> activatedKeys = new LinkedHashSet<>();
    private ResolvePolicy policy;

    public VerdictModel.ResolutionResult resolve(List<InputEntry> entries,
                                                 ResolvePolicy requestedPolicy) {
        this.policy = requestedPolicy == null ? ResolvePolicy.gnu() : requestedPolicy;
        reset();
        InputLoader.Loaded loaded = new InputLoader().load(entries);
        errors.addAll(loaded.errors);
        allUnits.addAll(loaded.explicitUnits);
        archives.addAll(loaded.archives);
        for (InputLoader.ArchiveUnits archive : archives) {
            allUnits.addAll(archive.members);
        }

        List<String> loadOrder = entries.stream().map(InputEntry::displayName).toList();
        step(0, "load", "导入输入", "按用户给出的显式顺序导入 " + entries.size()
                + " 个输入；解析失败仅隔离自身", null);

        dedupeComdat();
        activateExplicitUnits();
        lazyExtract();

        RefBucket.Registry registry = new RefBucket.Registry();
        collectReferences(registry);
        List<Conclusion> conclusions = adjudicate(registry);
        List<Conclusion> copyConclusions = handleCopyRelocations(conclusions, registry);

        return new VerdictModel.ResolutionResult(describeUnits(), copyConclusions,
                List.copyOf(comdatDecisions), List.copyOf(extractions), List.copyOf(steps),
                List.copyOf(errors), buildVersionNodes(), buildVersionEdges(),
                policy, loadOrder);
    }

    private void reset() {
        allUnits.clear();
        archives.clear();
        errors.clear();
        steps.clear();
        extractions.clear();
        comdatDecisions.clear();
        activatedKeys.clear();
    }

    // ------------------------------------------------------------------
    // COMDAT
    // ------------------------------------------------------------------

    private void dedupeComdat() {
        Map<String, List<GroupOccurrence>> bySignature = new LinkedHashMap<>();
        for (ParsedUnit unit : allUnits) {
            for (SectionGroup group : unit.elf.groups()) {
                if (!group.comdat() || group.signature() == null
                        || group.signature().isEmpty()) {
                    continue;
                }
                bySignature.computeIfAbsent(group.signature(),
                        ignored -> new ArrayList<>()).add(
                        new GroupOccurrence(unit, group));
            }
        }
        for (Map.Entry<String, List<GroupOccurrence>> entry : bySignature.entrySet()) {
            List<GroupOccurrence> occurrences = entry.getValue();
            if (occurrences.size() == 1) {
                continue;
            }
            // Explicit objects beat still-inert archive members; among active units
            // the first one in explicit load order keeps the group. The order here
            // is the user supplied order, never a file system traversal.
            occurrences.sort((a, b) -> {
                int active = Boolean.compare(b.unit.explicit, a.unit.explicit);
                if (active != 0) {
                    return active;
                }
                return Integer.compare(a.unit.order, b.unit.order);
            });
            GroupOccurrence keeper = occurrences.get(0);
            List<ComdatDecision.DiscardedGroup> discarded = new ArrayList<>();
            for (int i = 1; i < occurrences.size(); i++) {
                GroupOccurrence loser = occurrences.get(i);
                discarded.add(new ComdatDecision.DiscardedGroup(loser.unit.id,
                        loser.group.sectionIndex()));
                loser.unit.discardedSections.addAll(loser.group.members());
            }
            comdatDecisions.add(new ComdatDecision(entry.getKey(), keeper.unit.id,
                    keeper.group.sectionIndex(), List.copyOf(discarded)));
            step(steps.size(), "comdat", "COMDAT 去重：" + entry.getKey(),
                    "保留 " + keeper.unit.id.label() + "，丢弃 " + discarded.size()
                            + " 个同签 group", keeper.unit.id);
        }
    }

    private record GroupOccurrence(ParsedUnit unit, SectionGroup group) {
    }

    private void activateExplicitUnits() {
        for (ParsedUnit unit : allUnits) {
            if (unit.explicit) {
                activatedKeys.add(unit.id.label());
            }
        }
    }

    // ------------------------------------------------------------------
    // Lazy archive extraction
    // ------------------------------------------------------------------

    private void lazyExtract() {
        RefBucket.Registry registry = new RefBucket.Registry();
        boolean changed = true;
        int pass = 0;
        int maxPasses = Math.max(1, archives.stream().mapToInt(a -> a.members.size()).sum());
        while (changed && (!policy.singleScanArchives() || pass == 0)) {
            changed = false;
            pass++;
            if (pass > maxPasses + 1) {
                break;
            }
            for (InputLoader.ArchiveUnits archive : archives) {
                // Evaluate demand from the currently active units on each pass.
                RefBucket.Registry demand = new RefBucket.Registry();
                for (ParsedUnit unit : allUnits) {
                    if (unit.active) {
                        collectUnitReferences(unit, demand);
                    }
                }
                Set<String> demandedStrong = new LinkedHashSet<>();
                for (RefBucket bucket : demand.buckets.values()) {
                    if (bucket.hasStrong && !satisfiedByActiveStrong(bucket)) {
                        demandedStrong.add(RefBucket.key(bucket.name,
                                bucket.requestedVersion, bucket.versionFrom));
                    }
                }
                for (ParsedUnit member : archive.members) {
                    if (member.active) {
                        continue;
                    }
                    Set<String> offered = offeredKeys(member);
                    offered.retainAll(demandedStrong);
                    if (offered.isEmpty()) {
                        continue;
                    }
                    // Equal candidates across multiple members keep ambiguity:
                    // only extract when this member is the unique offeror.
                    List<ParsedUnit> peerOfferors = peerOfferors(archive, demandedStrong,
                            member, offered);
                    boolean unique = peerOfferors.isEmpty();
                    member.active = true;
                    activatedKeys.add(member.id.label());
                    String reason;
                    if (unique) {
                        reason = "解析尚未满足的强引用：" + String.join(", ", offered);
                    } else {
                        reason = "与 " + peerOfferors.size() + " 个同等 archive member 同时命中："
                                + String.join(", ", offered) + "（保留并列证据）";
                    }
                    extractions.add(new Extraction(archive.name, member.memberName,
                            member.id, true, reason, steps.size()));
                    step(steps.size(), "extract", "抽取 archive member "
                            + member.id.label(), reason, member.id);
                    changed = true;
                }
            }
            if (policy.singleScanArchives()) {
                break;
            }
        }
        // Record inert members explicitly for the player.
        for (InputLoader.ArchiveUnits archive : archives) {
            for (ParsedUnit member : archive.members) {
                if (!member.active) {
                    member.inactivityReason = "没有现存未满足强引用要求其定义（惰性抽取）";
                    extractions.add(new Extraction(archive.name, member.memberName,
                            member.id, false, member.inactivityReason, steps.size()));
                }
            }
        }
    }

    private List<ParsedUnit> peerOfferors(InputLoader.ArchiveUnits archive,
                                          Set<String> demandedStrong, ParsedUnit self,
                                          Set<String> offered) {
        List<ParsedUnit> peers = new ArrayList<>();
        for (ParsedUnit candidate : archive.members) {
            if (candidate == self || candidate.active) {
                continue;
            }
            Set<String> their = offeredKeys(candidate);
            their.retainAll(demandedStrong);
            if (!their.isEmpty() && their.equals(offered)) {
                peers.add(candidate);
            }
        }
        return peers;
    }

    private boolean satisfiedByActiveStrong(RefBucket bucket) {
        for (ParsedUnit unit : allUnits) {
            if (!unit.active) {
                continue;
            }
            for (Symbol symbol : unit.elf.symbols()) {
                if (!symbol.name().equals(bucket.name) || symbol.isUndefined()
                        || symbol.isCommon() || symbol.isWeak()
                        || unit.symbolDiscarded(symbol)) {
                    continue;
                }
                if (visibilityExportable(unit, symbol)
                        && versionMatches(bucket, unit, symbol) != MatchResult.NO) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> offeredKeys(ParsedUnit member) {
        Set<String> keys = new LinkedHashSet<>();
        if (member.discardedSections.size() == countAllocatedSections(member)) {
            return keys;
        }
        for (Symbol symbol : member.elf.symbols()) {
            if (symbol.isUndefined() || symbol.name().isEmpty()
                    || member.symbolDiscarded(symbol)
                    || symbol.binding() == Elf.STB_LOCAL) {
                continue;
            }
            keys.add(RefBucket.key(symbol.name(), null, null));
        }
        return keys;
    }

    private int countAllocatedSections(ParsedUnit member) {
        return (int) member.elf.sections().stream()
                .filter(s -> (s.flags() & Elf.SHF_ALLOC) != 0).count();
    }

    // ------------------------------------------------------------------
    // Reference collection
    // ------------------------------------------------------------------

    private void collectReferences(RefBucket.Registry registry) {
        for (ParsedUnit unit : allUnits) {
            if (unit.active) {
                collectUnitReferences(unit, registry);
            }
        }
    }

    private void collectUnitReferences(ParsedUnit unit, RefBucket.Registry registry) {
        for (Relocation relocation : unit.elf.relocations()) {
            Symbol symbol = unit.elf.symbol(relocation.symbolIndex());
            if (symbol == null || symbol.name().isEmpty()) {
                continue;
            }
            if (unit.kind == InputKind.EXECUTABLE
                    && com.tribunal.elf.RelocTypes.isCopyType(unit.elf.machine(),
                    relocation.type())) {
                // COPY relocations are adjudicated by a dedicated rule.
                continue;
            }
            addReference(unit, symbol, relocation, registry);
        }
    }

    private void addReference(ParsedUnit unit, Symbol symbol, Relocation relocation,
                              RefBucket.Registry registry) {
        boolean localBind = false;
        String localNote = null;
        String requestedVersion = null;
        String versionFrom = null;
        if (symbol.isUndefined()) {
            if (symbol.versionName() != null) {
                requestedVersion = symbol.versionName();
                versionFrom = versionOwner(unit, symbol);
            }
            if (symbol.binding() == Elf.STB_WEAK) {
                // Weak undefined: archive lazy extraction must not be triggered.
            }
        } else if (symbol.binding() == Elf.STB_LOCAL) {
            localBind = true;
            localNote = "LOCAL 符号只绑定本对象 section "
                    + unit.sectionName(symbol.shndx());
        } else if (unit.kind == InputKind.SHARED && symbol.isProtected()) {
            localBind = true;
            localNote = "PROTECTED 符号在本共享库内部绑定，不参与跨库介入";
        }
        RefBucket bucket = registry.bucket(symbol.name(), requestedVersion, versionFrom);
        if (symbol.isUndefined()) {
            if (symbol.binding() == Elf.STB_WEAK) {
                bucket.hasWeak = true;
            } else {
                bucket.hasStrong = true;
            }
            if (symbol.hiddenVersion()) {
                bucket.nonDefaultRef = true;
            }
        }
        bucket.sites.add(new RefBucket.RefSite(unit, symbol, relocation, localBind,
                localNote));
    }

    private String versionOwner(ParsedUnit unit, Symbol symbol) {
        // GNU verneed entries name the provider SONAME; versym itself does not.
        String fallback = unit.elf.soname() == null ? unit.id.label() : unit.elf.soname();
        for (var need : unit.elf.versionNeeds()) {
            if (need.versionName().equals(symbol.versionName())) {
                return need.fileName();
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Candidate visibility / version checks
    // ------------------------------------------------------------------

    private boolean visibilityExportable(ParsedUnit unit, Symbol symbol) {
        if (symbol.binding() == Elf.STB_LOCAL) {
            return false;
        }
        if (unit.kind == InputKind.SHARED && symbol.isHidden()) {
            return false;
        }
        return true;
    }

    enum MatchResult {
        YES,
        NO,
        ONLY_PROTECTED
    }

    private MatchResult versionMatches(RefBucket bucket, ParsedUnit unit, Symbol symbol) {
        if (bucket.requestedVersion == null) {
            // Unversioned reference: binds to the default version of that symbol.
            if (symbol.versionName() == null) {
                return MatchResult.YES;
            }
            if (symbol.defaultVersion() && !symbol.hiddenVersion()) {
                return MatchResult.YES;
            }
            return MatchResult.NO;
        }
        if (symbol.versionName() == null) {
            return MatchResult.NO;
        }
        if (!symbol.versionName().equals(bucket.requestedVersion)) {
            return MatchResult.NO;
        }
        // Version node names may exist in several libraries; when the reference
        // names a provider SONAME, require it to agree.
        if (bucket.versionFrom != null && unit.kind == InputKind.SHARED) {
            String soname = unit.elf.soname() != null ? unit.elf.soname()
                    : unit.id.unit();
            if (!sameLibrary(bucket.versionFrom, soname)) {
                return MatchResult.NO;
            }
        }
        if (unit.kind == InputKind.SHARED && symbol.isProtected()) {
            return MatchResult.ONLY_PROTECTED;
        }
        return MatchResult.YES;
    }

    private boolean sameLibrary(String requested, String provided) {
        if (requested == null || provided == null) {
            return true;
        }
        String a = requested.contains("/")
                ? requested.substring(requested.lastIndexOf('/') + 1) : requested;
        String b = provided.contains("/")
                ? provided.substring(provided.lastIndexOf('/') + 1) : provided;
        return a.equals(b);
    }

    // ------------------------------------------------------------------
    // Adjudication
    // ------------------------------------------------------------------

    private List<Conclusion> adjudicate(RefBucket.Registry registry) {
        List<Conclusion> conclusions = new ArrayList<>();
        for (RefBucket bucket : registry.buckets.values()) {
            conclusions.add(adjudicateBucket(bucket));
        }
        return conclusions;
    }

    private Conclusion adjudicateBucket(RefBucket bucket) {
        List<CandidateBuild> builds = new ArrayList<>();
        List<RelocRef> relocRefs = new ArrayList<>();

        for (ParsedUnit unit : allUnits) {
            boolean fromArchive = unit.memberName != null;
            for (Symbol symbol : unit.elf.symbols()) {
                if (!symbol.name().equals(bucket.name) || symbol.isUndefined()) {
                    continue;
                }
                CandidateBuild build = evaluateCandidate(bucket, unit, symbol,
                        fromArchive);
                if (build != null) {
                    builds.add(build);
                }
            }
        }

        // Resolve local/protected bindings at the relocation sites.
        for (RefBucket.RefSite site : bucket.sites) {
            String bind = "GLOBAL";
            String note = null;
            if (site.localBind()) {
                bind = "LOCAL";
                note = site.localNote();
            }
            relocRefs.add(new RelocRef(site.unit().id, site.relocation().sectionIndex(),
                    site.unit().sectionName(site.relocation().sectionIndex()),
                    site.relocation().offset(), site.relocation().addend(),
                    com.tribunal.elf.RelocTypes.name(site.unit().elf.machine(),
                            site.relocation().type()), bind, note));
        }

        return decide(bucket, builds, relocRefs);
    }

    private record CandidateBuild(Candidate candidate, int rank, ParsedUnit unit,
                                  Symbol symbol) {
    }

    private CandidateBuild evaluateCandidate(RefBucket bucket, ParsedUnit unit,
                                             Symbol symbol, boolean fromArchive) {
        EliminationReason eliminated = null;
        String detail = null;
        if (symbol.binding() == Elf.STB_LOCAL) {
            eliminated = EliminationReason.LOCAL_NOT_EXPORTED;
        } else if (unit.kind == InputKind.SHARED && symbol.isHidden()) {
            eliminated = EliminationReason.HIDDEN_NOT_EXPORTED;
            detail = "visibility=" + Elf.visibilityName(symbol.visibility());
        } else if (unit.symbolDiscarded(symbol)) {
            eliminated = EliminationReason.COMDAT_DISCARDED;
            detail = "所属 COMDAT group 已被去重丢弃";
        } else {
            MatchResult match = versionMatches(bucket, unit, symbol);
            if (match == MatchResult.NO) {
                if (symbol.hiddenVersion()) {
                    eliminated = EliminationReason.VERSYM_HIDDEN;
                    detail = "版本 " + displayVersion(symbol.versionName())
                            + " 为非默认隐藏版本";
                } else if (bucket.requestedVersion == null && symbol.versionName() != null
                        && !symbol.defaultVersion()) {
                    eliminated = EliminationReason.NOT_DEFAULT_UNVERSIONED;
                    detail = "引用未请求版本，提供者仅为非默认版本 "
                            + symbol.versionName();
                } else {
                    eliminated = EliminationReason.WRONG_VERSION;
                    detail = "引用请求 " + displayVersion(bucket.requestedVersion)
                            + "，提供者为 " + displayVersion(symbol.versionName());
                }
            } else if (match == MatchResult.ONLY_PROTECTED) {
                // It satisfies same-DSO references but never external ones.
                boolean hasExternalRef = bucket.sites.stream().anyMatch(
                        s -> !s.unit().id.equals(unit.id));
                if (hasExternalRef) {
                    eliminated = EliminationReason.PROTECTED_BIND_LOCAL;
                    detail = "PROTECTED 数据不能被其他库介入（copy relocation 亦被禁止）";
                }
            }
        }
        if (!unit.active && eliminated == null) {
            eliminated = EliminationReason.ARCHIVE_NOT_NEEDED;
            detail = "archive member 未被任何未满足强引用抽取";
        }
        Candidate candidate = new Candidate(unit.id, symbol.name(), symbol.versionName(),
                symbol.defaultVersion(), symbol.binding(), symbol.type(),
                symbol.visibility(), symbol.value(), symbol.size(), symbol.isCommon(),
                fromArchive, unit.active, eliminated, detail);
        return new CandidateBuild(candidate,
                CandidateRank.rank(unit, symbol, policy), unit, symbol);
    }

    private Conclusion decide(RefBucket bucket, List<CandidateBuild> builds,
                              List<RelocRef> relocRefs) {
        // First pass: find the best rank among the alive candidates.
        int bestRank = Integer.MAX_VALUE;
        for (CandidateBuild build : builds) {
            if (build.candidate.alive() && build.rank < bestRank) {
                bestRank = build.rank;
            }
        }
        // Mark lower-ranked candidates eliminated by binding precedence;
        // equal-ranked later candidates by load order (if decisive).
        List<Candidate> presented = new ArrayList<>();
        List<CandidateBuild> best = new ArrayList<>();
        for (CandidateBuild build : builds) {
            Candidate c = build.candidate;
            if (c.alive()) {
                if (build.rank > bestRank) {
                    boolean commonWeakSwap = build.rank == CandidateRank.OBJECT_COMMON
                            || build.rank == CandidateRank.OBJECT_WEAK;
                    c = withElimination(c, EliminationReason.LOWER_BINDING,
                            (commonWeakSwap ? "绑定层级低于中选候选（" : "绑定层级（")
                                    + CandidateRank.rankName(build.rank) + "低于"
                                    + CandidateRank.rankName(bestRank) + "）");
                } else {
                    best.add(new CandidateBuild(c, build.rank, build.unit, build.symbol));
                }
            }
            presented.add(c);
        }

        List<UnitId> winners = new ArrayList<>();
        VerdictStatus status;
        String explanation;
        if (bestRank == Integer.MAX_VALUE) {
            boolean versionProblem = presented.stream().anyMatch(c ->
                    c.eliminated() == EliminationReason.WRONG_VERSION
                            || c.eliminated() == EliminationReason.VERSYM_HIDDEN
                            || c.eliminated() == EliminationReason.NOT_DEFAULT_UNVERSIONED);
            if (bucket.hasStrong) {
                status = versionProblem ? VerdictStatus.UNRESOLVED_VERSION
                        : VerdictStatus.UNRESOLVED;
                explanation = versionProblem
                        ? "存在同名符号但没有任何候选满足所请求的版本 "
                                + displayVersion(bucket.requestedVersion)
                        : "没有任何存活候选提供该符号";
            } else {
                status = VerdictStatus.RESOLVED_WEAK_NULL;
                explanation = "仅存在弱引用且无定义：解析为 0（空绑定），链接继续";
            }
            if (bucket.sites.stream().anyMatch(s -> s.localBind())) {
                explanation += "；部分 LOCAL/PROTECTED 引用在各自单元内部解析";
            }
        } else if (best.size() == 1) {
            CandidateBuild winner = best.get(0);
            // Apply load order elimination to other equal-rank alive candidates
            // (already removed); mark remaining equal ones below if any.
            winners.add(winner.unit.id);
            presented = markLoadOrderLosers(presented, winner.unit.id, bestRank);
            status = VerdictStatus.RESOLVED;
            explanation = "选中 " + winner.unit.id.label() + " 中的 "
                    + CandidateRank.rankName(bestRank)
                    + "（" + Elf.bindingName(winner.symbol.binding()) + " "
                    + Elf.typeName(winner.symbol.type()) + "），地址 0x"
                    + Long.toHexString(winner.symbol.value());
        } else {
            // Multiple equal candidates: preserve ambiguity. Only collapse it
            // when the tie is fully explained by explicit load order AND the
            // symbols are not duplicate strong definitions of the same kind.
            boolean allObjectStrong = best.stream()
                    .allMatch(b -> b.rank == CandidateRank.OBJECT_STRONG
                            && b.unit.kind != InputKind.SHARED);
            boolean distinctUnits = best.stream().map(b -> b.unit.id).distinct().count()
                    == best.size();
            if (allObjectStrong && distinctUnits) {
                status = VerdictStatus.AMBIGUOUS;
                best.forEach(b -> winners.add(b.unit.id));
                explanation = "多个对象提供同等强定义，保留歧义，不使用文件系统次序裁决";
            } else {
                best.sort((a, b) -> Integer.compare(a.unit.order, b.unit.order));
                CandidateBuild winner = best.get(0);
                winners.add(winner.unit.id);
                for (int i = 1; i < best.size(); i++) {
                    final UnitId loser = best.get(i).unit.id;
                    presented = presented.stream().map(c -> c.unit().equals(loser)
                            && c.alive()
                            ? withElimination(c, EliminationReason.LOAD_ORDER,
                            "同等候选但在显式加载顺序中靠后") : c).toList();
                }
                status = VerdictStatus.RESOLVED;
                explanation = "同等候选按显式加载顺序选择 " + winner.unit.id.label();
            }
        }

        return new Conclusion(bucket.name, bucket.requestedVersion, bucket.versionFrom,
                status, List.copyOf(presented), List.copyOf(winners),
                List.copyOf(relocRefs), explanation);
    }

    private List<Candidate> markLoadOrderLosers(List<Candidate> presented, UnitId winner,
                                                int bestRank) {
        return presented;
    }

    private Candidate withElimination(Candidate c, EliminationReason reason, String detail) {
        return new Candidate(c.unit(), c.symbolName(), c.versionName(),
                c.defaultVersion(), c.binding(), c.symbolType(), c.visibility(),
                c.value(), c.size(), c.common(), c.fromArchive(), c.extracted(),
                reason, detail);
    }

    private String displayVersion(String version) {
        return version == null ? "（无版本）" : version;
    }

    // ------------------------------------------------------------------
    // COPY relocations
    // ------------------------------------------------------------------

    private List<Conclusion> handleCopyRelocations(List<Conclusion> normal,
                                                   RefBucket.Registry registry) {
        List<Conclusion> all = new ArrayList<>(normal);
        for (ParsedUnit unit : allUnits) {
            if (unit.kind != InputKind.EXECUTABLE) {
                continue;
            }
            for (Relocation relocation : unit.elf.relocations()) {
                if (!com.tribunal.elf.RelocTypes.isCopyType(unit.elf.machine(),
                        relocation.type())) {
                    continue;
                }
                Symbol ref = unit.elf.symbol(relocation.symbolIndex());
                if (ref == null) {
                    continue;
                }
                all.add(adjudicateCopy(unit, relocation, ref));
                step(steps.size(), "copy", "COPY relocation：" + ref.name(),
                        "可执行文件为数据符号 " + ref.name()
                                + " 在自身 .bss 分配副本，提供者解析不被普通覆盖规则吞掉",
                        unit.id);
            }
        }
        return all;
    }

    private Conclusion adjudicateCopy(ParsedUnit exec, Relocation relocation,
                                      Symbol ref) {
        List<Candidate> candidates = new ArrayList<>();
        Candidate winner = null;
        for (ParsedUnit unit : allUnits) {
            if (unit.kind != InputKind.SHARED || !unit.active) {
                continue;
            }
            for (Symbol symbol : unit.elf.symbols()) {
                if (!symbol.name().equals(ref.name()) || symbol.isUndefined()) {
                    continue;
                }
                Candidate candidate = new Candidate(unit.id, symbol.name(),
                        symbol.versionName(), symbol.defaultVersion(), symbol.binding(),
                        symbol.type(), symbol.visibility(), symbol.value(),
                        symbol.size(), symbol.isCommon(), false, true, null, null);
                boolean protectedData = symbol.isProtected();
                boolean versionOk = ref.versionName() == null
                        || symbol.versionName() == null
                        || symbol.versionName().equals(ref.versionName());
                if (protectedData) {
                    candidate = withElimination(candidate,
                            EliminationReason.PROTECTED_BIND_LOCAL,
                            "PROTECTED 数据禁止 copy relocation");
                } else if (symbol.isHidden()) {
                    candidate = withElimination(candidate,
                            EliminationReason.HIDDEN_NOT_EXPORTED,
                            "HIDDEN 符号不导出，无法 copy");
                } else if (!versionOk) {
                    candidate = withElimination(candidate, EliminationReason.WRONG_VERSION,
                            "copy 源版本 " + displayVersion(symbol.versionName())
                                    + " 不匹配引用 " + displayVersion(ref.versionName()));
                } else if (winner == null) {
                    winner = candidate;
                } else {
                    candidate = withElimination(candidate, EliminationReason.LOAD_ORDER,
                            "copy 源按显式加载顺序选择先加载的库");
                }
                candidates.add(candidate);
            }
        }
        List<RelocRef> refs = List.of(new RelocRef(exec.id, relocation.sectionIndex(),
                exec.sectionName(relocation.sectionIndex()), relocation.offset(),
                relocation.addend(),
                com.tribunal.elf.RelocTypes.name(exec.elf.machine(), relocation.type()),
                "COPY", "可执行文件复制数据对象，后续引用绑定本地副本"));
        VerdictStatus status;
        List<UnitId> winners = new ArrayList<>();
        String explanation;
        if (!policy.copyRelocations()) {
            status = VerdictStatus.COPY_BLOCKED_PROTECTED;
            explanation = "当前目标平台策略禁用 COPY relocation：该数据引用不能按普通覆盖规则裁决";
        } else if (winner == null) {
            boolean blocked = candidates.stream().anyMatch(c ->
                    c.eliminated() == EliminationReason.PROTECTED_BIND_LOCAL);
            status = blocked ? VerdictStatus.COPY_BLOCKED_PROTECTED
                    : VerdictStatus.UNRESOLVED;
            explanation = blocked
                    ? "唯一可复制源是 PROTECTED 数据：copy relocation 被禁止，链接失败"
                    : "没有可作为 copy 源的已导出数据符号";
        } else {
            status = VerdictStatus.COPY;
            winners.add(winner.unit());
            explanation = "COPY：从 " + winner.unit().label() + " 复制 " + ref.name()
                    + "（大小 " + winner.size() + " 字节）到可执行文件，普通覆盖规则不适用";
        }
        return new Conclusion(ref.name(), ref.versionName(), null, status,
                List.copyOf(candidates), winners, refs, explanation);
    }

    // ------------------------------------------------------------------
    // UI support
    // ------------------------------------------------------------------

    private List<LoadedUnit> describeUnits() {
        List<LoadedUnit> out = new ArrayList<>();
        for (ParsedUnit unit : allUnits) {
            ElfFile elf = unit.elf;
            out.add(new LoadedUnit(unit.id, unit.kind, Elf.typeName(elf.type()),
                    Elf.machineName(elf.machine()), elf.soname(), elf.needed(),
                    unit.active, unit.inactivityReason, elf));
        }
        return out;
    }

    private List<VersionNode> buildVersionNodes() {
        Set<String> seen = new LinkedHashSet<>();
        List<VersionNode> nodes = new ArrayList<>();
        for (ParsedUnit unit : allUnits) {
            String lib = unit.elf.soname() != null ? unit.elf.soname() : unit.id.label();
            if (seen.add("lib:" + lib)) {
                nodes.add(new VersionNode("lib:" + lib, lib, "library"));
            }
            for (var def : unit.elf.versionDefs()) {
                String id = "ver:" + lib + ":" + def.name();
                if (seen.add(id)) {
                    nodes.add(new VersionNode(id, def.name()
                            + (def.base() ? " [BASE]" : "")
                            + (def.weak() ? " [WEAK]" : ""), "definition"));
                }
            }
            for (var need : unit.elf.versionNeeds()) {
                String id = "need:" + need.fileName() + ":" + need.versionName();
                if (seen.add(id)) {
                    nodes.add(new VersionNode(id, need.fileName() + " 需求 "
                            + need.versionName() + (need.weak() ? " [WEAK]" : ""),
                            "requirement"));
                }
            }
        }
        return nodes;
    }

    private List<VersionEdge> buildVersionEdges() {
        List<VersionEdge> edges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParsedUnit unit : allUnits) {
            String lib = unit.elf.soname() != null ? unit.elf.soname() : unit.id.label();
            for (var def : unit.elf.versionDefs()) {
                if (def.base()) {
                    continue;
                }
                for (String parent : def.parents()) {
                    addEdge(edges, seen, "ver:" + lib + ":" + def.name(),
                            "ver:" + lib + ":" + parent, "继承");
                }
            }
            for (var need : unit.elf.versionNeeds()) {
                String libNode = "lib:" + lib;
                String needNode = "need:" + need.fileName() + ":" + need.versionName();
                addEdge(edges, seen, libNode, needNode, "版本需求");
                addEdge(edges, seen, needNode,
                        "ver:" + need.fileName() + ":" + need.versionName(), "由定义满足");
            }
        }
        return edges;
    }

    private void addEdge(List<VersionEdge> edges, Set<String> seen, String from,
                         String to, String label) {
        String key = from + "->" + to + ":" + label;
        if (seen.add(key)) {
            edges.add(new VersionEdge(from, to, label));
        }
    }

    private void step(int index, String kind, String title, String detail, UnitId unit) {
        List<String> snapshot = new ArrayList<>();
        List<String> active = activatedKeys.stream().toList();
        if (unit != null) {
            ParsedUnit parsed = findUnit(unit);
            if (parsed != null) {
                for (Symbol symbol : parsed.elf.symbols()) {
                    if (symbol.name().isEmpty()) {
                        continue;
                    }
                    snapshot.add(Elf.bindingName(symbol.binding()) + " "
                            + Elf.typeName(symbol.type()) + " " + symbol.name()
                            + (symbol.versionName() != null ? "@" + symbol.versionName()
                            + (symbol.defaultVersion() ? "" : "(非默认)") : "")
                            + "  shndx=" + symbol.shndx()
                            + (parsed.symbolDiscarded(symbol) ? " [COMDAT丢弃]" : "")
                            + "  vis=" + Elf.visibilityName(symbol.visibility()));
                }
            }
        }
        steps.add(new StepEvent(index, kind, title, detail,
                unit == null ? null : unit.label(), List.copyOf(snapshot), active));
    }

    private ParsedUnit findUnit(UnitId id) {
        return allUnits.stream().filter(u -> u.id.equals(id)).findFirst().orElse(null);
    }
}
