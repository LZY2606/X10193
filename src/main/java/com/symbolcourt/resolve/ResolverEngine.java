package com.symbolcourt.resolve;

import com.symbolcourt.elf.ElfConstants;
import com.symbolcourt.elf.ElfFile;
import com.symbolcourt.elf.ElfRelocation;
import com.symbolcourt.elf.ElfSection;
import com.symbolcourt.elf.ElfSymbol;
import com.symbolcourt.elf.SectionGroup;
import com.symbolcourt.model.Candidate;
import com.symbolcourt.model.InputEntry;
import com.symbolcourt.model.LoadUnit;
import com.symbolcourt.model.Policy;
import com.symbolcourt.model.Reference;
import com.symbolcourt.model.ResolutionRecord;
import com.symbolcourt.model.ResolutionStatus;
import com.symbolcourt.model.ResolveEvent;
import com.symbolcourt.model.ResolutionResult;
import com.symbolcourt.model.SymbolKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The arbitration court. Determines, for every undefined reference and its
 * affected relocations, which provider wins and why - without invoking any
 * system linker.
 */
public final class ResolverEngine {

    private int eventSeq;

    public ResolutionResult arbitrate(List<InputEntry> entries, Map<String, byte[]> blobs,
                                      Policy policy) {
        eventSeq = 0;
        ResolutionResult result = new ResolutionResult(policy);
        Loader loader = new Loader();
        Loader.LoadedInput loaded = loader.load(entries, blobs, bad ->
                result.getWarnings().add("input '" + bad.getDisplayName()
                        + "' is corrupt and was isolated: " + bad.getParseError()));

        // ---- COMDAT selection across initially loaded relocatable objects ----
        Map<String, ComdatWinner> comdat = new LinkedHashMap<>();
        Map<String, Set<String>> discardedSectionsByUnit = new HashMap<>();

        List<LoadUnit> active = new ArrayList<>(loaded.directUnits);
        for (LoadUnit u : active) {
            result.getUnits().add(u);
            if (u.isParseOk()) {
                emit(result, ResolveEvent.Type.UNIT_LOADED, "载入对象", u.getDisplayName())
                        .with("unitId", u.getUnitId())
                        .with("kind", u.getKind().name().toLowerCase());
                selectComdat(u, comdat, discardedSectionsByUnit, result);
            }
        }
        for (String w : loaded.warnings) {
            result.getWarnings().add(w);
            emit(result, ResolveEvent.Type.WARNING, "警告", w);
        }

        // ---- Index providers from active units; run archive fixed-point extraction ----
        ProviderIndex index = new ProviderIndex();
        for (LoadUnit u : active) {
            if (u.isParseOk()) {
                indexUnit(u, index, discardedSectionsByUnit, result);
            }
        }

        List<Reference> references = new ArrayList<>();
        Set<String> openedRefs = new HashSet<>();
        for (LoadUnit u : active) {
            if (u.isParseOk()) {
                collectReferences(u, references, openedRefs, result);
            }
        }

        extractArchives(result, loaded, active, index, references, openedRefs,
                discardedSectionsByUnit, comdat, policy);

        // ---- Arbitrate each reference ----
        for (Reference ref : references) {
            arbitrateReference(ref, index, result, policy);
        }

        buildVersionGraph(loaded, result);
        buildFinalSymbolTable(index, result);
        return result;
    }

    private static final class ComdatWinner {
        final LoadUnit unit;
        final SectionGroup group;

        ComdatWinner(LoadUnit unit, SectionGroup group) {
            this.unit = unit;
            this.group = group;
        }
    }

    private void selectComdat(LoadUnit u, Map<String, ComdatWinner> comdat,
                              Map<String, Set<String>> discarded, ResolutionResult result) {
        ElfFile elf = u.getElf();
        if (elf == null || elf.getType() != ElfConstants.ET_REL) {
            return;
        }
        Set<String> mine = discarded.computeIfAbsent(u.getUnitId(), k -> new HashSet<>());
        for (SectionGroup g : elf.getGroups()) {
            if (!g.isComdat()) {
                continue;
            }
            ComdatWinner existing = comdat.get(g.getSignature());
            if (existing == null) {
                comdat.put(g.getSignature(), new ComdatWinner(u, g));
                emit(result, ResolveEvent.Type.COMDAT_SELECTED, "COMDAT 保留",
                        g.getSignature()).with("signature", g.getSignature())
                        .with("unitId", u.getUnitId());
                continue;
            }
            // Duplicate signature: first winner in load order keeps its sections; the later
            // group's member sections are discarded (COMDAT deduplication, not normal override).
            if (existing.unit.getUnitId().equals(u.getUnitId())) {
                continue; // same object already won
            }
            for (Integer secIdx : g.getMembers()) {
                ElfSection sec = elf.sectionByIndex(secIdx);
                if (sec != null) {
                    mine.add(sec.getName());
                }
            }
            emit(result, ResolveEvent.Type.COMDAT_DISCARDED, "COMDAT 去重丢弃",
                    g.getSignature() + " 重复于 " + existing.unit.getDisplayName())
                    .with("signature", g.getSignature())
                    .with("unitId", u.getUnitId())
                    .with("winnerUnitId", existing.unit.getUnitId())
                    .with("sections", g.getMembers().stream()
                            .map(i -> {
                                ElfSection s = elf.sectionByIndex(i);
                                return s == null ? ("#" + i) : s.getName();
                            }).toList());
        }
    }

    private void indexUnit(LoadUnit u, ProviderIndex index,
                           Map<String, Set<String>> discardedSections, ResolutionResult result) {
        ElfFile elf = u.getElf();
        Set<String> discarded = discardedSections.getOrDefault(u.getUnitId(), Set.of());

        // Determine which sections survive COMDAT and whether the symbol lives in a group.
        Set<String> groupSections = new HashSet<>();
        Map<String, Boolean> sectionInWinningGroup = new HashMap<>();
        for (SectionGroup g : elf.getGroups()) {
            for (Integer idx : g.getMembers()) {
                ElfSection s = elf.sectionByIndex(idx);
                if (s != null) {
                    groupSections.add(s.getName());
                }
            }
        }

        List<ElfSymbol> source = symbolsForIndexing(elf);
        for (ElfSymbol s : source) {
            if (!ProviderIndex.isExternallyProvided(s, elf, discarded)) {
                continue;
            }
            boolean comdatProvided = false;
            ElfSection sec = elf.sectionByIndex(s.getShndx());
            if (sec != null && groupSections.contains(sec.getName())) {
                comdatProvided = true;
            }
            Candidate c = ProviderIndex.toCandidate(u, s,
                    sec != null && discarded.contains(sec.getName()), comdatProvided);
            SymbolKey key = keyOf(s, elf);
            index.add(key, new ProviderIndex.Provider(u, s, c));
        }
    }

    private List<ElfSymbol> symbolsForIndexing(ElfFile elf) {
        // Relocatable objects: static symtab. Shared/exec: dynamic symtab (exported ABI).
        if (elf.getType() == ElfConstants.ET_REL) {
            return elf.getStaticSymbols().isEmpty() ? elf.getDynamicSymbols()
                    : elf.getStaticSymbols();
        }
        return elf.getDynamicSymbols().isEmpty() ? elf.getStaticSymbols()
                : elf.getDynamicSymbols();
    }

    private SymbolKey keyOf(ElfSymbol s, ElfFile elf) {
        if (s.getVersionName() == null) {
            return SymbolKey.unversioned(s.getName());
        }
        // A default definition participates both in the exact versioned slot and, for
        // unversioned references, as the default node. We key by version and let the
        // matcher treat defaultVersion providers when resolving unversioned requests.
        return new SymbolKey(s.getName(), s.getVersionName(), s.isDefaultVersion());
    }

    // ------------------------------------------------------------------
    // Reference collection
    // ------------------------------------------------------------------

    private void collectReferences(LoadUnit u, List<Reference> references, Set<String> opened,
                                   ResolutionResult result) {
        ElfFile elf = u.getElf();
        List<ElfSymbol> syms = symbolsForIndexing(elf);
        Map<String, String> verneedFile = new HashMap<>();
        for (var vr : elf.getVersionRequirements()) {
            for (var n : vr.getNodes()) {
                verneedFile.put(n.getName(), vr.getFileName());
            }
        }

        // Undefined symbols in the symbol table are the references. Prefer relocation-driven
        // references (so affected relocations attach precisely), then add orphan undef syms.
        Set<String> seenOrphan = new HashSet<>();
        for (ElfRelocation rel : elf.getRelocations()) {
            int si = rel.getSymbolIndex();
            if (si < 0 || si >= syms.size()) {
                continue;
            }
            ElfSymbol s = syms.get(si);
            if (s.isDefined()) {
                continue;
            }
            String id = refId(u, s, rel.getOffset());
            if (!opened.add(id)) {
                continue;
            }
            SymbolKey key = referenceKey(s);
            references.add(new Reference(id, u.getUnitId(), s.getName(), key, s.getBind(),
                    rel.isCopyRelocation(), rel.getSectionName(), rel.getOffset(),
                    rel.getAddend(), rel.getType(),
                    s.getVersionSource() == ElfSymbol.VersionSource.VERNEED
                            ? verneedFile.get(s.getVersionName()) : null,
                    s.isWeak() && s.getVersionName() == null));
        }
        // Undefined symbols without a relocation (still a link requirement for executables).
        for (ElfSymbol s : syms) {
            if (s.isDefined() || s.getName() == null || s.getName().isEmpty()) {
                continue;
            }
            String id = refId(u, s, -1);
            if (!opened.add(id)) {
                continue;
            }
            SymbolKey key = referenceKey(s);
            references.add(new Reference(id, u.getUnitId(), s.getName(), key, s.getBind(),
                    false, null, -1, 0, -1,
                    s.getVersionSource() == ElfSymbol.VersionSource.VERNEED
                            ? verneedFile.get(s.getVersionName()) : null,
                    s.isWeak() && s.getVersionName() == null));
        }
    }

    private SymbolKey referenceKey(ElfSymbol s) {
        return new SymbolKey(s.getName(), s.getVersionName(), false);
    }

    private String refId(LoadUnit u, ElfSymbol s, long off) {
        return u.getUnitId() + "::" + s.getName()
                + (s.getVersionName() == null ? "" : "@" + s.getVersionName())
                + (off < 0 ? ":sym" : (":" + off));
    }


    // ------------------------------------------------------------------
    // Archive lazy extraction (with cycle fixed-point)
    // ------------------------------------------------------------------

    private void extractArchives(ResolutionResult result, Loader.LoadedInput loaded,
                                 List<LoadUnit> active, ProviderIndex index,
                                 List<Reference> references, Set<String> openedRefs,
                                 Map<String, Set<String>> discarded,
                                 Map<String, ComdatWinner> comdat, Policy policy) {
        Set<String> extractedUnitIds = new HashSet<>();
        boolean singlePass = policy.archiveStrategy() == Policy.ArchiveStrategy.SINGLE_PASS;

        int sweep = 0;
        boolean changedThisSweep;
        do {
            sweep++;
            if (sweep > 1) {
                emit(result, ResolveEvent.Type.ARCHIVE_CYCLE_RETRY, "Archive 循环重扫",
                        "第 " + sweep + " 轮定点扫描（解析 archive 循环依赖）").with("sweep", sweep);
            }
            changedThisSweep = false;
            // Repeat until no member in this sweep is pulled (chains within a sweep).
            boolean pulled;
            do {
                pulled = false;
                for (Reference ref : new ArrayList<>(unsatisfiedExtractionNeeds(references, index))) {
                    for (Loader.ArchiveLibrary lib : new ArrayList<>(loaded.archives)) {
                        List<Integer> orders = lib.symbolToMemberOrders.get(ref.getSymbolName());
                        if (orders == null || orders.isEmpty()) {
                            continue;
                        }
                        List<Integer> fresh = new ArrayList<>();
                        for (int order : orders) {
                            String uid = lib.members.get(order).unit.getUnitId();
                            if (!extractedUnitIds.contains(uid)) {
                                fresh.add(order);
                            }
                        }
                        if (fresh.isEmpty()) {
                            continue;
                        }
                        if (fresh.size() > 1) {
                            result.getWarnings().add("archive " + lib.displayName
                                    + " 中符号 '" + ref.getSymbolName()
                                    + "' 有多个惰性候选成员，按显式成员顺序全部保留为歧义候选");
                            emit(result, ResolveEvent.Type.WARNING, "Archive 重复候选",
                                    ref.getSymbolName() + " @ " + lib.displayName);
                        }
                        for (int order : fresh) {
                            Loader.PendingMember pm = lib.members.get(order);
                            extractMember(result, lib, pm, order, active, index, references,
                                    openedRefs, discarded, comdat, extractedUnitIds, ref);
                            pulled = true;
                            changedThisSweep = true;
                        }
                    }
                }
            } while (pulled);

            // Emit non-extraction diagnostics for unresolved names present in no archive.
            if (singlePass) {
                break;
            }
        } while (changedThisSweep);
    }

    private List<Reference> unsatisfiedExtractionNeeds(List<Reference> references,
                                                       ProviderIndex index) {
        List<Reference> out = new ArrayList<>();
        for (Reference ref : references) {
            if (ref.getBind() == ElfConstants.STB_WEAK) {
                continue; // weak refs never pull archive members
            }
            if (matchingProviders(ref, index).isEmpty()) {
                out.add(ref);
            }
        }
        return out;
    }

    private void extractMember(ResolutionResult result, Loader.ArchiveLibrary lib,
                               Loader.PendingMember pm, int order, List<LoadUnit> active,
                               ProviderIndex index, List<Reference> references,
                               Set<String> openedRefs, Map<String, Set<String>> discarded,
                               Map<String, ComdatWinner> comdat,
                               Set<String> extractedUnitIds, Reference triggeredBy) {
        LoadUnit u = pm.unit;
        if (!extractedUnitIds.add(u.getUnitId())) {
            return;
        }
        // Members were parsed during indexing; mark extraction and assign a stable load index
        // derived from archive position and member order (deterministic, never FS order).
        u.markExtracted(triggeredBy.getReferenceId());
        u.assignLoadIndex(lib.loadIndex * 100000 + order);
        active.add(u);
        result.getUnits().add(u);
        emit(result, ResolveEvent.Type.ARCHIVE_EXTRACTED, "惰性抽取 archive 成员",
                        u.getDisplayName() + " 被引用 " + triggeredBy.getSymbolName() + " 抽取")
                .with("unitId", u.getUnitId())
                .with("archiveId", lib.archiveId)
                .with("member", u.getArchiveMemberName())
                .with("triggeredBy", triggeredBy.getReferenceId());
        if (u.isParseOk()) {
            selectComdat(u, comdat, discarded, result);
            indexUnit(u, index, discarded, result);
            collectReferences(u, references, openedRefs, result);
            emit(result, ResolveEvent.Type.SYMBOL_TABLE_UPDATE, "符号表更新",
                    "抽取 " + u.getArchiveMemberName() + " 后并入其定义与未定义符号")
                    .with("unitId", u.getUnitId())
                    .with("defined", countDefined(u))
                    .with("undefined", countUndefined(u));
        }
    }

    private int countDefined(LoadUnit u) {
        int n = 0;
        for (ElfSymbol s : symbolsForIndexing(u.getElf())) {
            if (s.isDefined() && !s.isLocal()) n++;
        }
        return n;
    }

    private int countUndefined(LoadUnit u) {
        int n = 0;
        for (ElfSymbol s : symbolsForIndexing(u.getElf())) {
            if (!s.isDefined()) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    /** Providers that can satisfy the reference's name/version request. */
    private List<ProviderIndex.Provider> matchingProviders(Reference ref, ProviderIndex index) {
        List<ProviderIndex.Provider> out = new ArrayList<>();
        String wantVersion = ref.getKey().getVersion();

        if (wantVersion == null) {
            // Unversioned request: bind only to the default node (@@), or to an unversioned
            // provider. Hidden/non-default "@" nodes must not be selected.
            for (ProviderIndex.Provider p : index.exact(SymbolKey.unversioned(ref.getSymbolName()))) {
                out.add(p);
            }
            for (ProviderIndex.Provider p : index.byName(ref.getSymbolName())) {
                ElfSymbol s = p.symbol;
                if (s.getVersionName() != null && s.isDefaultVersion()) {
                    if (!out.contains(p)) {
                        out.add(p);
                    }
                }
            }
        } else {
            // Versioned request: exact version match required; default flag does not matter
            // for a request that explicitly asks for a non-default node.
            for (ProviderIndex.Provider p : index.byName(ref.getSymbolName())) {
                ElfSymbol s = p.symbol;
                if (wantVersion.equals(s.getVersionName())) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private void arbitrateReference(Reference ref, ProviderIndex index,
                                    ResolutionResult result, Policy policy) {
        ResolutionRecord rec = new ResolutionRecord(ref);
        emit(result, ResolveEvent.Type.REFERENCE_OPENED, "解析引用",
                        ref.getSymbolName() + " 来自 " + ref.getUnitId())
                .with("referenceId", ref.getReferenceId())
                .with("symbol", ref.getSymbolName())
                .with("version", ref.getKey().getVersion())
                .with("bind", ElfConstants.bindName(ref.getBind()))
                .with("copy", ref.isCopyRelocation());

        List<ProviderIndex.Provider> matched = matchingProviders(ref, index);

        // Build candidate views and filter by COPY/protected and visibility rules.
        List<ProviderIndex.Provider> usable = new ArrayList<>();
        for (ProviderIndex.Provider p : matched) {
            Candidate c = p.candidate;
            rec.getCandidates().add(c);
            String reject = rejectionFor(ref, p, policy);
            if (reject != null) {
                c.eliminate(reject);
                emit(result, ResolveEvent.Type.CANDIDATE_ELIMINATED, "淘汰候选",
                        c.getUnitDisplayName() + " — " + reject)
                        .with("referenceId", ref.getReferenceId())
                        .with("unitId", c.getUnitId())
                        .with("reason", reject);
            } else {
                c.win(); // tentative; narrowed below
                usable.add(p);
            }
        }

        // Mark candidates that never appeared (wrong version) as eliminated for transparency.
        if (ref.getKey().getVersion() != null) {
            for (ProviderIndex.Provider p : index.byName(ref.getSymbolName())) {
                ElfSymbol s = p.symbol;
                boolean inMatched = matched.contains(p);
                if (!inMatched) {
                    Candidate c = p.candidate;
                    rec.getCandidates().add(c);
                    String reason = (s.getVersionName() == null)
                            ? "版本不匹配：提供者未带版本，而引用要求 " + ref.getKey().getVersion()
                            : "版本不匹配：提供版本 " + s.getVersionName() + " ≠ 要求 "
                                    + ref.getKey().getVersion();
                    c.eliminate(reason);
                    emit(result, ResolveEvent.Type.CANDIDATE_ELIMINATED, "淘汰候选（版本）",
                            c.getUnitDisplayName() + " — " + reason)
                            .with("referenceId", ref.getReferenceId())
                            .with("unitId", c.getUnitId())
                            .with("reason", reason);
                }
            }
        }

        // ---- Version-unmet: name exists somewhere but no required version provider ----
        boolean anyName = !index.byName(ref.getSymbolName()).isEmpty();

        if (usable.isEmpty()) {
            handleNoProvider(ref, rec, result, anyName);
        } else {
            chooseAmongUsable(ref, rec, usable, result, policy);
        }
        attachRelocations(rec, ref);
        result.getRecords().add(rec);
    }

    private String rejectionFor(Reference ref, ProviderIndex.Provider p, Policy policy) {
        Candidate c = p.candidate;
        // A producer's own protected symbol cannot be preempted by an external definition;
        // references originating in that DSO must bind locally.
        if (c.getVisibility() == ElfConstants.STV_PROTECTED
                && policy.protectedNonInterposableProducer()
                && !ref.getUnitId().equals(c.getUnitId())) {
            // From outside, a protected symbol is still a valid dynamic provider for calls in
            // the GNU model; only a COPY against protected data is illegal. Handled below.
        }
        if (ref.isCopyRelocation() && c.getVisibility() == ElfConstants.STV_PROTECTED
                && !policy.allowCopyAgainstProtected()) {
            return "COPY relocation 不能绑定 protected 数据（受保护符号不可被拷贝拦截）";
        }
        if (ref.isCopyRelocation() && c.getType() != ElfConstants.STT_OBJECT
                && c.getType() != ElfConstants.STT_NOTYPE && c.getType() != ElfConstants.STT_TLS) {
            return "COPY relocation 只能针对数据对象（提供者类型 "
                    + ElfConstants.typeName(c.getType()) + "）";
        }
        // Hidden/local symbols never cross modules; should not be indexed, guard anyway.
        if (c.getVisibility() == ElfConstants.STV_HIDDEN
                && !ref.getUnitId().equals(c.getUnitId())) {
            return "hidden 符号不导出，无法满足跨对象引用";
        }
        return null;
    }

    private void handleNoProvider(Reference ref, ResolutionRecord rec,
                                  ResolutionResult result, boolean anyName) {
        ResolutionStatus status;
        String explanation;
        boolean copyBlocked = rec.getCandidates().stream().anyMatch(c ->
                c.getStatus() == Candidate.Status.ELIMINATED
                        && c.getEliminationReason() != null
                        && c.getEliminationReason().startsWith("COPY relocation 不能绑定 protected"));
        if (copyBlocked) {
            status = ResolutionStatus.COPY_PROTECTED;
            explanation = "COPY relocation 仅有的候选是 protected 数据，无法合法绑定";
        } else if (ref.getKey().getVersion() != null && anyName) {
            status = ResolutionStatus.VERSION_UNMET;
            explanation = "存在同名符号，但没有对象提供要求的版本 " + ref.getKey().getVersion();
        } else if (ref.getBind() == ElfConstants.STB_WEAK) {
            status = ResolutionStatus.RESOLVED_WEAK;
            explanation = "弱引用无提供者，按 0 保留（不报错）";
        } else {
            status = ResolutionStatus.UNRESOLVED;
            explanation = "强引用 '" + ref.getSymbolName() + "' 没有任何提供者";
        }
        rec.setStatus(status);
        rec.setExplanation(explanation);
        emit(result, ResolveEvent.Type.VERDICT, "裁决", explanation)
                .with("referenceId", ref.getReferenceId())
                .with("status", status.name());
    }

    private void chooseAmongUsable(Reference ref, ResolutionRecord rec,
                                   List<ProviderIndex.Provider> usable,
                                   ResolutionResult result, Policy policy) {
        // Rank order: strong > common > weak. Common merges toward the largest common.
        Candidate.Rank bestRank = usable.stream().map(p -> p.candidate.getRank())
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElseThrow();

        List<ProviderIndex.Provider> atBest = new ArrayList<>();
        for (ProviderIndex.Provider p : usable) {
            if (p.candidate.getRank() == bestRank) {
                atBest.add(p);
            } else {
                Candidate c = p.candidate;
                String why = lowerRankReason(bestRank, c.getRank());
                // candidate was tentatively WON; convert to eliminated
                c.eliminate(why);
                emit(result, ResolveEvent.Type.CANDIDATE_ELIMINATED, "淘汰候选（强度）",
                        c.getUnitDisplayName() + " — " + why)
                        .with("referenceId", ref.getReferenceId())
                        .with("unitId", c.getUnitId())
                        .with("reason", why);
            }
        }

        // Common merge: multiple common symbols of different sizes collapse onto the largest;
        // equal-sized commons are an ambiguity.
        if (bestRank == Candidate.Rank.COMMON) {
            long maxSize = atBest.stream().mapToLong(p -> p.candidate.getCommonSize()).max().orElse(0);
            List<ProviderIndex.Provider> biggest = atBest.stream()
                    .filter(p -> p.candidate.getCommonSize() == maxSize).toList();
            for (ProviderIndex.Provider p : atBest) {
                if (p.candidate.getCommonSize() < maxSize) {
                    p.candidate.eliminate("common 合并：对齐到更大的 common（"
                            + p.candidate.getCommonSize() + " < " + maxSize + " 字节）");
                }
            }
            atBest = new ArrayList<>(biggest);
        }

        boolean linkTimeConflict = atBest.stream().allMatch(p ->
                p.unit.getKind() != com.symbolcourt.model.InputEntry.Kind.SHARED);

        if (atBest.size() == 1) {
            ProviderIndex.Provider win = atBest.get(0);
            win.candidate.win();
            rec.setWinner(win.candidate);
            rec.setStatus(ResolutionStatus.RESOLVED);
            String explain = (bestRank == Candidate.Rank.COMMON)
                    ? "选择 common 提供者（将分配公共块）：" + win.unit.getDisplayName()
                    : "选择最强且最先载入的提供者：" + win.unit.getDisplayName();
            rec.setExplanation(explain);
            emit(result, ResolveEvent.Type.WINNER_SELECTED, "选定提供者",
                            win.unit.getDisplayName() + " 提供 " + ref.getSymbolName())
                    .with("referenceId", ref.getReferenceId())
                    .with("unitId", win.unit.getUnitId())
                    .with("rank", bestRank.name());
            emit(result, ResolveEvent.Type.VERDICT, "裁决", explain)
                    .with("referenceId", ref.getReferenceId())
                    .with("status", ResolutionStatus.RESOLVED.name());
            return;
        }

        // Multiple equally ranked providers.
        boolean duplicateStrong = bestRank == Candidate.Rank.STRONG_DEFINITION && linkTimeConflict;
        if (duplicateStrong && !policy.allowMultipleDefinition()
                && policy.tiePolicy() == Policy.TiePolicy.AMBIGUOUS) {
            for (ProviderIndex.Provider p : atBest) {
                p.candidate.tie();
            }
            rec.setStatus(ResolutionStatus.CONFLICT);
            String list = atBest.stream().map(p -> p.unit.getDisplayName()).toList().toString();
            rec.setExplanation("重复强定义冲突：多个同等 strong 提供者 " + list
                    + "，按平台策略不允许多重定义");
            emit(result, ResolveEvent.Type.VERDICT, "裁决（冲突）", rec.getExplanation())
                    .with("referenceId", ref.getReferenceId())
                    .with("status", ResolutionStatus.CONFLICT.name());
        } else if (policy.tiePolicy() == Policy.TiePolicy.AMBIGUOUS) {
            for (ProviderIndex.Provider p : atBest) {
                p.candidate.tie();
            }
            rec.setStatus(ResolutionStatus.AMBIGUOUS);
            String list = atBest.stream().map(p -> p.unit.getDisplayName()).toList().toString();
            rec.setExplanation("存在 " + atBest.size() + " 个同等候选 " + list
                    + "，保留歧义，不以文件遍历次序裁决");
            emit(result, ResolveEvent.Type.VERDICT, "裁决（歧义）", rec.getExplanation())
                    .with("referenceId", ref.getReferenceId())
                    .with("status", ResolutionStatus.AMBIGUOUS.name());
        } else {
            // FIRST tie-break: explicit deterministic load order, never FS traversal.
            atBest.sort(Comparator.comparingInt(p -> p.candidate.getLoadIndex()));
            ProviderIndex.Provider win = atBest.get(0);
            win.candidate.win();
            for (int i = 1; i < atBest.size(); i++) {
                ProviderIndex.Provider p = atBest.get(i);
                p.candidate.eliminate("平台策略=显式载入次序优先，"
                        + win.unit.getDisplayName() + " 的载入序更早");
            }
            rec.setWinner(win.candidate);
            rec.setStatus(ResolutionStatus.RESOLVED);
            rec.setExplanation("按平台策略以显式载入次序选定：" + win.unit.getDisplayName());
            if (duplicateStrong && policy.allowMultipleDefinition()) {
                rec.getNotes().add("--allow-multiple-definition：已抑制重复强定义冲突");
            }
            emit(result, ResolveEvent.Type.WINNER_SELECTED, "选定提供者（次序策略）",
                            win.unit.getDisplayName())
                    .with("referenceId", ref.getReferenceId())
                    .with("unitId", win.unit.getUnitId());
            emit(result, ResolveEvent.Type.VERDICT, "裁决", rec.getExplanation())
                    .with("referenceId", ref.getReferenceId())
                    .with("status", ResolutionStatus.RESOLVED.name());
        }
    }

    private String lowerRankReason(Candidate.Rank best, Candidate.Rank mine) {
        if (best == Candidate.Rank.STRONG_DEFINITION && mine == Candidate.Rank.COMMON) {
            return "strong 定义优先于 common 符号";
        }
        if (best == Candidate.Rank.STRONG_DEFINITION && mine == Candidate.Rank.WEAK_DEFINITION) {
            return "strong 定义覆盖 weak 定义";
        }
        if (best == Candidate.Rank.COMMON && mine == Candidate.Rank.WEAK_DEFINITION) {
            return "common 定义优先于 weak 定义";
        }
        return "定义强度低于中选候选（" + mine + " < " + best + "）";
    }

    private void attachRelocations(ResolutionRecord rec, Reference ref) {
        String base = ref.getSectionName() == null
                ? "(符号表引用)"
                : ref.getSectionName() + "+0x" + Long.toHexString(ref.getOffset());
        String type = ref.getRelocType() >= 0 ? " type=" + ref.getRelocType() : "";
        String copy = ref.isCopyRelocation() ? " [COPY]" : "";
        rec.getAffectedRelocations().add(base + type + copy
                + (ref.getAddend() != 0 ? " addend=" + ref.getAddend() : ""));
    }

    // ------------------------------------------------------------------
    // Version graph + final symbol table snapshot
    // ------------------------------------------------------------------

    private void buildVersionGraph(Loader.LoadedInput loaded, ResolutionResult result) {
        int nodeSeq = 0;
        for (LoadUnit u : result.getUnits()) {
            if (!u.isParseOk()) {
                continue;
            }
            ElfFile elf = u.getElf();
            String objId = "u:" + u.getUnitId();
            ResolutionResult.VersionGraphNode obj = node(result, objId,
                    u.getDisplayName(), elf.getType() == ElfConstants.ET_REL ? "object"
                            : elf.getType() == ElfConstants.ET_DYN ? "shared" : "exec");
            obj.getMeta().put("unitId", u.getUnitId());
            for (String need : elf.getNeeded()) {
                String depId = "dep:" + need;
                node(result, depId, need, "dependency");
                obj.getEdges().add(depId);
            }
            for (var vd : elf.getVersionDefinitions()) {
                String id = "verdef:" + u.getUnitId() + ":" + vd.getIndex();
                ResolutionResult.VersionGraphNode n = node(result, id, vd.getName(),
                        vd.isBase() ? "version-base" : "version-def");
                n.getMeta().put("index", vd.getIndex());
                n.getMeta().put("aliases", vd.getAliases());
                obj.getEdges().add(id);
            }
            for (var vr : elf.getVersionRequirements()) {
                String reqId = "verneed:" + u.getUnitId() + ":" + vr.getFileName();
                ResolutionResult.VersionGraphNode n = node(result, reqId,
                        vr.getFileName() + " (需求)", "version-need");
                obj.getEdges().add(reqId);
                for (var nd : vr.getNodes()) {
                    String ni = reqId + ":" + nd.getIndex();
                    ResolutionResult.VersionGraphNode vn = node(result, ni, nd.getName(),
                            nd.isWeak() ? "version-node-weak" : "version-node");
                    vn.getMeta().put("index", nd.getIndex());
                    n.getEdges().add(ni);
                }
            }
        }
    }

    private ResolutionResult.VersionGraphNode node(ResolutionResult result, String id,
                                                   String label, String kind) {
        return result.getVersionGraph().computeIfAbsent(id,
                k -> new ResolutionResult.VersionGraphNode(k, label, kind));
    }

    private void buildFinalSymbolTable(ProviderIndex index, ResolutionResult result) {
        Map<String, Object> table = result.getSymbolTableSnapshot();
        List<Map.Entry<SymbolKey, List<ProviderIndex.Provider>>> entries = new ArrayList<>(
                index.all().entrySet());
        entries.sort(Map.Entry.comparingByKey(Comparator
                .comparing(SymbolKey::getName).thenComparing(k ->
                        k.getVersion() == null ? "" : k.getVersion())));
        for (var e : entries) {
            List<Object> providers = new ArrayList<>();
            for (ProviderIndex.Provider p : e.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("unitId", p.unit.getUnitId());
                m.put("displayName", p.unit.getDisplayName());
                m.put("bind", ElfConstants.bindName(p.symbol.getBind()));
                m.put("visibility", ElfConstants.visibilityName(p.symbol.getVisibility()));
                m.put("version", p.symbol.getVersionName());
                m.put("default", p.symbol.isDefaultVersion());
                m.put("common", p.symbol.isCommon());
                m.put("address", p.symbol.getValue());
                providers.add(m);
            }
            table.put(e.getKey().getName()
                    + (e.getKey().getVersion() == null ? "" : "@" + e.getKey().getVersion()),
                    providers);
        }
    }

    private ResolveEvent emit(ResolutionResult result, ResolveEvent.Type type, String title,
                              String detail) {
        ResolveEvent e = new ResolveEvent(eventSeq++, type, title, detail);
        result.getEvents().add(e);
        return e;
    }
}
