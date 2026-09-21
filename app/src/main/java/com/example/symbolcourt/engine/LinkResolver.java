package com.example.symbolcourt.engine;

import com.example.symbolcourt.domain.Models.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class LinkResolver {

    public ResolutionResult resolve(LinkRequest request) {
        State state = new State(request);
        state.events.add(new PlayEvent(state.events.size(), "PLAN_START", "开始裁决",
                request.policy().displayName(), Map.of("objects", request.loadOrder().size())));
        for (ParsedObject object : request.explicitObjects()) {
            if (object.healthy()) {
                state.activate(object, -1, "显式 ELF 对象", null);
            } else {
                state.notices.add("隔离损坏对象 " + object.displayName() + "：" + object.parseError());
            }
        }
        extractArchives(state);
        resolveStaticDefinitions(state);
        List<ResolutionDecision> decisions = buildDecisions(state);
        return new ResolutionResult(request.planId(), request.policy().name(),
                request.policy().rescanArchives(), state.activeObjects, state.extractedMembers,
                decisions, state.events, buildDependencyEdges(state), state.allVersionNodes(),
                state.allVersionEdges(), state.notices);
    }

    private void extractArchives(State state) {
        for (ArchiveFile archive : state.request.archives()) {
            if (archive.parseError() != null) {
                state.notices.add("隔离损坏 archive " + archive.displayName() + "：" + archive.parseError());
            }
        }
        List<ArchiveFile> healthyArchives = state.request.archives().stream()
                .filter(archive -> archive.parseError() == null).toList();
        boolean changed = true;
        int passes = 0;
        while (changed) {
            changed = false;
            passes++;
            for (ArchiveFile archive : healthyArchives) {
                for (ArchiveMember member : archive.members()) {
                    if (state.extracted.contains(member)) {
                        continue;
                    }
                    ParsedObject object = member.object();
                    if (!object.healthy()) {
                        state.notices.add("隔离损坏 archive 成员 " + object.displayName() + "："
                                + object.parseError());
                        state.skippedMembers.add(member);
                        continue;
                    }
                    String reason = extractionReason(state, object);
                    if (reason != null) {
                        state.activate(object, state.loadOrderOf(archive.id()), "从 archive 惰性抽取",
                                member);
                        state.extracted.add(member);
                        changed = true;
                        state.events.add(new PlayEvent(state.events.size(), "ARCHIVE_EXTRACT",
                                "抽取 archive 成员", object.displayName(),
                                Map.of("archive", archive.displayName(), "member", member.memberName(),
                                        "reason", reason, "pass", passes)));
                    }
                }
            }
            if (!state.request.policy().rescanArchives()) {
                break;
            }
        }
    }

    private String extractionReason(State state, ParsedObject candidateObject) {
        for (ElfSymbol provider : definitions(candidateObject)) {
            if (!canExtractFor(provider)) {
                continue;
            }
            for (ParsedObject referrer : state.activeObjects) {
                for (SymbolReference reference : references(referrer)) {
                    if (matches(reference, provider) && !hasSatisfyingStatic(state, provider)) {
                        return "满足 " + referrer.displayName() + " 对 " + provider.name() + " 的引用";
                    }
                }
            }
        }
        return null;
    }

    private boolean canExtractFor(ElfSymbol provider) {
        if (!provider.defined()) {
            return false;
        }
        return "GLOBAL".equals(provider.binding()) || "WEAK".equals(provider.binding());
    }

    private boolean hasSatisfyingStatic(State state, ElfSymbol candidate) {
        return state.activeObjects.stream().flatMap(object -> definitions(object).stream())
                .filter(symbol -> isStaticDomain(state, symbol)
                        && symbolMatchesNameVersion(candidate, symbol))
                .anyMatch(symbol -> true);
    }

    private void resolveStaticDefinitions(State state) {
        Map<String, List<ElfSymbol>> strong = new LinkedHashMap<>();
        for (ParsedObject object : state.activeObjects) {
            for (ElfSymbol symbol : definitions(object)) {
                if (symbol.common() || !"GLOBAL".equals(symbol.binding())
                        || "HIDDEN".equals(symbol.visibility())
                        || "INTERNAL".equals(symbol.visibility())) {
                    continue;
                }
                if (!isStaticDomain(state, symbol)) {
                    continue;
                }
                String key = defKey(symbol.name(), versionName(symbol));
                strong.computeIfAbsent(key, ignored -> new ArrayList<>()).add(symbol);
            }
        }
        strong.forEach((key, symbols) -> {
            Set<String> providers = symbols.stream().map(ElfSymbol::providerId).collect(Collectors.toSet());
            if (providers.size() > 1) {
                state.staticConflicts.put(key, List.copyOf(symbols));
                state.events.add(new PlayEvent(state.events.size(), "STRONG_CONFLICT",
                        "多个 strong 定义冲突", key, Map.of("candidates", providers.size())));
            }
        });
    }

    private List<ResolutionDecision> buildDecisions(State state) {
        Map<String, List<SymbolReference>> grouped = new LinkedHashMap<>();
        for (ParsedObject object : state.activeObjects) {
            for (SymbolReference reference : references(object)) {
                String key = refKey(reference);
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(reference);
            }
        }
        List<ResolutionDecision> decisions = new ArrayList<>();
        for (Map.Entry<String, List<SymbolReference>> entry : grouped.entrySet()) {
            decisions.add(decide(state, entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, List<ElfSymbol>> conflict : state.staticConflicts.entrySet()) {
            if (grouped.containsKey(conflict.getKey())) {
                continue;
            }
            decisions.add(conflictDecision(state, conflict.getKey(), conflict.getValue()));
        }
        return List.copyOf(decisions);
    }

    private List<ElfSymbol> definitions(ParsedObject object) {
        return object.symbols().stream()
                .filter(symbol -> symbol.defined() || symbol.common())
                .filter(symbol -> !symbol.name().isEmpty())
                .toList();
    }

    private List<SymbolReference> references(ParsedObject object) {
        return object.references().stream()
                .filter(reference -> !reference.symbolName().isEmpty())
                .filter(reference -> !"LOCAL".equals(reference.binding()))
                .toList();
    }

    private String refKey(SymbolReference reference) {
        return (reference.copy() ? "COPY:" : "REF:") + reference.symbolName() + "\u0000"
                + nullToEmpty(reference.requestedVersion()) + "\u0000"
                + referrerDomain(reference);
    }

    private String defKey(String name, String version) {
        return name + "\u0000" + nullToEmpty(version);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String referrerDomain(SymbolReference reference) {
        return reference.objectId();
    }

    private boolean isStaticDomain(State state, ElfSymbol symbol) {
        return state.activeObjects.stream()
                .filter(object -> Objects.equals(object.id(), symbol.objectId()))
                .findFirst()
                .map(ParsedObject::staticObject)
                .orElse(false);
    }

    private String versionName(ElfSymbol symbol) {
        if (symbol.versionName() == null) {
            return null;
        }
        return symbol.defaultVersion() ? null : symbol.versionName();
    }

    private boolean matches(SymbolReference reference, ElfSymbol provider) {
        if (!reference.symbolName().equals(provider.name()) || !provider.defined() && !provider.common()) {
            return false;
        }
        if (sameObject(reference, provider) && !reference.dynamic()) {
            return "HIDDEN".equals(provider.visibility()) || "PROTECTED".equals(provider.visibility())
                    || "LOCAL".equals(provider.binding());
        }
        if ("LOCAL".equals(provider.binding()) || "HIDDEN".equals(provider.visibility())) {
            return false;
        }
        return versionMatches(reference.requestedVersion(), provider);
    }

    private boolean sameObject(SymbolReference reference, ElfSymbol provider) {
        return Objects.equals(reference.objectId(), provider.objectId());
    }

    private boolean symbolMatchesNameVersion(ElfSymbol wanted, ElfSymbol candidate) {
        return wanted.name().equals(candidate.name())
                && Objects.equals(versionName(wanted), versionName(candidate));
    }

    private boolean versionMatches(String requested, ElfSymbol provider) {
        if (requested == null || requested.isBlank()) {
            return provider.versionName() == null || provider.defaultVersion();
        }
        if (Objects.equals(requested, provider.versionName())) {
            return true;
        }
        return provider.versionAliases().contains(requested);
    }

    private ResolutionDecision decide(State state, String key, List<SymbolReference> references) {
        SymbolReference first = references.get(0);
        List<Candidate> candidates = new ArrayList<>();
        List<ElfSymbol> viable = new ArrayList<>();
        Map<String, Long> scores = new LinkedHashMap<>();
        for (ParsedObject object : state.activeObjects) {
            for (ElfSymbol symbol : definitions(object)) {
                Candidate candidate = evaluate(state, first, symbol);
                candidates.add(candidate);
                if (candidate.accepted()) {
                    viable.add(symbol);
                    scores.put(symbol.providerId(), score(state, first, symbol));
                }
            }
        }
        String conflictKey = defKey(first.symbolName(), first.requestedVersion());
        List<ElfSymbol> forcedConflicts = state.staticConflicts.getOrDefault(conflictKey, List.of());
        long best = viable.stream().mapToLong(symbol -> scores.get(symbol.providerId())).max().orElse(-1);
        Set<String> forcedIds = forcedConflicts.stream().map(ElfSymbol::providerId)
                .collect(Collectors.toSet());
        List<ElfSymbol> winners = forcedConflicts.isEmpty()
                ? viable.stream().filter(symbol -> scores.get(symbol.providerId()) == best).toList()
                : viable.stream().filter(symbol -> forcedIds.contains(symbol.providerId())).toList();
        if (winners.isEmpty()) {
            winners = viable.stream().filter(symbol -> scores.get(symbol.providerId()) == best).toList();
        }
        List<Candidate> marked = markWinners(candidates, winners, !forcedConflicts.isEmpty());
        String status;
        List<String> chosen = winners.stream().map(ElfSymbol::providerId).toList();
        String reason;
        if (!forcedConflicts.isEmpty() && winners.size() > 1) {
            status = "STRONG_CONFLICT";
            reason = "多个不可覆盖的 strong 定义同等可见，保留歧义";
        } else if (winners.size() > 1) {
            status = "AMBIGUOUS";
            reason = "多个候选评分相同，不能按遍历顺序打破平局";
        } else if (winners.size() == 1) {
            status = first.copy() ? "COPY_RELOCATION" : "RESOLVED";
            reason = first.copy() ? "copy relocation 将复制主程序可见值" : "按静态/动态作用域和加载顺序选中";
        } else {
            status = isWeakReference(first) ? "WEAK_UNRESOLVED" : "UNRESOLVED";
            reason = isWeakReference(first) ? "weak 引用允许保持空绑定" : "没有满足名称、版本、可见性和 COMDAT 条件的候选";
            chosen = List.of();
        }
        return new ResolutionDecision(key, first.symbolName(), first.requestedVersion(),
                first.copy() ? "COPY" : "SYMBOL", status, List.copyOf(marked), chosen, reason,
                affected(references));
    }

    private List<Candidate> markWinners(List<Candidate> candidates, List<ElfSymbol> winners,
                                        boolean conflict) {
        Set<String> winnerIds = winners.stream().map(ElfSymbol::providerId).collect(Collectors.toSet());
        return candidates.stream().map(candidate -> {
            if (!candidate.accepted()) {
                return candidate;
            }
            if (!winnerIds.contains(candidate.providerId())) {
                return withElimination(candidate, "排名低于最终候选");
            }
            if (conflict || winners.size() > 1) {
                return candidate;
            }
            return new Candidate(candidate.providerId(), candidate.objectId(), candidate.objectName(),
                    candidate.archiveName(), candidate.memberName(), candidate.symbolName(),
                    candidate.versionName(), candidate.versionAliases(), candidate.defaultVersion(),
                    candidate.binding(), candidate.visibility(), candidate.symbolType(),
                    candidate.sectionName(), candidate.sectionIndex(), candidate.value(), candidate.size(),
                    candidate.loadOrder(), candidate.common(), true, null, candidate.evidence());
        }).toList();
    }

    private Candidate withElimination(Candidate candidate, String reason) {
        return new Candidate(candidate.providerId(), candidate.objectId(), candidate.objectName(),
                candidate.archiveName(), candidate.memberName(), candidate.symbolName(),
                candidate.versionName(), candidate.versionAliases(), candidate.defaultVersion(),
                candidate.binding(), candidate.visibility(), candidate.symbolType(),
                candidate.sectionName(), candidate.sectionIndex(), candidate.value(), candidate.size(),
                candidate.loadOrder(), candidate.common(), false, reason, candidate.evidence());
    }

    private Candidate evaluate(State state, SymbolReference reference, ElfSymbol symbol) {
        int order = state.loadOrderOf(symbol.objectId());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("loadOrder", order);
        evidence.put("binding", symbol.binding());
        evidence.put("visibility", symbol.visibility());
        evidence.put("version", symbol.versionName());
        evidence.put("common", symbol.common());
        String elimination = eliminationReason(state, reference, symbol, evidence);
        return new Candidate(symbol.providerId(), symbol.objectId(), symbol.objectName(),
                symbol.archiveName(), symbol.memberName(), symbol.name(), symbol.versionName(),
                symbol.versionAliases(), symbol.defaultVersion(), symbol.binding(), symbol.visibility(),
                symbol.symbolType(), symbol.sectionName(), symbol.sectionIndex(), symbol.value(),
                symbol.size(), order, symbol.common(), elimination == null, elimination,
                Map.copyOf(evidence));
    }

    private String eliminationReason(State state, SymbolReference reference, ElfSymbol symbol,
                                     Map<String, Object> evidence) {
        if (!reference.symbolName().equals(symbol.name())) {
            return "名称不同";
        }
        if (reference.copy() && symbol.objectId().equals(reference.objectId())) {
            return "copy relocation 不能选择引用所在对象自身";
        }
        if (sameObject(reference, symbol) && !reference.dynamic()
                && "HIDDEN".equals(symbol.visibility())) {
            return null;
        }
        if ("LOCAL".equals(symbol.binding())) {
            return "local 符号只在对象内可见";
        }
        if ("HIDDEN".equals(symbol.visibility()) && !sameObject(reference, symbol)) {
            return "hidden 符号不参与外部链接";
        }
        if ("PROTECTED".equals(symbol.visibility()) && !sameObject(reference, symbol)
                && !reference.copy() && reference.dynamic()) {
            return "protected 动态符号不能从其他 DSO 抢占";
        }
        if (reference.requestedVersion() != null && !versionMatches(reference.requestedVersion(), symbol)) {
            evidence.put("requestedVersion", reference.requestedVersion());
            return "版本索引还原后的版本名不匹配";
        }
        if (reference.requestedVersion() == null && symbol.versionName() != null
                && !symbol.defaultVersion()) {
            return "非默认版本不能满足无版本引用";
        }
        if (isInDiscardedComdat(state, symbol)) {
            return "所属 COMDAT section group 已去重丢弃";
        }
        if (reference.copy() && symbol.common()) {
            return "copy relocation 源不能是 common 占位定义";
        }
        if (!reference.copy() && reference.strong() && symbol.common()
                && state.staticConflicts.containsKey(defKey(symbol.name(), versionName(symbol)))) {
            return "已有 strong 定义，common 被合并淘汰";
        }
        return null;
    }

    private boolean isInDiscardedComdat(State state, ElfSymbol symbol) {
        ParsedObject object = state.activeObjects.stream()
                .filter(candidate -> Objects.equals(candidate.id(), symbol.objectId()))
                .findFirst().orElse(null);
        if (object == null) {
            return false;
        }
        for (SectionGroup group : object.groups()) {
            if (!group.comdat() || !group.memberSections().contains(symbol.sectionIndex())) {
                continue;
            }
            String selected = state.selectedComdatGroups.get(group.signature());
            if (selected != null && !selected.equals(object.id() + ":" + group.signature())) {
                return true;
            }
        }
        return false;
    }

    private long score(State state, SymbolReference reference, ElfSymbol symbol) {
        ParsedObject object = state.activeObjects.stream()
                .filter(candidate -> Objects.equals(candidate.id(), symbol.objectId())).findFirst()
                .orElseThrow();
        long score = 0;
        if (sameObject(reference, symbol)) {
            score += 1_000_000;
        }
        if (object.staticObject()) {
            score += 500_000;
        }
        if (!symbol.common()) {
            score += 100_000;
        }
        if ("GLOBAL".equals(symbol.binding())) {
            score += 40_000;
        } else if ("WEAK".equals(symbol.binding())) {
            score += 10_000;
        }
        if ("PROTECTED".equals(symbol.visibility()) && sameObject(reference, symbol)) {
            score += 20_000;
        }
        if (symbol.versionName() != null && Objects.equals(symbol.versionName(), reference.requestedVersion())) {
            score += 5_000;
        }
        int order = state.loadOrderOf(symbol.objectId());
        score += 100_000L - Math.min(order, 100_000);
        return score;
    }

    private ResolutionDecision conflictDecision(State state, String key, List<ElfSymbol> symbols) {
        String[] parts = key.split("\u0000", 2);
        String version = parts.length == 2 && !parts[1].isEmpty() ? parts[1] : null;
        List<Candidate> candidates = symbols.stream()
                .map(symbol -> evaluate(state, new SymbolReference("conflict", symbol.objectId(),
                        symbol.objectName(), symbol.archiveName(), symbol.memberName(), symbol.name(),
                        version, "GLOBAL", true, "DEFINITION", -1, symbol.symbolIndex(),
                        symbol.sectionName(), 0, 0, false, false), symbol))
                .toList();
        List<String> chosen = symbols.stream().map(ElfSymbol::providerId).toList();
        return new ResolutionDecision("DEF:" + key, parts[0], version, "DEFINITION",
                "STRONG_CONFLICT", candidates, chosen, "重复 strong 定义，没有可接受的加载顺序优先级",
                List.of());
    }

    private List<AffectedRelocation> affected(List<SymbolReference> references) {
        return references.stream()
                .map(reference -> new AffectedRelocation(reference.id(), reference.objectName(),
                        reference.memberName(), reference.sectionName(), reference.offset(),
                        reference.addend(), reference.relocationType(), reference.symbolName(),
                        reference.requestedVersion(), reference.copy()))
                .toList();
    }

    private boolean isWeakReference(SymbolReference reference) {
        return "WEAK".equals(reference.binding());
    }

    private List<DependencyEdge> buildDependencyEdges(State state) {
        List<DependencyEdge> edges = new ArrayList<>();
        for (ParsedObject object : state.activeObjects) {
            for (String needed : object.neededDependencies()) {
                String requestedName = needed.startsWith("DT_NEEDED#") ? needed : needed;
                boolean satisfied = state.activeObjects.stream()
                        .anyMatch(candidate -> Objects.equals(candidate.displayName(), requestedName));
                edges.add(new DependencyEdge(object.id(), object.displayName(), null, requestedName,
                        requestedName, satisfied));
            }
            for (VersionNode node : object.versionNodes()) {
                if (node.kind() != VersionNode.Kind.REQUIREMENT) {
                    continue;
                }
                boolean satisfied = state.activeObjects.stream().flatMap(candidate ->
                                candidate.versionNodes().stream())
                        .anyMatch(definition -> definition.kind() == VersionNode.Kind.DEFINITION
                                && (definition.name().equals(node.name())
                                || definition.aliases().contains(node.name())));
                edges.add(new DependencyEdge(object.id(), object.displayName(), null,
                        node.dependencyName(), node.name(), satisfied));
            }
        }
        return List.copyOf(edges);
    }
}
