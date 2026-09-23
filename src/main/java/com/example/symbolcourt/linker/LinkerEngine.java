package com.example.symbolcourt.linker;

import com.example.symbolcourt.elf.ElfModels.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class LinkerEngine {

    public ResolutionPlan resolve(List<ObjectUnit> parsed, LinkRequest request) {
        return resolve(parsed, request.policy(), request.label(), canonicalHash(request));
    }

    public ResolutionPlan resolve(List<ObjectUnit> parsed, PolicyProfile policy, String label, String inputHash) {
        List<String> warnings = new ArrayList<>();
        List<ObjectUnit> active = new ArrayList<>();
        List<ObjectUnit> archiveMembers = new ArrayList<>();
        for (ObjectUnit unit : parsed) {
            if (unit.corrupt() || !unit.archiveMember()) active.add(unit);
            else archiveMembers.add(unit);
        }
        for (ObjectUnit unit : active) {
            if (unit.corrupt()) warnings.add("损坏对象已隔离: " + unit.displayName());
        }

        List<TimelineEvent> timeline = new ArrayList<>();
        timeline.add(event(0, "INPUT", "载入显式对象",
                active.stream().map(ObjectUnit::displayName).collect(Collectors.joining(", ")),
                null, snapshot(active, List.of())));

        List<ObjectUnit> extracted = lazyArchiveExtraction(active, archiveMembers, policy, timeline, warnings);
        List<ObjectUnit> all = new ArrayList<>(active);
        for (ObjectUnit member : archiveMembers) {
            if (extracted.stream().noneMatch(value -> value.id().equals(member.id()))) all.add(member);
        }
        all.sort(Comparator.comparingInt(ObjectUnit::explicitOrder).thenComparing(ObjectUnit::id));
        List<ObjectUnit> activeForLookup = List.copyOf(active);

        timeline.add(event(timeline.size(), "SYMBOL_TABLE", "最终符号表",
                "参与裁决的定义数量: " + all.stream().mapToInt(u -> (int) u.symbols().stream().filter(ElfSymbol::isDefined).count()).sum(),
                null, snapshot(all, extracted)));

        List<DependencyEdge> edges = dependencies(activeForLookup, extracted);
        Map<RequestKey, List<SymbolRequest>> grouped = buildRequests(activeForLookup);
        List<SymbolDecision> decisions = new ArrayList<>();
        for (Map.Entry<RequestKey, List<SymbolRequest>> entry : grouped.entrySet()) {
            decisions.add(decide(entry.getKey(), entry.getValue(), all, activeForLookup, policy, extracted));
        }
        decisions.sort(Comparator.comparing(SymbolDecision::name).thenComparing(d -> String.valueOf(d.requestedVersion())));

        Set<String> extractedIdSet = extracted.stream().map(ObjectUnit::id).collect(Collectors.toSet());
        return new ResolutionPlan(UUID.randomUUID().toString(), label == null ? "未命名方案" : label,
                inputHash, policy, List.copyOf(all), List.copyOf(edges), List.copyOf(decisions),
                List.copyOf(timeline), List.copyOf(warnings), Instant.now().toString());
    }

    private List<ObjectUnit> lazyArchiveExtraction(List<ObjectUnit> active, List<ObjectUnit> members,
                                                   PolicyProfile policy, List<TimelineEvent> events,
                                                   List<String> warnings) {
        List<ObjectUnit> extracted = new ArrayList<>();
        Set<String> extractedIds = new HashSet<>();
        Set<String> satisfied = new HashSet<>();
        int step = events.size();
        boolean changed = true;
        while (changed) {
            changed = false;
            List<SymbolRequest> requests = buildRequests(active).values().stream().flatMap(List::stream).toList();
            for (ObjectUnit member : members) {
                if (extractedIds.contains(member.id())) continue;
                Optional<SymbolRequest> needed = requests.stream()
                        .filter(req -> requestStillNeeds(req, satisfied))
                        .filter(req -> policy.extractArchiveForWeak() || !req.weak())
                        .filter(req -> memberCanProvide(member, req, policy, active))
                        .min(Comparator.comparingInt(SymbolRequest::order));
                if (needed.isPresent()) {
                    ObjectUnit pulled = member.withExtracted(true);
                    active.add(pulled);
                    extracted.add(pulled);
                    extractedIds.add(pulled.id());
                    String key = needed.get().name() + "\0" + String.valueOf(needed.get().requestedVersion());
                    satisfied.add(key);
                    Map<String, Object> snap = new LinkedHashMap<>();
                    snap.put("archive", member.archiveName());
                    snap.put("member", member.name());
                    snap.put("becauseOf", needed.get().name() + (needed.get().requestedVersion() == null ? "" : "@" + needed.get().requestedVersion()));
                    snap.put("activeObjects", active.stream().map(ObjectUnit::displayName).toList());
                    events.add(event(step++, "ARCHIVE_EXTRACT", "抽取 archive member",
                            member.archiveName() + "(" + member.name() + ") 因 " + needed.get().name() + " 被惰性抽取",
                            member.name(), snap));
                    if (!pulled.parseErrors().isEmpty()) warnings.add("archive member 已隔离: " + pulled.displayName());
                    changed = true;
                }
            }
        }
        return extracted;
    }

    private boolean requestStillNeeds(SymbolRequest request, Set<String> satisfied) {
        return !satisfied.contains(request.name() + "\0" + String.valueOf(request.requestedVersion()));
    }

    private boolean memberCanProvide(ObjectUnit member, SymbolRequest request, PolicyProfile policy, List<ObjectUnit> active) {
        if (member.corrupt()) return false;
        return member.symbols().stream().anyMatch(symbol -> isPotentialProvider(symbol)
                && symbol.name().equals(request.name())
                && versionMatches(request.requestedVersion(), symbol.versionName())
                && platformMatches(member, policy));
    }

    private Map<RequestKey, List<SymbolRequest>> buildRequests(List<ObjectUnit> units) {
        Map<String, List<AffectedRelocation>> relocations = new LinkedHashMap<>();
        Map<String, SymbolRequest> unique = new LinkedHashMap<>();
        for (ObjectUnit unit : units) {
            if (unit.corrupt()) continue;
            for (ElfSymbol symbol : unit.symbols()) {
                if (!symbol.isUndefined()) continue;
                int visibility = symbol.visibility();
                String id = requestId(unit, symbol.name(), symbol.versionName(), symbol.isWeak(), false, visibility);
                relocations.computeIfAbsent(id, ignored -> new ArrayList<>());
                unique.computeIfAbsent(id, ignored -> new SymbolRequest(id, unit.id(), unit.name(), symbol.name(),
                        symbol.versionName(), symbol.isWeak(), false, "symbol:" + symbol.symbolIndex(),
                        unit.order(), visibility, relocations.get(id)));
            }
            for (ElfRelocation relocation : unit.relocations()) {
                Optional<ElfSymbol> referenced = unit.symbols().stream()
                        .filter(s -> s.dynamic() == relocation.dynamicSymbol() && s.symbolIndex() == relocation.symbolIndex())
                        .findFirst();
                if (referenced.isPresent() && !referenced.get().isUndefined() && !relocation.copy()) continue;
                String version = referenced.map(ElfSymbol::versionName).orElse(relocation.symbolVersion());
                boolean weak = referenced.map(ElfSymbol::isWeak).orElse(false);
                int visibility = referenced.map(ElfSymbol::visibility).orElse(0);
                String id = requestId(unit, relocation.symbolName(), version, weak, relocation.copy(), visibility);
                List<AffectedRelocation> list = relocations.computeIfAbsent(id, ignored -> new ArrayList<>());
                list.add(new AffectedRelocation(unit.id(), unit.name(), relocation.offset(), relocation.sectionName(),
                        relocation.type(), relocation.addend(), relocation.copy(), version));
                unique.computeIfAbsent(id, ignored -> new SymbolRequest(id, unit.id(), unit.name(),
                        relocation.symbolName(), version, weak, relocation.copy(),
                        relocation.sectionName() + "+" + relocation.offset(), unit.order(), visibility, list));
            }
        }
        Map<RequestKey, List<SymbolRequest>> grouped = new LinkedHashMap<>();
        for (SymbolRequest request : unique.values()) {
            RequestKey key = request.visibility() == 1 || request.visibility() == 2
                    ? new RequestKey(request.name(), normalizeVersion(request.requestedVersion()), request.unitId())
                    : new RequestKey(request.name(), normalizeVersion(request.requestedVersion()), null);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(request);
        }
        return grouped;
    }

    private SymbolDecision decide(RequestKey key, List<SymbolRequest> requests, List<ObjectUnit> all,
                                  List<ObjectUnit> active, PolicyProfile policy, List<ObjectUnit> extracted) {
        List<AffectedRelocation> affected = requests.stream()
                .flatMap(req -> req.relocations().stream())
                .distinct()
                .sorted(Comparator.comparing(AffectedRelocation::unitName).thenComparing(AffectedRelocation::offset))
                .toList();
        boolean weakOnly = requests.stream().allMatch(SymbolRequest::weak);
        boolean copy = requests.stream().anyMatch(SymbolRequest::copyRelocation);

        List<Candidate> candidates = new ArrayList<>();
        for (ObjectUnit unit : all) {
            if (unit.corrupt()) continue;
            for (ElfSymbol symbol : unit.symbols()) {
                if (!isPotentialProvider(symbol)) continue;
                Candidate candidate = evaluateCandidate(unit, symbol, key, requests, active, policy, copy);
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::rank).reversed()
                .thenComparing(c -> c.unit.order())
                .thenComparing(c -> c.symbol.symbolIndex()));
        List<CandidateView> views = candidates.stream().map(this::toView).toList();
        List<Candidate> eligible = candidates.stream().filter(Candidate::eligible).toList();

        if (eligible.isEmpty()) {
            DecisionState state;
            if (candidates.stream().anyMatch(c -> c.reason == RejectReason.VERSION_MISMATCH)) state = DecisionState.UNSATISFIED_VERSION;
            else if (copy && candidates.stream().anyMatch(c -> c.reason == RejectReason.COPY_PROTECTED)) state = DecisionState.COPY_RELOCATION_REJECTED;
            else state = weakOnly ? DecisionState.WEAK_UNRESOLVED : DecisionState.UNRESOLVED;
            return new SymbolDecision(key.name(), key.version(), state, reasonForEmpty(state, key, weakOnly), views,
                    List.of(), affected, copy, null);
        }

        Candidate best = eligible.get(0);
        List<Candidate> bestTier = eligible.stream().filter(c -> c.rank == best.rank).toList();
        List<Candidate> strong = bestTier.stream().filter(c -> c.symbol.isGlobal() && c.symbol.isDefined()
                && !c.symbol.isProtected() ).toList();
        if (best.rank >= rankForStaticStrong(best) && strong.size() > 1
                && strong.stream().map(c -> c.symbol.groupSignature()).filter(Objects::nonNull).distinct().count() != 1) {
            return new SymbolDecision(key.name(), key.version(), DecisionState.MULTIPLE_DEFINITION,
                    "多个同强度 static/global 定义竞争；不使用文件系统遍历次序消解。",
                    views, strong.stream().map(Candidate::id).toList(), affected, copy, best.symbol.versionName());
        }
        if (!policy.firstLoadedTie() && bestTier.size() > 1) {
            return new SymbolDecision(key.name(), key.version(), DecisionState.AMBIGUOUS,
                    "存在 " + bestTier.size() + " 个同等候选；策略保留歧义。",
                    views, bestTier.stream().map(Candidate::id).toList(), affected, copy, best.symbol.versionName());
        }
        if (copy && !policy.allowCopyRelocation()) {
            return new SymbolDecision(key.name(), key.version(), DecisionState.COPY_RELOCATION_REJECTED,
                    "目标平台策略禁止 copy relocation。", views, List.of(), affected, true, best.symbol.versionName());
        }
        String rationale;
        if (best.symbol.isCommon()) rationale = "common 符号按大小合并并由最大块提供。";
        else if (best.symbol.groupSignature() != null && bestTier.size() > 1) rationale = "COMDAT section group 同名组去重，保留一个等价成员。";
        else if (best.unit.kind() == ObjectKind.SHARED) rationale = "按显式加载顺序从 shared object 的动态符号表选中。";
        else rationale = "按显式输入顺序、binding 与可见性规则选中最强候选。";
        return new SymbolDecision(key.name(), key.version(), DecisionState.RESOLVED, rationale,
                views, List.of(best.id()), affected, copy, best.symbol.versionName());
    }

    private Candidate evaluateCandidate(ObjectUnit unit, ElfSymbol symbol, RequestKey key,
                                        List<SymbolRequest> requests, List<ObjectUnit> active,
                                        PolicyProfile policy, boolean copy) {
        RejectReason reason = RejectReason.NONE;
        String detail = "";
        int rank = 0;
        boolean eligible = true;
        boolean extracted = active.stream().anyMatch(value -> value.id().equals(unit.id()));

        if (unit.archiveMember() && !extracted) {
            eligible = false; reason = RejectReason.NOT_EXTRACTED; detail = "archive member 惰性抽取未触发";
        } else if (!platformMatches(unit, policy)) {
            eligible = false; reason = RejectReason.PLATFORM_MISMATCH; detail = "machine/class 与目标平台策略不一致";
        } else if (!versionMatches(key.version(), symbol.versionName())) {
            eligible = false; reason = RejectReason.VERSION_MISMATCH; detail = "请求 " + versionText(key.version()) + "，定义 " + versionText(symbol.versionName());
        } else if (symbol.isLocal()) {
            eligible = false; reason = RejectReason.LOCAL_SCOPE; detail = "local binding 不能跨对象提供";
        } else if (symbol.isHiddenOrInternal()) {
            boolean sameUnit = requests.stream().anyMatch(req -> req.unitId().equals(unit.id()));
            if (!sameUnit) { eligible = false; reason = RejectReason.VISIBILITY; detail = "hidden/internal 仅对象内可见"; }
            else rank += 90;
        } else {
            if (symbol.isDefined()) rank += unit.kind() == ObjectKind.SHARED ? 35 : 100;
            if (symbol.isCommon()) rank += policy.allowCommonMerge() ? 55 : 0;
            if (symbol.isGlobal()) rank += 30;
            if (symbol.isWeak()) rank += 10;
            if (symbol.versionName() != null) rank += 4;
            if (symbol.groupSignature() != null) rank += 2;
            rank += Math.min((int) symbol.size() / 8, 20);
            rank -= unit.order() * 2;
        }
        if (symbol.isCommon() && !policy.allowCommonMerge() && reason == RejectReason.NONE) {
            eligible = false; reason = RejectReason.COMMON_DISABLED; detail = "平台策略不接受 SHN_COMMON 合并";
        }
        if (copy && symbol.isProtected() && !policy.allowProtectedDataOverride() && reason == RejectReason.NONE) {
            eligible = false; reason = RejectReason.COPY_PROTECTED; detail = "protected 数据不能作为普通 copy relocation 来源";
        }
        if (!eligible && rank > 0 && reason != RejectReason.PLATFORM_MISMATCH && reason != RejectReason.VERSION_MISMATCH) {
            rank = -rank;
        }
        return new Candidate(unit.id() + "#" + (symbol.dynamic() ? "dyn" : "sym") + symbol.symbolIndex(),
                unit, symbol, eligible, reason, detail, rank);
    }

    private boolean isPotentialProvider(ElfSymbol symbol) {
        if (symbol.name() == null || symbol.name().isEmpty()) return false;
        if (symbol.isUndefined()) return false;
        return symbol.isDefined() || symbol.isCommon();
    }

    private boolean versionMatches(String requested, String provided) {
        return Objects.equals(normalizeVersion(requested), normalizeVersion(provided));
    }

    private String normalizeVersion(String value) {
        if (value == null || value.isBlank() || "(local)".equals(value) || "(global)".equals(value)) return "";
        return value;
    }

    private String versionText(String value) {
        String normalized = normalizeVersion(value);
        return normalized.isEmpty() ? "(unversioned)" : normalized;
    }

    private boolean platformMatches(ObjectUnit unit, PolicyProfile policy) {
        if (unit.identity() == null) return false;
        if (policy.targetMachine() != null && unit.identity().machine() != policy.targetMachine()) return false;
        return unit.identity().elfClass() == 1 || unit.identity().elfClass() == 2;
    }

    private CandidateView toView(Candidate candidate) {
        ObjectUnit unit = candidate.unit;
        ElfSymbol symbol = candidate.symbol;
        return new CandidateView(candidate.id, unit.id(), unit.displayName(), unit.archiveName(), unit.order(),
                symbol.bindingName(), symbol.visibilityName(), symbol.sectionName(), symbol.versionName(),
                symbol.nonDefaultVersion(), symbol.size(), symbol.value(), simulatedAddress(unit, symbol),
                symbol.groupSignature(), symbol.isCommon(), unit.archiveMember(), unit.extracted(),
                candidate.eligible, candidate.reason, candidate.detail, candidate.rank);
    }

    private long simulatedAddress(ObjectUnit unit, ElfSymbol symbol) {
        long base = unit.kind() == ObjectKind.SHARED ? 0x7f0000000000L + (long) unit.order() * 0x100000L
                : 0x400000L + (long) unit.order() * 0x10000L;
        return base + Long.divideUnsigned(symbol.value(), 1);
    }

    private String reasonForEmpty(DecisionState state, RequestKey key, boolean weakOnly) {
        return switch (state) {
            case UNSATISFIED_VERSION -> "存在同名符号但版本索引还原后的版本需求无法满足。";
            case COPY_RELOCATION_REJECTED -> "copy relocation 不能由 protected 数据或当前平台策略满足。";
            case WEAK_UNRESOLVED -> "weak 引用没有可用提供者；保留为 weak unresolved，不升级为强错误。";
            default -> "没有满足名称、版本与可见性约束的候选。";
        };
    }

    private List<DependencyEdge> dependencies(List<ObjectUnit> active, List<ObjectUnit> extracted) {
        List<DependencyEdge> edges = new ArrayList<>();
        List<ObjectUnit> all = new ArrayList<>(active);
        for (ObjectUnit unit : active) {
            for (String needed : unit.neededLibraries()) {
                boolean satisfied = all.stream().anyMatch(value -> value.name().equals(needed)
                        || value.name().startsWith(needed + "."));
                edges.add(new DependencyEdge(unit.id(), needed, "DT_NEEDED", satisfied));
            }
        }
        for (ObjectUnit member : extracted) {
            edges.add(new DependencyEdge(member.archiveName(), member.id(), "ARCHIVE_LAZY_EXTRACT", true));
        }
        return edges;
    }

    private TimelineEvent event(int step, String type, String title, String detail, String unitName,
                                Map<String, Object> snapshot) {
        return new TimelineEvent(step, type, title, detail, unitName, snapshot == null ? Map.of() : snapshot);
    }

    private Map<String, Object> snapshot(List<ObjectUnit> units, List<ObjectUnit> extracted) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("activeObjects", units.stream().map(ObjectUnit::displayName).toList());
        value.put("definedSymbols", units.stream().flatMap(unit -> unit.symbols().stream()
                .filter(this::isPotentialProvider)
                .map(symbol -> unit.displayName() + ":" + symbol.name()
                        + (symbol.versionName() == null ? "" : "@" + symbol.versionName())
                        + " [" + symbol.bindingName() + "/" + symbol.visibilityName() + "]"))
                .toList());
        value.put("extractedMembers", extracted.stream().map(ObjectUnit::displayName).toList());
        return value;
    }

    private String requestId(ObjectUnit unit, String name, String version, boolean weak, boolean copy, int visibility) {
        return unit.id() + "|" + name + "|" + normalizeVersion(version) + "|" + weak + "|" + copy + "|" + visibility;
    }

    public PlanComparison compare(ResolutionPlan older, ResolutionPlan newer) {
        Map<String, ProviderPoint> oldPoints = providerPoints(older);
        Map<String, ProviderPoint> newPoints = newPoints(newer);
        List<ReferenceDifference> providerChanges = new ArrayList<>();
        List<ReferenceDifference> addressOnly = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, ProviderPoint> entry : oldPoints.entrySet()) {
            ProviderPoint oldPoint = entry.getValue();
            ProviderPoint newPoint = newPoints.get(entry.getKey());
            if (newPoint == null) {
                removed.add(entry.getKey());
                continue;
            }
            if (!Objects.equals(oldPoint.providerId, newPoint.providerId)) {
                providerChanges.add(new ReferenceDifference(oldPoint.unitName, oldPoint.offset, oldPoint.symbol,
                        oldPoint.version, oldPoint.provider, newPoint.provider, "PROVIDER_CHANGED",
                        "引用改绑到另一个对象或符号候选。"));
            } else if (oldPoint.address != newPoint.address || oldPoint.state != newPoint.state) {
                addressOnly.add(new ReferenceDifference(oldPoint.unitName, oldPoint.offset, oldPoint.symbol,
                        oldPoint.version, oldPoint.provider, newPoint.provider, "ADDRESS_ONLY",
                        "提供者相同，仅模拟装载地址或裁决状态变化。"));
            }
        }
        boolean equivalent = providerChanges.isEmpty() && removed.isEmpty();
        return new PlanComparison(equivalent, List.copyOf(providerChanges), List.copyOf(addressOnly), List.copyOf(removed));
    }

    private Map<String, ProviderPoint> providerPoints(ResolutionPlan plan) {
        Map<String, ProviderPoint> points = new LinkedHashMap<>();
        Map<String, CandidateView> candidates = new LinkedHashMap<>();
        for (SymbolDecision decision : plan.decisions()) {
            for (CandidateView candidate : decision.candidates()) candidates.put(candidate.candidateId(), candidate);
            for (AffectedRelocation relocation : decision.relocations()) {
                String key = relocation.unitId() + ":" + relocation.section() + ":" + relocation.offset()
                        + ":" + decision.name() + ":" + normalizeVersion(decision.requestedVersion());
                if (decision.selectedCandidateIds().isEmpty()) {
                    points.put(key, new ProviderPoint(relocation.unitName(), relocation.offset(), decision.name(),
                            decision.requestedVersion(), "", "<" + decision.state() + ">", 0, decision.state().name()));
                } else {
                    CandidateView candidate = candidates.get(decision.selectedCandidateIds().get(0));
                    if (candidate != null) points.put(key, new ProviderPoint(relocation.unitName(), relocation.offset(),
                            decision.name(), decision.requestedVersion(), candidate.candidateId(),
                            candidate.unitName(), candidate.simulatedAddress() + relocation.offset(), decision.state().name()));
                }
            }
        }
        return points;
    }

    private String canonicalHash(LinkRequest request) {
        String raw = request.policy().name() + "\n" + request.label() + "\n"
                + String.join("\n", request.paths() == null ? List.of() : request.paths()) + "\n";
        List<byte[]> objects = request.embeddedObjects() == null ? List.of() : request.embeddedObjects();
        return UUID.nameUUIDFromBytes((raw + objects.size()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private record RequestKey(String name, String version, String localUnit) {}
    private record Candidate(String id, ObjectUnit unit, ElfSymbol symbol, boolean eligible,
                             RejectReason reason, String detail, int rank) {}
    private record ProviderPoint(String unitName, long offset, String symbol, String version,
                                 String providerId, String provider, long address, String state) {}
}
