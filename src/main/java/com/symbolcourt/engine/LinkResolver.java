package com.symbolcourt.engine;

import com.symbolcourt.elf.Constants;
import com.symbolcourt.elf.ElfFile;
import com.symbolcourt.elf.ElfRelocation;
import com.symbolcourt.elf.ElfSection;
import com.symbolcourt.elf.ElfSymbol;
import com.symbolcourt.elf.VersionDef;
import com.symbolcourt.elf.VersionNeed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LinkResolver {

    private static final String[] DEFINITION_RANK = {
            "object-strong", "common", "object-weak", "shared-strong", "shared-weak"
    };

    private final List<InputItem> inputs;
    private final PlatformPolicy policy;
    private final String platformMachine;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final List<TimelineEvent> timeline = new ArrayList<>();
    private final List<Instance> instances = new ArrayList<>();
    private final List<DependencyEdge> dependencies = new ArrayList<>();
    private int eventSequence;

    public LinkResolver(List<InputItem> inputs, PlatformPolicy policy, String platformMachine) {
        this.inputs = inputs;
        this.policy = policy;
        this.platformMachine = platformMachine;
    }

    public ResolveResult resolve(String schemeId) {
        scanInputs();
        Map<String, List<Reference>> referencesByKey = collectReferences();
        Map<String, ResolutionEntry> entries = contestReferences(referencesByKey);
        List<SymbolTableRow> symbolTable = buildSymbolTable(entries);
        return new ResolveResult(schemeId, policy.name(), platformMachine, buildObjectViews(),
                dependencies, orderedEntries(entries, referencesByKey), symbolTable,
                List.copyOf(timeline), List.copyOf(diagnostics));
    }

    private static class Instance {
        String id;
        String archiveId;
        String memberName;
        String displayName;
        InputItem input;
        ElfFile elf;
        int orderIndex;
        boolean extracted;
        boolean shared;
        Set<String> discardedSections = new LinkedHashSet<>();
        Map<String, String> chosenComdats = new LinkedHashMap<>();
        final Map<Integer, String> comdatKeys = new LinkedHashMap<>();
        List<Provider> providers = new ArrayList<>();
    }

    private static class Provider {
        ElfSymbol symbol;
        String name;
        String version;
        boolean defaultVersion;
        String sectionName;
        int sectionIndex;
        int rank;
        long orderIndex;
        long address;
        long size;
        boolean comdat;
        String comdatKey;
        String exportKey;
        boolean comdatDiscard;
    }

    private static class Reference {
        String objectId;
        String objectName;
        String memberName;
        String symbol;
        String requiredVersion;
        boolean versionExplicit;
        int binding;
        boolean copy;
        long type;
        long offset;
        long addend;
        boolean hasAddend;
        String section;
    }

    private void scanInputs() {
        int order = 0;
        for (InputItem item : inputs) {
            if (item.corruptReason != null) {
                continue;
            }
            if (item.archive) {
                for (Map.Entry<String, ElfFile> entry : item.memberElves.entrySet()) {
                    ElfFile elf = entry.getValue();
                    Instance instance = new Instance();
                    instance.id = item.id + "::" + entry.getKey();
                    instance.archiveId = item.id;
                    instance.memberName = entry.getKey();
                    instance.displayName = item.displayName + "(" + entry.getKey() + ")";
                    instance.input = item;
                    instance.elf = elf;
                    instance.orderIndex = order++;
                    instance.shared = elf.type == Constants.ET_DYN;
                    instances.add(instance);
                }
                addDependencyEdges(item, null, "archive");
            } else {
                Instance instance = new Instance();
                instance.id = item.id;
                instance.memberName = null;
                instance.displayName = item.displayName;
                instance.input = item;
                instance.elf = item.elf;
                instance.orderIndex = order++;
                instance.shared = item.elf.type == Constants.ET_DYN;
                instance.extracted = true;
                instances.add(instance);
                addDependencyEdges(item, instance.id, "needed");
            }
        }

        for (Instance instance : instances) {
            if (isExplicit(instance)) {
                scanInstance(instance);
            }
        }

        for (InputItem item : inputs) {
            if (!item.archive) {
                continue;
            }
            extractArchive(item);
        }
        applyAllComdatDiscards();
    }

    private boolean isExplicit(Instance instance) {
        return instance.archiveId == null;
    }

    private void extractArchive(InputItem archiveItem) {
        List<Instance> members = instances.stream()
                .filter(instance -> archiveItem.id.equals(instance.archiveId))
                .collect(Collectors.toList());
        Set<String> extracted = new LinkedHashSet<>();
        List<Instance> scanQueue = new ArrayList<>();
        for (Instance instance : members) {
            if (isExplicit(instance) || instance.extracted) {
                extracted.add(instance.id);
            }
        }
        boolean changed = true;
        int pass = 0;
        while (changed) {
            changed = false;
            pass++;
            for (Instance candidate : members) {
                if (extracted.contains(candidate.id)) {
                    continue;
                }
                String provided = archiveProvides(candidate, scanQueue);
                if (provided != null) {
                    candidate.extracted = true;
                    extracted.add(candidate.id);
                    scanQueue.add(candidate);
                    scanInstance(candidate);
                    emitEvent("ARCHIVE_EXTRACT", "抽取 archive 成员 " + candidate.displayName,
                            "第 " + pass + " 轮抽取以满足 " + provided, candidate, provided, null,
                            symbolsOf(candidate), null);
                    changed = true;
                }
            }
            if (pass > members.size() + 2) {
                diagnostics.add(new Diagnostic("warn", "ARCHIVE_CYCLE_LIMIT",
                        "archive " + archiveItem.displayName + " 的依赖循环超过迭代上限", archiveItem.id));
                break;
            }
        }
        for (Instance instance : members) {
            if (!extracted.contains(instance.id) && !instance.extracted) {
                emitEvent("ARCHIVE_SKIP", "跳过 archive 成员 " + instance.displayName,
                        "没有未解析引用需要该成员", instance, null, null, List.of(), null);
            }
        }
    }

    private String archiveProvides(Instance candidate, List<Instance> alreadyExtracted) {
        scanProvidersOnly(candidate);
        Set<String> wanted = new LinkedHashSet<>();
        for (Instance included : activeInstances()) {
            if (included == candidate) {
                continue;
            }
            for (Provider provider : included.providers) {
                if (provider.rank == 1 && provider.symbol.isUndefined()) {
                    wanted.add(key(provider.name, provider.version));
                }
            }
        }
        for (Instance other : alreadyExtracted) {
            for (Provider provider : other.providers) {
                if (provider.rank == 1 && provider.symbol.isUndefined()) {
                    wanted.add(key(provider.name, provider.version));
                }
            }
        }
        for (Provider provider : candidate.providers) {
            if (!provider.symbol.isUndefined() && !provider.comdatDiscard) {
                if (provider.rank == 1 || (policy.commonTriggersExtraction() && provider.rank == 2)) {
                    if (wanted.contains(key(provider.name, provider.version))) {
                        return provider.name
                                + (provider.version == null ? "" : "@" + provider.version);
                    }
                }
            }
        }
        return null;
    }

    private List<Instance> activeInstances() {
        return instances.stream().filter(i -> isExplicit(i) || i.extracted).collect(Collectors.toList());
    }

    private void addDependencyEdges(InputItem item, String fromId, String kind) {
        ElfFile representative = item.archive
                ? item.memberElves.values().stream().findFirst().orElse(null)
                : item.elf;
        if (representative == null) {
            return;
        }
        if (!item.archive) {
            for (String needed : representative.needed) {
                dependencies.add(new DependencyEdge(fromId, needed, "needed", null));
            }
        }
        for (VersionNeed need : representative.versionNeeds) {
            for (VersionNeed.NeedEntry entry : need.entries) {
                dependencies.add(new DependencyEdge(item.archive ? item.id : fromId,
                        need.fileName, "version-need", entry.name));
            }
        }
    }

    private void scanProvidersOnly(Instance instance) {
        if (!instance.providers.isEmpty()) {
            return;
        }
        buildProviders(instance);
    }

    private void scanInstance(Instance instance) {
        buildProviders(instance);
        resolveComdats(instance);
        emitEvent(instance.shared ? "LOAD_SHARED" : "LOAD_OBJECT",
                (instance.shared ? "加载共享对象 " : "读入对象 ") + instance.displayName,
                "该输入提供 " + countProviders(instance) + " 个全局定义",
                instance, null, null, symbolsOf(instance), null);
    }

    private int countProviders(Instance instance) {
        return (int) instance.providers.stream().filter(p -> !p.symbol.isUndefined()).count();
    }

    private void buildProviders(Instance instance) {
        ElfFile elf = instance.elf;
        List<ElfSymbol> table = elf.type == Constants.ET_REL ? elf.staticSymbols : elf.dynamicSymbols;
        if (table == null) {
            table = List.of();
        }
        for (int i = 0; i < table.size(); i++) {
            ElfSymbol symbol = table.get(i);
            if (i == 0 || symbol.name == null || symbol.name.isEmpty()) {
                continue;
            }
            if (symbol.type == Constants.STT_SECTION || symbol.type == Constants.STT_FILE) {
                continue;
            }
            Provider provider = new Provider();
            provider.symbol = symbol;
            provider.name = symbol.name;
            provider.size = symbol.size;
            provider.address = symbol.value;
            provider.sectionIndex = symbol.sectionIndex;
            provider.sectionName = elf.sectionName(symbol.sectionIndex);
            provider.version = versionName(elf, symbol);
            provider.defaultVersion = provider.version == null || !symbol.hiddenBit();
            if (symbol.local || symbol.binding == Constants.STB_LOCAL) {
                continue;
            }
            if (symbol.isUndefined()) {
                provider.rank = 6;
            } else if (symbol.isCommon()) {
                provider.rank = 2;
            } else if (symbol.binding == Constants.STB_WEAK) {
                provider.rank = instance.shared ? 5 : 3;
            } else {
                provider.rank = instance.shared ? 4 : 1;
            }
            ElfSection section = elf.section(symbol.sectionIndex);
            if (section != null && (section.flags & Constants.SHF_GROUP) != 0) {
                provider.comdat = true;
                provider.comdatKey = instance.comdatKeys.get(symbol.sectionIndex);
            }
            instance.providers.add(provider);
        }
    }

    private String versionName(ElfFile elf, ElfSymbol symbol) {
        int raw = symbol.versionIndex;
        int key = raw & 0x7fff;
        if (key <= 1) {
            return null;
        }
        for (VersionDef def : elf.versionDefs) {
            if (def.ndx == key) {
                return def.name;
            }
        }
        for (VersionNeed need : elf.versionNeeds) {
            for (VersionNeed.NeedEntry entry : need.entries) {
                if (entry.other == key) {
                    return entry.name;
                }
            }
        }
        return null;
    }

}

    private void resolveComdats(Instance instance) {
        ElfFile elf = instance.elf;
        byte[] content = contentOf(instance);
        for (int groupIndex = 0; groupIndex < elf.sections.size(); groupIndex++) {
            ElfSection group = elf.sections.get(groupIndex);
            if (group.type != Constants.SHT_GROUP) {
                continue;
            }
            if (group.info <= 0 || group.info >= (elf.type == Constants.ET_REL
                    ? elf.staticSymbols.size() : elf.dynamicSymbols.size())) {
                diagnostics.add(new Diagnostic("warn", "BAD_COMDAT_SIGNATURE",
                        instance.displayName + " 的组节 sh_info 无效", instance.id));
                continue;
            }
            List<ElfSymbol> symbolTable = elf.type == Constants.ET_REL
                    ? elf.staticSymbols : elf.dynamicSymbols;
            ElfSymbol signatureSymbol = symbolTable.get(group.info);
            String signature = signatureSymbol.name;
            boolean isComdat = readGroupFlags(content, elf, group) == Constants.GRP_COMDAT;
            List<Integer> members = readGroupMembers(content, elf, group);
            if (!isComdat) {
                for (Integer member : members) {
                    instance.comdatKeys.put(member, null);
                }
                continue;
            }
            for (Integer member : members) {
                instance.comdatKeys.put(member, signature);
            }
        }
    }

    private int readGroupFlags(byte[] content, ElfFile elf, ElfSection group) {
        com.symbolcourt.elf.ByteReader reader = new com.symbolcourt.elf.ByteReader(content, elf.bigEndian, 0);
        byte[] body = reader.sliceAt(group.offset, Math.min(group.size, 4));
        return new com.symbolcourt.elf.ByteReader(body, elf.bigEndian, 0).u32();
    }

    private List<Integer> readGroupMembers(byte[] content, ElfFile elf, ElfSection group) {
        com.symbolcourt.elf.ByteReader reader = new com.symbolcourt.elf.ByteReader(content, elf.bigEndian, 0);
        byte[] body = reader.sliceAt(group.offset, group.size);
        com.symbolcourt.elf.ByteReader groupReader = new com.symbolcourt.elf.ByteReader(body, elf.bigEndian, 0);
        groupReader.u32();
        List<Integer> members = new ArrayList<>();
        while (groupReader.remaining() >= 4) {
            int member = groupReader.u32();
            if (member >= 0 && member < elf.sections.size()) {
                members.add(member);
            }
        }
        return members;
    }

    private byte[] contentOf(Instance instance) {
        if (instance.memberName == null) {
            return instance.input.content;
        }
        return instance.input.memberList.stream()
                .filter(member -> member.name().equals(instance.memberName))
                .findFirst()
                .orElseThrow()
                .content();
    }


    private void applyAllComdatDiscards() {
        Map<String, Instance> chosenBySignature = new LinkedHashMap<>();
        for (Instance active : activeInstances()) {
            Set<String> signatures = new LinkedHashSet<>(active.comdatKeys.values());
            signatures.remove(null);
            for (String signature : signatures) {
                chosenBySignature.putIfAbsent(signature, active);
            }
        }
        for (Instance instance : activeInstances()) {
            for (Map.Entry<Integer, String> entry : instance.comdatKeys.entrySet()) {
                String signature = entry.getValue();
                if (signature == null) {
                    continue;
                }
                Instance chosen = chosenBySignature.get(signature);
                if (chosen != instance) {
                    String sectionName = instance.elf.sectionName(entry.getKey());
                    instance.discardedSections.add(sectionName);
                    emitEvent("COMDAT_DROP",
                            "丢弃 COMDAT 节 " + sectionName,
                            "签名 " + signature + " 已由 "
                                    + (chosen == null ? "未知对象" : chosen.displayName) + " 选中",
                            instance, null, null, List.of(), List.of(sectionName));
                } else {
                    emitEvent("COMDAT_KEEP",
                            "保留 COMDAT 节 " + instance.elf.sectionName(entry.getKey()),
                            "签名 " + signature,
                            instance, signature, null, List.of(), null);
                }
            }
            for (Provider provider : instance.providers) {
                if (provider.comdat && instance.discardedSections.contains(provider.sectionName)) {
                    provider.comdatDiscard = true;
                }
            }
        }
    }

    private Map<String, List<Reference>> collectReferences() {
        Map<String, List<Reference>> references = new LinkedHashMap<>();
        for (Instance instance : activeInstances()) {
            ElfFile elf = instance.elf;
            List<ElfSymbol> symbolTable = elf.type == Constants.ET_REL
                    ? elf.staticSymbols : elf.dynamicSymbols;
            for (ElfRelocation relocation : elf.relocations) {
                if (instance.discardedSections.contains(elf.sectionName(relocation.targetSectionIndex))) {
                    continue;
                }
                if (relocation.symbolIndex <= 0 || relocation.symbolIndex >= symbolTable.size()) {
                    continue;
                }
                ElfSymbol symbol = symbolTable.get(relocation.symbolIndex);
                if (symbol.name == null || symbol.name.isEmpty()) {
                    continue;
                }
                Reference reference = new Reference();
                reference.objectId = instance.id;
                reference.objectName = instance.displayName;
                reference.memberName = instance.memberName;
                reference.symbol = symbol.name;
                reference.type = relocation.type;
                reference.offset = relocation.offset;
                reference.addend = relocation.addend;
                reference.hasAddend = relocation.hasAddend;
                reference.section = elf.sectionName(relocation.targetSectionIndex);
                reference.binding = symbol.binding;
                reference.copy = relocation.copy;
                if (symbol.isUndefined()) {
                    reference.requiredVersion = versionName(elf, symbol);
                    reference.versionExplicit = reference.requiredVersion != null
                            && (symbol.versionIndex & 0x7fff) > 1;
                } else {
                    reference.requiredVersion = null;
                    reference.versionExplicit = false;
                }
                references.computeIfAbsent(referenceKey(symbol.name, reference.requiredVersion),
                        key -> new ArrayList<>()).add(reference);
            }
        }
        return references;
    }

    private static String referenceKey(String name, String version) {
        return name + "\u0000" + (version == null ? "" : version);
    }

    private static String key(String name, String version) {
        return referenceKey(name, version);
    }

    private Map<String, ResolutionEntry> contestReferences(Map<String, List<Reference>> references) {
        Map<String, ResolutionEntry> entries = new LinkedHashMap<>();
        for (Map.Entry<String, List<Reference>> grouped : references.entrySet()) {
            List<Reference> refs = grouped.getValue();
            Reference first = refs.get(0);
            List<CandidateView> allCandidates = new ArrayList<>();
            List<Provider> eligible = new ArrayList<>();
            for (Instance instance : activeInstances()) {
                for (Provider provider : instance.providers) {
                    if (!provider.name.equals(first.symbol)) {
                        continue;
                    }
                    CandidateView view = evaluateCandidate(instance, provider, first, refs);
                    allCandidates.add(view);
                    if (view.rejectedReason == null && !provider.symbol.isUndefined()) {
                        eligible.add(provider);
                    }
                }
            }
            List<Provider> winners = selectWinners(first, refs, eligible, allCandidates);
            String status;
            String detail;
            if (!winners.isEmpty() && winners.size() > 1) {
                status = "AMBIGUOUS";
                detail = "存在 " + winners.size() + " 个同等候选，无法仅按显式加载顺序裁决";
            } else if (!winners.isEmpty()) {
                status = "RESOLVED";
                detail = null;
            } else {
                boolean allWeak = refs.stream().allMatch(ref -> ref.binding == Constants.STB_WEAK);
                if (allWeak) {
                    status = "WEAK_NULL";
                    detail = "只有弱引用且没有提供者，按 0 地址绑定";
                } else if (hasRejection(allCandidates, "VERSION_MISMATCH")
                        || hasRejection(allCandidates, "VERSION_UNMET")) {
                    status = "UNRESOLVED_VERSION";
                    detail = "存在同名符号但没有满足版本需求的定义";
                } else if (hasRejection(allCandidates, "HIDDEN")) {
                    status = "UNRESOLVED_VISIBILITY";
                    detail = "同名定义不可见（hidden/local）";
                } else {
                    status = "UNRESOLVED";
                    detail = "无提供者";
                }
            }
            boolean copyRelocation = refs.stream().anyMatch(ref -> ref.copy);
            if (copyRelocation && status.equals("RESOLVED")) {
                Provider winner = winners.get(0);
                Instance winnerInstance = instanceOf(winner);
                if (winnerInstance.shared
                        && winner.symbol.visibility == Constants.STV_PROTECTED
                        && !policy.copyRelocAgainstProtected()) {
                    status = "COPY_PROTECTED_REJECTED";
                    detail = "平台策略禁止对 protected 共享数据生成 copy relocation";
                } else if (winner.rank >= 4) {
                    detail = detail == null ? "由 copy relocation 截取共享数据副本" : detail;
                }
            }
            Set<String> winnerIds = winners.stream().map(provider -> instanceOf(provider).id)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<Candidate> candidateViews = allCandidates.stream()
                    .map(view -> toCandidate(view, winnerIds))
                    .sorted(Comparator.comparingInt(candidate -> candidateIndex(candidate, allCandidates)))
                    .toList();
            List<AffectedRelocation> relocations = refs.stream()
                    .map(this::toRelocationView)
                    .toList();
            entries.put(grouped.getKey(), new ResolutionEntry(grouped.getKey(), first.symbol,
                    first.requiredVersion, status, detail, candidateViews,
                    List.copyOf(winnerIds), relocations));
        }
        return entries;
    }

    private static class CandidateView {
        Instance instance;
        Provider provider;
        String rejectedReason;
        long sortKey;
    }

    private CandidateView evaluateCandidate(Instance instance, Provider provider, Reference first,
                                            List<Reference> references) {
        CandidateView view = new CandidateView();
        view.instance = instance;
        view.provider = provider;
        if (provider.comdatDiscard) {
            view.rejectedReason = "COMDAT_DISCARDED";
        } else if (provider.symbol.visibility == Constants.STV_HIDDEN
                || provider.symbol.visibility == Constants.STV_INTERNAL) {
            boolean ownReference = references.stream().allMatch(ref -> ref.objectId.equals(instance.id));
            if (!ownReference) {
                view.rejectedReason = "HIDDEN";
            }
        } else if (provider.symbol.isUndefined()) {
            view.rejectedReason = "UNDEFINED";
        } else {
            boolean versionMismatch = false;
            boolean versionUnmet = false;
            for (Reference ref : references) {
                if (ref.requiredVersion == null) {
                    if (ref.versionExplicit) {
                        continue;
                    }
                    if (provider.version != null && !provider.defaultVersion) {
                        versionMismatch = true;
                    }
                } else if (provider.version == null || !provider.version.equals(ref.requiredVersion)) {
                    versionMismatch = true;
                }
            }
            if (versionMismatch && policy.rejectVersionMismatch()) {
                view.rejectedReason = "VERSION_MISMATCH";
            }
            if (!references.stream().allMatch(ref -> ref.objectId.equals(instance.id))
                    && provider.symbol.visibility == Constants.STV_PROTECTED
                    && instance.shared
                    && references.stream().anyMatch(ref -> ref.copy && !ref.objectId.equals(instance.id))) {
                if (!policy.copyRelocAgainstProtected()) {
                    view.rejectedReason = view.rejectedReason == null
                            ? "PROTECTED_COPY" : view.rejectedReason;
                }
            }
        }
        return view;
    }

    private List<Provider> selectWinners(Reference first, List<Reference> refs,
                                         List<Provider> eligible, List<CandidateView> allCandidates) {
        if (eligible.isEmpty()) {
            return List.of();
        }
        boolean directBinding = refs.stream().allMatch(ref -> {
            for (CandidateView view : allCandidates) {
                if (view.instance.id.equals(ref.objectId)
                        && view.provider.name.equals(ref.symbol)
                        && !view.provider.symbol.isUndefined()
                        && view.rejectedReason == null
                        && view.instance.elf.type == Constants.ET_REL) {
                    return true;
                }
            }
            return false;
        });
        Comparator<Provider> comparator = Comparator
                .comparingInt((Provider provider) -> rankOf(provider))
                .thenComparingLong(provider -> instanceOf(provider).orderIndex);
        List<Provider> sorted = eligible.stream().sorted(comparator).collect(Collectors.toList());
        Provider best = sorted.get(0);
        int bestRank = rankOf(best);
        long bestOrder = instanceOf(best).orderIndex;
        List<Provider> ties = sorted.stream()
                .filter(provider -> rankOf(provider) == bestRank
                        && instanceOf(provider).orderIndex == bestOrder)
                .collect(Collectors.toList());
        if (ties.size() > 1) {
            boolean duplicateStrong = ties.stream().allMatch(provider -> rankOf(provider) == 1);
            if (duplicateStrong) {
                if (policy.allowMultipleDefinition()) {
                    return List.of(ties.get(0));
                }
                diagnostics.add(new Diagnostic("error", "DUPLICATE_SYMBOL",
                        "符号 " + first.symbol + " 存在多个同等强定义: "
                                + ties.stream().map(provider -> instanceOf(provider).displayName)
                                .collect(Collectors.joining(", ")),
                        null));
                return ties;
            }
            return ties;
        }
        if (!policy.sharedFirstWins()) {
            Provider firstDefinition = sorted.stream()
                    .min(Comparator.comparingLong(provider -> instanceOf(provider).orderIndex))
                    .orElse(best);
            return List.of(firstDefinition);
        }
        return List.of(best);
    }

    private int rankOf(Provider provider) {
        int base = provider.rank;
        if (provider.comdat) {
            base += 0;
        }
        return base;
    }

    private boolean hasRejection(List<CandidateView> views, String reason) {
        return views.stream().anyMatch(view -> reason.equals(view.rejectedReason));
    }

    private Instance instanceOf(Provider provider) {
        for (Instance instance : activeInstances()) {
            if (instance.providers.contains(provider)) {
                return instance;
            }
        }
        throw new IllegalStateException("provider owner missing");
    }

    private Candidate toCandidate(CandidateView view, Set<String> winnerIds) {
        Instance instance = view.instance;
        Provider provider = view.provider;
        return new Candidate(instance.id, instance.displayName, instance.memberName,
                provider.sectionName, bindingName(provider), visibilityName(provider),
                provider.version, provider.defaultVersion, provider.address, provider.size,
                winnerIds.contains(instance.id), view.rejectedReason);
    }

    private int candidateIndex(Candidate candidate, List<CandidateView> views) {
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).instance.id.equals(candidate.objectId())
                    && views.get(i).provider == providersByIdentity(views, candidate).get(i)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private List<Provider> providersByIdentity(List<CandidateView> views, Candidate candidate) {
        return views.stream().map(view -> view.provider).toList();
    }

    private AffectedRelocation toRelocationView(Reference reference) {
        return new AffectedRelocation(reference.objectId, reference.objectName, reference.memberName,
                reference.section, reference.offset, reference.type, relocKind(reference.type),
                reference.symbol, reference.addend, reference.copy,
                reference.binding == Constants.STB_WEAK ? "WEAK" : "STRONG");
    }

    private String relocKind(long type) {
        return switch (platformMachine) {
            case "x86_64" -> switch ((int) type) {
                case 1 -> "R_X86_64_64";
                case 2 -> "R_X86_64_PC32";
                case 4 -> "R_X86_64_PLT32";
                case 5 -> "R_X86_64_COPY";
                case 7 -> "R_X86_64_JUMP_SLOT";
                case 8 -> "R_X86_64_RELATIVE";
                case 10 -> "R_X86_64_32";
                default -> "R_X86_64_" + type;
            };
            case "aarch64" -> switch ((int) type) {
                case 257 -> "R_AARCH64_ABS64";
                case 1024 -> "R_AARCH64_COPY";
                case 1025 -> "R_AARCH64_GLOB_DAT";
                case 1026 -> "R_AARCH64_JUMP_SLOT";
                default -> "R_AARCH64_" + type;
            };
            case "riscv64" -> switch ((int) type) {
                case 2 -> "R_RISCV_32";
                case 8 -> "R_RISCV_COPY";
                case 11 -> "R_RISCV_JUMP_SLOT";
                case 24 -> "R_RISCV_64";
                default -> "R_RISCV_" + type;
            };
            case "i386" -> switch ((int) type) {
                case 1 -> "R_32";
                case 5 -> "R_386_COPY";
                case 7 -> "R_386_JMP_SLOT";
                default -> "R_386_" + type;
            };
            default -> "R_" + type;
        };
    }
