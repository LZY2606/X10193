package com.court.resolution;

import com.court.elf.ArchiveParser;
import com.court.elf.ElfParseException;
import com.court.elf.ElfParser;
import com.court.model.ElfFile;
import com.court.model.ElfSection;
import com.court.model.ElfSymbol;
import com.court.model.Relocation;
import com.court.model.SectionGroup;
import com.court.model.VersionDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The core of the symbol court.
 *
 * <p>Input objects are parsed and validated in explicit load order. A corrupt
 * object is quarantined alone and does not poison the rest. Archive members
 * are extracted lazily: only when a currently-undefined symbol is advertised
 * by an archive symbol table. The fixed-point loop naturally resolves cyclic
 * archive dependencies.
 *
 * <p>For every undefined reference the engine lists every candidate provider,
 * the reason each loser was eliminated, and the final verdict. Equal-rank
 * candidates stay AMBIGUOUS under the default policy - no filesystem or
 * traversal order is used to manufacture a winner.
 */
public class ResolverEngine {

    private final Policies policies;
    private ResolutionResult result;
    private int traceSeq;
    private final List<ArchiveIndex> archives = new ArrayList<>();
    private final Map<ElfFile, Integer> loadOrderOf = new LinkedHashMap<>();
    private final Set<String> extractedMemberIds = new LinkedHashSet<>();

    public ResolverEngine(Policies policies) {
        this.policies = policies == null ? Policies.defaults() : policies;
    }

    public ResolutionResult adjudicate(String planName, String planVersion, List<LoadEntry> entries) {
        this.result = new ResolutionResult();
        result.setPlanName(planName);
        result.setPlanVersion(planVersion);
        result.setPolicies(policies);
        this.traceSeq = 0;
        this.archives.clear();
        this.loadOrderOf.clear();
        this.extractedMemberIds.clear();

        TraceEvent policyEvent = emit(TraceEvent.Kind.POLICY_APPLIED,
                "平台策略: " + policies.signature());
        policyEvent.detail.put("tiePolicy", policies.tiePolicy.name());

        // Phase 1: parse explicit inputs; archives are indexed but not extracted.
        List<ElfFile> initialFiles = new ArrayList<>();
        for (LoadEntry e : entries) {
            if (e.kind == LoadEntry.Kind.ARCHIVE) {
                try {
                    ArchiveIndex idx = new ArchiveIndex(e.label, e.order, e.content);
                    archives.add(idx);
                    TraceEvent te = emit(TraceEvent.Kind.INPUT_PARSED,
                            "归档已索引(惰性): " + e.label);
                    te.detail.put("members", idx.getEntries().size());
                } catch (ElfParseException ex) {
                    quarantine(e.label, ex.getMessage());
                }
            } else {
                ElfFile f = parseOne(e.label, null, e.content);
                if (f != null) {
                    f.setRole(f.isDynamic()
                            ? (f.isExecutable() ? ElfFile.Role.EXECUTABLE : ElfFile.Role.SHARED)
                            : ElfFile.Role.INPUT_OBJECT);
                    f.setActive(true);
                    initialFiles.add(f);
                    loadOrderOf.put(f, e.order);
                    result.getActiveFiles().add(f);
                }
            }
        }

        // Phase 2: COMDAT de-duplication across currently loaded relocatable files.
        dedupeComdatGroups(initialFiles);

        // Phase 3: collect references, then iterate archive extraction + verdicts.
        Map<ElfFile, List<Reference>> refsByFile = new LinkedHashMap<>();
        for (ElfFile f : initialFiles) refsByFile.put(f, collectReferences(f));

        boolean changed = true;
        int round = 0;
        while (changed) {
            changed = false;
            round++;
            // Resolve what we can with the current provider universe.
            List<ElfFile> universe = new ArrayList<>(result.getActiveFiles());
            Map<String, List<Provider>> providers = buildProviderTable(universe);
            mergeCommons(providers);

            List<Resolution> roundResolutions = new ArrayList<>();
            for (ElfFile f : universe) {
                List<Reference> refs = refsByFile.computeIfAbsent(f, k -> collectReferences(f));
                for (Reference ref : refs) {
                    Resolution res = adjudicateReference(f, ref, providers, universe);
                    roundResolutions.add(res);
                }
            }

            // Determine names still unsatisfiable that some archive might provide.
            Set<String> neededNames = new LinkedHashSet<>();
            Map<String, Boolean> neededWeak = new LinkedHashMap<>();
            Map<String, Boolean> neededVersioned = new LinkedHashMap<>();
            for (Resolution r : roundResolutions) {
                if (r.getStatus() == Resolution.Status.UNRESOLVED
                        || r.getStatus() == Resolution.Status.VERSION_UNSATISFIED
                        || r.getStatus() == Resolution.Status.WEAK_UNRESOLVED) {
                    Reference ref = r.getReference();
                    if (ref.weakReference && !policies.extractForWeak) continue;
                    neededNames.add(ref.name());
                    neededWeak.put(ref.name(), ref.weakReference);
                    neededVersioned.put(ref.name(), ref.requiredVersion != null);
                }
            }

            // Ask archives in explicit load order.
            for (String name : neededNames) {
                for (ArchiveIndex ar : archives) {
                    List<Integer> candidates = ar.providersFor(name);
                    if (candidates.isEmpty()) continue;
                    TraceEvent cons = emit(TraceEvent.Kind.ARCHIVE_CONSIDERED,
                            "归档 " + ar.getLabel() + " 可提供 " + name);
                    cons.detail.put("archive", ar.getLabel());
                    cons.detail.put("symbol", name);
                    cons.detail.put("members", candidateNames(ar, candidates));
                    for (Integer mi : candidates) {
                        ArchiveIndex.Entry en = ar.entryAt(mi);
                        String memberId = ar.getLabel() + "(" + en.memberName + ")";
                        if (en.extracted) continue;
                        ElfFile member = parseOne(ar.getLabel(), en.memberName, en.member.getData());
                        if (member == null) continue;
                        member.setRole(ElfFile.Role.EXTRACTED_OBJECT);
                        member.setActive(true);
                        en.extracted = true;
                        extractedMemberIds.add(memberId);
                        loadOrderOf.put(member, ar.getLoadOrder());
                        result.getActiveFiles().add(member);
                        refsByFile.put(member, collectReferences(member));
                        TraceEvent ex = emit(TraceEvent.Kind.ARCHIVE_EXTRACTED,
                                "惰性抽取 " + memberId + " (第 " + round + " 轮, 为 " + name + ")");
                        ex.detail.put("archive", ar.getLabel());
                        ex.detail.put("member", en.memberName);
                        ex.detail.put("round", round);
                        ex.detail.put("trigger", name);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                // Snapshot final resolutions.
                result.getResolutions().clear();
                result.getResolutions().addAll(roundResolutions);
            }
            if (round > 10000) throw new IllegalStateException("archive extraction did not converge");
        }

        // Phase 4: final layout (addresses) and derived graphs.
        assignAddresses();
        for (Resolution r : result.getResolutions()) {
            if (r.getWinner() != null) r.setResolvedAddress(r.getWinner().assignedAddress);
        }
        buildDependencyGraph();
        buildVersionGraph();
        snapshotSymbolTable();
        return result;
    }

    private ElfFile parseOne(String label, String member, byte[] data) {
        try {
            ElfFile f = ElfParser.parse(data, label, member);
            TraceEvent te = emit(TraceEvent.Kind.INPUT_PARSED,
                    "已解析: " + f.id());
            te.detail.put("file", f.id());
            te.detail.put("type", f.isRelocatable() ? "ET_REL"
                    : f.isExecutable() ? "ET_EXEC" : "ET_DYN");
            te.detail.put("symbols", f.getStaticSymbols().size() + f.getDynamicSymbols().size());
            te.detail.put("relocations", f.getRelocations().size());
            return f;
        } catch (ElfParseException | RuntimeException ex) {
            quarantine(member == null ? label : label + "(" + member + ")", ex.getMessage());
            return null;
        }
    }

    private void quarantine(String label, String reason) {
        result.getQuarantined().add(new Quarantine(label, reason));
        TraceEvent te = emit(TraceEvent.Kind.INPUT_QUARANTINED,
                "已隔离损坏对象: " + label);
        te.detail.put("file", label);
        te.detail.put("reason", reason);
    }

    private TraceEvent emit(TraceEvent.Kind kind, String summary) {
        TraceEvent e = new TraceEvent(traceSeq++, kind, summary);
        result.getTimeline().add(e);
        return e;
    }

    private List<String> candidateNames(ArchiveIndex ar, List<Integer> idxs) {
        List<String> out = new ArrayList<>();
        for (Integer i : idxs) out.add(ar.entryAt(i).memberName);
        return out;
    }

    // ---------- COMDAT ----------

    private void dedupeComdatGroups(List<ElfFile> files) {
        if (!policies.dedupeComdat) return;
        Map<String, String> winnerBySignature = new LinkedHashMap<>();
        for (ElfFile f : files) {
            if (!f.isRelocatable()) continue;
            for (SectionGroup g : f.getGroups()) {
                if (!g.comdat()) continue;
                String sig = g.signature();
                String winnerId = winnerBySignature.get(sig);
                if (winnerId == null) {
                    winnerBySignature.put(sig, f.id());
                    continue;
                }
                // Drop this group's member sections: remove symbols defined in them
                // and relocations applied to them.
                Set<Integer> droppedSections = new LinkedHashSet<>(g.sectionIndices());
                List<ElfSymbol> kept = new ArrayList<>();
                for (ElfSymbol s : f.getStaticSymbols()) {
                    if (s.getShndx() > 0 && s.getShndx() < f.getSections().size()
                            && droppedSections.contains(s.getShndx())) {
                        f.getComdatDiscardedSymbols().add(s);
                    } else {
                        kept.add(s);
                    }
                }
                f.getStaticSymbols().clear();
                f.getStaticSymbols().addAll(kept);
                List<Relocation> keptRel = new ArrayList<>();
                for (Relocation r : f.getRelocations()) {
                    if (droppedSections.contains(r.sectionIndex())) {
                        f.getComdatDiscardedRelocs().add(r);
                    } else {
                        keptRel.add(r);
                    }
                }
                f.getRelocations().clear();
                f.getRelocations().addAll(keptRel);
                TraceEvent te = emit(TraceEvent.Kind.COMDAT_DEDUPED,
                        "COMDAT 去重: 丢弃 " + f.id() + " 的组 @" + sig
                                + " (保留 " + winnerId + ")");
                te.detail.put("signature", sig);
                te.detail.put("keptBy", winnerId);
                te.detail.put("droppedBy", f.id());
                te.detail.put("droppedSections", new ArrayList<>(droppedSections));
            }
        }
    }

    // ---------- References ----------

    private List<Reference> collectReferences(ElfFile f) {
        List<Reference> refs = new ArrayList<>();
        if (f.isRelocatable()) {
            List<ElfSymbol> syms = f.getStaticSymbols();
            for (Relocation r : f.getRelocations()) {
                if (r.symbolIndex() <= 0 || r.symbolIndex() >= syms.size()) continue;
                ElfSymbol s = syms.get(r.symbolIndex());
                if (s.getName().isEmpty() || !s.isUndefined()) continue;
                refs.add(new Reference(f, s, null, false, r, s.isWeak()));
            }
        } else {
            List<ElfSymbol> syms = f.getDynamicSymbols();
            for (int i = 0; i < syms.size(); i++) {
                ElfSymbol s = syms.get(i);
                if (s.getName().isEmpty() || !s.isUndefined()) continue;
                Integer vi = i < f.getDynVersym().size() ? f.getDynVersym().get(i) : 0;
                VersionLookup.VersionView vv = vi == null ? null : VersionLookup.resolve(f, vi);
                Relocation synthetic = findDynamicRelocation(f, s);
                refs.add(new Reference(f, s,
                        vv == null ? null : vv.name(),
                        vv != null && vv.nonDefault(),
                        synthetic, s.isWeak()));
            }
        }
        return refs;
    }

    private Relocation findDynamicRelocation(ElfFile f, ElfSymbol target) {
        for (Relocation r : f.getRelocations()) {
            if (r.symbolIndex() == target.getIndex()
                    && (r.type() == Relocation.R_X86_64_COPY
                        || r.type() == Relocation.R_X86_64_JUMP_SLOT
                        || r.type() == Relocation.R_X86_64_GLOB_DAT
                        || r.type() == Relocation.R_X86_64_64)) {
                return r;
            }
        }
        return new Relocation(-1, 0, target.getIndex(), target.getName(),
                Relocation.DYNREF, null, false, true);
    }

    // ---------- Provider table ----------

    private Map<String, List<Provider>> buildProviderTable(List<ElfFile> files) {
        Map<String, List<Provider>> table = new LinkedHashMap<>();
        for (ElfFile f : files) {
            int order = loadOrderOf.getOrDefault(f, Integer.MAX_VALUE);
            int round = f.getRole() == ElfFile.Role.EXTRACTED_OBJECT ? 1 : 0;
            if (f.isRelocatable()) {
                for (ElfSymbol s : f.getStaticSymbols()) {
                    addProvider(table, f, s, null, false, order, round);
                }
            } else {
                List<ElfSymbol> dyn = f.getDynamicSymbols();
                for (int i = 0; i < dyn.size(); i++) {
                    ElfSymbol s = dyn.get(i);
                    Integer raw = i < f.getDynVersym().size() ? f.getDynVersym().get(i) : 0;
                    VersionLookup.VersionView vv = raw == null ? null : VersionLookup.resolve(f, raw);
                    addProvider(table, f, s,
                            vv == null ? null : vv.name(),
                            vv != null && vv.nonDefault(), order, round);
                }
            }
        }
        return table;
    }

    private void addProvider(Map<String, List<Provider>> table, ElfFile f, ElfSymbol s,
                             String version, boolean nonDefault, int order, int round) {
        if (s.getName().isEmpty()) return;
        if (s.isUndefined()) return;
        if (s.isLocal()) return;
        Provider p = new Provider(f, s, version, nonDefault, order, round);
        table.computeIfAbsent(s.getName(), k -> new ArrayList<>()).add(p);
    }

    // ---------- Common symbols ----------

    private void mergeCommons(Map<String, List<Provider>> table) {
        for (Map.Entry<String, List<Provider>> e : new ArrayList<>(table.entrySet())) {
            List<Provider> list = e.getValue();
            boolean hasStrong = list.stream().anyMatch(this::isStrongDef);
            List<Provider> commons = new ArrayList<>();
            for (Provider p : list) if (isCommon(p)) commons.add(p);
            if (commons.isEmpty()) continue;
            if (hasStrong) {
                for (Provider p : commons) {
                    TraceEvent te = emit(TraceEvent.Kind.CANDIDATE_REJECTED,
                            "COMMON " + labelOf(p) + " 被强定义吸收: " + e.getKey());
                    te.detail.put("symbol", e.getKey());
                    te.detail.put("provider", labelOf(p));
                    te.detail.put("reason", "common loses to strong definition");
                }
                list.removeIf(this::isCommon);
            } else if (policies.commonCoalesce && commons.size() > 1) {
                Provider biggest = pickCommon(commons);
                List<Provider> losers = new ArrayList<>(commons);
                losers.remove(biggest);
                for (Provider p : losers) {
                    TraceEvent te = emit(TraceEvent.Kind.COMMON_MERGED,
                            "COMMON 合并: " + e.getKey() + " <- " + labelOf(p)
                                    + " 并入 " + labelOf(biggest));
                    te.detail.put("symbol", e.getKey());
                    te.detail.put("kept", labelOf(biggest));
                    te.detail.put("merged", labelOf(p));
                }
                list.removeIf(losers::contains);
            }
        }
    }

    private boolean isCommon(Provider p) {
        return p.symbol.isCommon()
                || p.symbol.getType() == ElfSymbol.STT_COMMON;
    }

    private boolean isStrongDef(Provider p) {
        if (isCommon(p)) return false;
        return p.symbol.isGlobal()
                && p.symbol.getVisibility() != ElfSymbol.STV_HIDDEN
                && p.symbol.getVisibility() != ElfSymbol.STV_INTERNAL;
    }

    private Provider pickCommon(List<Provider> commons) {
        // Largest size wins; ties are broken by explicit load order, then member
        // order within an archive - never by directory iteration.
        return commons.stream().min(Comparator
                .comparingLong((Provider p) -> -p.symbol.getSize())
                .thenComparingInt(p -> p.loadOrder)
                .thenComparingInt(p -> p.extractionRound)
                .thenComparing(p -> p.file.getMemberName() == null
                        ? "" : p.file.getMemberName())).orElse(commons.get(0));
    }

    private String labelOf(Provider p) {
        return p.file.id() + "#" + p.symbol.getName()
                + (p.versionName == null ? "" : (p.nonDefaultVersion ? "@" : "@@") + p.versionName);
    }

    // ---------- Adjudication of one reference ----------

    private Resolution adjudicateReference(ElfFile source, Reference ref,
                                           Map<String, List<Provider>> table,
                                           List<ElfFile> universe) {
        Resolution res = new Resolution(ref);
        String name = ref.name();

        // Local/self bind inside the same file (static symbols resolving locally).
        for (ElfSymbol s : source.isRelocatable() ? source.getStaticSymbols()
                                                 : source.getDynamicSymbols()) {
            if (!s.isUndefined() && !s.isLocal() && s.getName().equals(name)) {
                Provider self = new Provider(source, s, null, false,
                        loadOrderOf.getOrDefault(source, 0), 0);
                res.getCandidates().add(self);
            }
        }

        List<Provider> raw = table.getOrDefault(name, new ArrayList<>());
        boolean versionRequired = ref.requiredVersion != null;
        boolean sawSameName = !raw.isEmpty();

        for (Provider p : raw) {
            // Local symbols never serve another object.
            if (p.symbol.isLocal()) {
                reject(res, p, "LOCAL 绑定不对外导出");
                continue;
            }
            // HIDDEN / INTERNAL are not visible to other objects.
            if (p.symbol.getVisibility() == ElfSymbol.STV_HIDDEN
                    || p.symbol.getVisibility() == ElfSymbol.STV_INTERNAL) {
                if (p.file != source) {
                    reject(res, p, p.symbol.visibilityName() + " 符号对其它对象不可见");
                    continue;
                }
            }
            // Version matching: same name is not the same candidate.
            if (versionRequired) {
                if (p.versionName == null) {
                    reject(res, p, "引用要求版本 " + decorated(ref.requiredVersion, ref.nonDefaultVersion)
                            + "，提供者未版本化");
                    continue;
                }
                if (!p.versionName.equals(ref.requiredVersion)) {
                    reject(res, p, "版本不匹配: 提供者为 "
                            + decorated(p.versionName, p.nonDefaultVersion)
                            + "，引用要求 " + decorated(ref.requiredVersion, ref.nonDefaultVersion));
                    continue;
                }
                if (p.nonDefaultVersion != ref.nonDefaultVersion) {
                    reject(res, p, "默认/非默认版本形态不一致: "
                            + decorated(p.versionName, p.nonDefaultVersion));
                    continue;
                }
            } else {
                // Unversioned reference binds to the default definition only; a
                // non-default (hidden) alias cannot be reached by an unversioned ref.
                if (p.versionName != null && p.nonDefaultVersion) {
                    reject(res, p, "非默认版本别名 " + p.versionName
                            + " 不响应未版本化引用");
                    continue;
                }
            }
            // Protected symbols cannot be interposed: executable cannot preempt one.
            if (policies.protectedNoInterposition
                    && p.symbol.getVisibility() == ElfSymbol.STV_PROTECTED
                    && source != p.file && source.isExecutable()) {
                reject(res, p, "PROTECTED 符号禁止来自可执行文件的介入(interposition)");
                continue;
            }
            res.getCandidates().add(p);
        }

        // Deduplicate exact (file, symbolIndex, version) candidates that appear
        // from both symtab and dynsym views.
        List<Provider> cands = dedupe(res.getCandidates());
        res.getCandidates().clear();
        res.getCandidates().addAll(cands);

        // COPY relocation: an executable's dynamic COPY reloc against an
        // OBJECT symbol defined in a DSO.
        Relocation rel = ref.relocation;
        boolean copyRequest = rel != null && !rel.dynamicRef()
                && rel.type() == Relocation.R_X86_64_COPY && source.isExecutable();
        if (copyRequest && policies.allowCopyRelocation) {
            Provider dsoData = cands.stream()
                    .filter(p -> p.file.isShared())
                    .filter(p -> p.symbol.getType() == ElfSymbol.STT_OBJECT)
                    .findFirst().orElse(null);
            if (dsoData != null) {
                res.setStatus(Resolution.Status.COPY_RELOCATION);
                res.setWinner(dsoData);
                res.setNote("COPY relocation: 在可执行文件内复制数据对象，"
                        + "DSO 内部引用必须改指向该副本（不能被普通覆盖规则吞掉）");
                TraceEvent te = emit(TraceEvent.Kind.COPY_RELOCATION_CREATED,
                        "COPY relocation 绑定 " + name + " -> " + labelOf(dsoData));
                te.detail.put("symbol", name);
                te.detail.put("provider", labelOf(dsoData));
                attachRelocations(res, source, name);
                return res;
            }
        }

        if (cands.isEmpty()) {
            if (versionRequired && sawSameName) {
                res.setStatus(Resolution.Status.VERSION_UNSATISFIED);
                res.setNote("同名符号存在，但没有任何定义满足版本 "
                        + decorated(ref.requiredVersion, ref.nonDefaultVersion));
            } else if (ref.weakReference) {
                res.setStatus(Resolution.Status.WEAK_UNRESOLVED);
                res.setNote("弱引用未满足，按 0 地址绑定（不导致失败）");
            } else {
                res.setStatus(Resolution.Status.UNRESOLVED);
                res.setNote("无可用定义" + (versionRequired
                        ? "（要求版本 " + decorated(ref.requiredVersion, ref.nonDefaultVersion) + "）" : ""));
            }
            attachRelocations(res, source, name);
            emitVerdict(res);
            return res;
        }

        // Rank: strong > weak; COMMON only beats undefined, loses to strong/weak defs.
        List<Provider> ranked = rank(cands);
        Provider best = ranked.get(0);
        List<Provider> ties = new ArrayList<>();
        for (Provider p : ranked) {
            if (compareRank(p, best) == 0 && !sameProvider(p, best)) ties.add(p);
        }

        if (!ties.isEmpty()) {
            ties.add(0, best);
            if (policies.tiePolicy == Policies.TiePolicy.KEEP_AMBIGUOUS) {
                res.setStatus(Resolution.Status.AMBIGUOUS);
                res.getTiedWinners().addAll(ties);
                res.setNote("存在 " + ties.size() + " 个同等级候选；保留歧义，"
                        + "不以文件系统遍历次序强行选择");
                for (Provider p : ties) {
                    TraceEvent te = emit(TraceEvent.Kind.CANDIDATE_REJECTED,
                            "同等级候选(未裁决): " + labelOf(p));
                    te.detail.put("symbol", name);
                    te.detail.put("provider", labelOf(p));
                }
            } else if (policies.tiePolicy == Policies.TiePolicy.LOAD_ORDER) {
                Provider picked = ties.stream()
                        .min(Comparator.comparingInt((Provider p) -> p.loadOrder)
                                .thenComparingInt(p -> p.extractionRound)
                                .thenComparing(p -> p.file.getMemberName() == null
                                        ? "" : p.file.getMemberName())).orElse(best);
                res.setStatus(Resolution.Status.RESOLVED);
                res.setWinner(picked);
                res.setNote("按显式加载顺序裁定平局 -> " + picked.file.id());
                for (Provider p : ties) {
                    if (!sameProvider(p, picked)) reject(res, p, "加载顺序靠后（平局裁定）");
                }
            } else {
                res.setStatus(Resolution.Status.AMBIGUOUS);
                res.getTiedWinners().addAll(ties);
                res.setNote("REJECT 策略：同等级多重定义，拒绝选择");
            }
        } else {
            res.setStatus(Resolution.Status.RESOLVED);
            res.setWinner(best);
            for (Provider p : cands) {
                if (!sameProvider(p, best)) reject(res, p, describeLoss(p, best));
            }
        }
        attachRelocations(res, source, name);
        emitVerdict(res);
        return res;
    }

    private void reject(Resolution res, Provider p, String reason) {
        res.getRejections().add(new CandidateRejection(labelOf(p), reason));
        TraceEvent te = emit(TraceEvent.Kind.CANDIDATE_REJECTED,
                "淘汰 " + labelOf(p) + "：" + reason);
        te.detail.put("symbol", p.symbolName());
        te.detail.put("provider", labelOf(p));
        te.detail.put("reason", reason);
    }

    private List<Provider> dedupe(List<Provider> in) {
        List<Provider> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Provider p : in) {
            String k = p.file.id() + "#" + p.symbol.getIndex()
                    + "#" + (p.versionName == null ? "-" : p.versionName)
                    + "#" + p.nonDefaultVersion;
            if (seen.add(k)) out.add(p);
        }
        return out;
    }

    private int rankScore(Provider p) {
        if (isCommon(p)) return 1;
        if (p.symbol.isWeak()) return 2;
        return 3; // strong
    }

    private int compareRank(Provider a, Provider b) {
        return Integer.compare(rankScore(a), rankScore(b));
    }

    private List<Provider> rank(List<Provider> cands) {
        List<Provider> sorted = new ArrayList<>(cands);
        sorted.sort(Comparator.comparingInt(this::rankScore).reversed()
                .thenComparingInt(p -> p.loadOrder)
                .thenComparingInt(p -> p.extractionRound)
                .thenComparing(p -> p.file.getMemberName() == null
                        ? "" : p.file.getMemberName()));
        return sorted;
    }

    private String describeLoss(Provider loser, Provider winner) {
        if (rankScore(loser) < rankScore(winner)) {
            if (isCommon(loser)) return "COMMON 弱于 "
                    + (winner.symbol.isWeak() ? "弱定义" : "强定义");
            if (loser.symbol.isWeak()) return "弱定义被强定义覆盖";
        }
        return "加载顺序/抽取次序靠后";
    }

    private boolean sameProvider(Provider a, Provider b) {
        return a.file == b.file && a.symbol.getIndex() == b.symbol.getIndex()
                && java.util.Objects.equals(a.versionName, b.versionName)
                && a.nonDefaultVersion == b.nonDefaultVersion;
    }

    private String decorated(String version, boolean nonDefault) {
        return (nonDefault ? "@" : "@@") + version;
    }

    private void attachRelocations(Resolution res, ElfFile source, String name) {
        for (Relocation r : source.getRelocations()) {
            if (name.equals(r.symbolName())) {
                res.getAffectedRelocations().add(new Resolution.RelocationRef(
                        source.id(), sectionName(source, r.sectionIndex()),
                        r.offset(), r.type(), r.typeName(), r.addend()));
            }
        }
        if (res.getReference().relocation != null
                && res.getReference().relocation.dynamicRef()) {
            Relocation r = res.getReference().relocation;
            res.getAffectedRelocations().add(new Resolution.RelocationRef(
                    source.id(), "dynamic", r.offset(), r.type(), r.typeName(), r.addend()));
        }
    }

    private String sectionName(ElfFile f, int idx) {
        if (idx < 0 || idx >= f.getSections().size()) return "dynsym";
        return f.getSections().get(idx).name();
    }

    private void emitVerdict(Resolution r) {
        Reference ref = r.getReference();
        TraceEvent te = emit(TraceEvent.Kind.VERDICT,
                "裁决 " + ref.sourceFile.id() + " 引用 " + ref.name()
                        + (ref.requiredVersion == null ? ""
                                : decorated(ref.requiredVersion, ref.nonDefaultVersion))
                        + " -> " + r.getStatus()
                        + (r.getWinner() == null ? "" : " (" + labelOf(r.getWinner()) + ")"));
        te.detail.put("reference", ref.sourceFile.id() + "#" + ref.name());
        te.detail.put("status", r.getStatus().name());
        te.detail.put("winner", r.getWinner() == null ? null : labelOf(r.getWinner()));
        te.detail.put("candidateCount", r.getCandidates().size());
        te.detail.put("rejectionCount", r.getRejections().size());
    }

    // ---------- Address layout ----------

    private void assignAddresses() {
        long cursor = 0x400000L;
        for (ElfFile f : result.getActiveFiles()) {
            long base;
            if (f.isDynamic()) {
                base = cursor;
                cursor += 0x200000L;
            } else {
                base = cursor;
                cursor += 0x100000L;
            }
            List<ElfSymbol> syms = f.isRelocatable() ? f.getStaticSymbols() : f.getDynamicSymbols();
            for (ElfSymbol s : syms) {
                if (s.isUndefined() || s.isLocal()) continue;
                if (s.getValue() != 0) {
                    // Pre-linked address (ET_EXEC) or DSO-relative value.
                    // We assign a deterministic virtual base + value for display.
                    long addr = f.isExecutable() ? s.getValue() : base + s.getValue();
                    rememberAddress(f, s, addr);
                } else if (s.isCommon()) {
                    long align = Math.max(1, commonAlignment(s));
                    base = (base + align - 1) & ~(align - 1);
                    rememberAddress(f, s, base);
                    base += Math.max(s.getSize(), 1);
                }
            }
        }
    }

    private long commonAlignment(ElfSymbol s) {
        // For SHN_COMMON the ELF spec stores alignment in st_value (power of two).
        long v = s.getValue();
        if (v > 0 && Long.bitCount(v) == 1) return v;
        return 16;
    }

    private void rememberAddress(ElfFile f, ElfSymbol s, long addr) {
        for (Provider p : allProvidersOf(f, s)) p.assignedAddress = addr;
    }

    private List<Provider> allProvidersOf(ElfFile f, ElfSymbol target) {
        List<Provider> out = new ArrayList<>();
        for (Resolution r : result.getResolutions()) {
            collectInto(r.getWinner(), f, target, out);
            for (Provider p : r.getTiedWinners()) collectInto(p, f, target, out);
            for (Provider p : r.getCandidates()) collectInto(p, f, target, out);
        }
        return out;
    }

    private void collectInto(Provider p, ElfFile f, ElfSymbol target, List<Provider> out) {
        if (p != null && p.file == f && p.symbol.getIndex() == target.getIndex()) out.add(p);
    }

    // ---------- Graphs ----------

    private void buildDependencyGraph() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> present = new LinkedHashSet<>();
        for (ElfFile f : result.getActiveFiles()) present.add(sonameOrId(f));
        for (ElfFile f : result.getActiveFiles()) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", sonameOrId(f));
            n.put("kind", f.isRelocatable()
                    ? (f.getRole() == ElfFile.Role.EXTRACTED_OBJECT ? "archive-member" : "object")
                    : f.isExecutable() ? "executable" : "shared");
            n.put("source", f.getSourcePath());
            n.put("member", f.getMemberName());
            nodes.add(n);
            for (String dep : f.getNeeded()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("from", sonameOrId(f));
                e.put("to", dep);
                e.put("kind", "DT_NEEDED");
                e.put("satisfied", present.contains(dep));
                edges.add(e);
            }
        }
        // Archive extraction edges.
        for (ElfFile f : result.getActiveFiles()) {
            if (f.getRole() == ElfFile.Role.EXTRACTED_OBJECT) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("from", f.getSourcePath());
                e.put("to", f.id());
                e.put("kind", "LAZY_EXTRACT");
                edges.add(e);
            }
        }
        result.getDependencyGraph().put("nodes", nodes);
        result.getDependencyGraph().put("edges", edges);
    }

    private String sonameOrId(ElfFile f) {
        return f.getSoname() != null ? f.getSoname() : f.id();
    }

    private void buildVersionGraph() {
        List<Map<String, Object>> defNodes = new ArrayList<>();
        List<Map<String, Object>> needEdges = new ArrayList<>();
        for (ElfFile f : result.getActiveFiles()) {
            for (VersionDef d : f.getVersionDefs()) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("file", sonameOrId(f));
                n.put("index", d.index());
                n.put("name", d.name());
                n.put("nonDefault", d.nonDefault());
                n.put("base", d.base());
                defNodes.add(n);
            }
            f.getVersionReqs().forEach(req -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("from", sonameOrId(f));
                e.put("neededFrom", req.neededFrom());
                e.put("version", req.name());
                e.put("nonDefault", req.nonDefault());
                e.put("satisfied", isVersionSatisfied(req.neededFrom(), req.name()));
                needEdges.add(e);
            });
        }
        result.getVersionGraph().put("definitions", defNodes);
        result.getVersionGraph().put("requirements", needEdges);
    }

    private boolean isVersionSatisfied(String neededFrom, String versionName) {
        for (ElfFile f : result.getActiveFiles()) {
            String id = sonameOrId(f);
            if (!id.equals(neededFrom)) continue;
            for (VersionDef d : f.getVersionDefs()) {
                if (d.name().equals(versionName)) return true;
            }
        }
        return false;
    }

    private void snapshotSymbolTable() {
        Map<String, Object> snap = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ElfFile f : result.getActiveFiles()) {
            List<ElfSymbol> syms = f.isRelocatable() ? f.getStaticSymbols() : f.getDynamicSymbols();
            for (ElfSymbol s : syms) {
                if (s.isUndefined() || s.isLocal() || s.getName().isEmpty()) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("file", f.id());
                row.put("name", s.getName());
                row.put("bind", s.bindName());
                row.put("visibility", s.visibilityName());
                row.put("common", s.isCommon());
                row.put("size", s.getSize());
                Provider p = winnerFor(f, s);
                row.put("exported", p != null);
                rows.add(row);
            }
        }
        snap.put("finalGlobalSymbols", rows);
        result.getSymbolTableSnapshots().add(snap);
    }

    private Provider winnerFor(ElfFile f, ElfSymbol s) {
        for (Resolution r : result.getResolutions()) {
            if (r.getWinner() != null && r.getWinner().file == f
                    && r.getWinner().symbol.getIndex() == s.getIndex()) {
                return r.getWinner();
            }
        }
        return null;
    }
}
