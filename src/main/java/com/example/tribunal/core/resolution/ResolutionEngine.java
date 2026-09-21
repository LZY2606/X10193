package com.example.tribunal.core.resolution;

import com.example.tribunal.core.elf.ElfModels;
import com.example.tribunal.core.elf.ElfModels.*;
import com.example.tribunal.core.model.TribunalModels.Module;
import com.example.tribunal.core.model.TribunalModels.*;
import java.util.*;
import java.util.stream.Collectors;

public class ResolutionEngine {
    private static final short SHN_ABS_LOCAL = (short) 0xfff1;
    private static final short SHN_COMMON_LOCAL = (short) 0xfff2;

    private static class Unit {
        Module module;
        final ElfObject object;
        final Map<Integer, Boolean> activeSection = new HashMap<>();
        final Map<Integer, String> discardReason = new HashMap<>();
        final Map<String, List<Symbol>> providers = new LinkedHashMap<>();
        final Map<String, ReferenceGroup> referenceGroups = new LinkedHashMap<>();
        boolean extracted;
        Unit(Module module, ElfObject object, boolean extracted) { this.module = module; this.object = object; this.extracted = extracted; }
    }
    private record Symbol(ElfSymbol elf, CandidateClass candidateClass) {}
    private record Reference(ElfSymbol symbol, SymbolKey key, boolean weak, List<RelocationRef> relocations) {}
    private record ReferenceGroup(SymbolKey key, boolean weak, Map<Integer, Reference> bySymbol) {}

    private final List<BinaryLoader.LoadedBinary> binaries;
    private final PlatformPolicy policy;
    private final String schemeId;
    private final List<Event> events = new ArrayList<>();
    private final LinkedHashMap<ModuleKey, Unit> units = new LinkedHashMap<>();
    private final List<ElfObject> archiveMembers = new ArrayList<>();
    private final Map<String, ElfObject> sharedBySoname = new HashMap<>();
    private int step = 1;
    private int moduleOrder = 1;
    private int candidateSeq = 1;
    private int referenceSeq = 1;

    public ResolutionEngine(List<BinaryLoader.LoadedBinary> binaries, PlatformPolicy policy, String schemeId) {
        this.binaries = binaries;
        this.policy = policy;
        this.schemeId = schemeId;
    }

    public Scheme resolve(String name, String createdAt, List<LoadedInput> inputs) {
        loadSharedLibraries();
        loadExplicitObjects();
        prepareArchiveMembers();
        initializeSections();
        collectReferences();
        fixedPointArchiveExtraction();
        collectProviders();
        List<SymbolResolution> resolutions = resolveReferences();
        VersionGraph graph = buildVersionGraph();
        List<Module> modules = units.values().stream().map(u -> u.module).toList();
        return new Scheme(schemeId, name, createdAt, policy.name(), inputs.size(), inputs, modules,
                List.copyOf(resolutions), List.copyOf(events), graph);
    }

    private void loadSharedLibraries() {
        for (BinaryLoader.LoadedBinary binary : binaries) {
            for (ElfObject object : binary.objects()) {
                if (object.type() == ElfType.SHARED) registerShared(object);
            }
        }
    }

    private void registerShared(ElfObject object) {
        String soname = object.soname().isBlank() ? baseName(object.displayName()) : object.soname();
        sharedBySoname.putIfAbsent(soname, object);
        sharedBySoname.putIfAbsent(baseName(object.displayName()), object);
    }

    private void loadExplicitObjects() {
        for (BinaryLoader.LoadedBinary binary : binaries) {
            for (ElfObject object : binary.objects()) {
                if (object.type() == ElfType.SHARED) {
                    addUnit(object, ModuleKind.EXPLICIT_SHARED, true, binary.input().id(), moduleOrder++, null);
                } else if (object.type() == ElfType.RELOCATABLE || object.type() == ElfType.EXECUTABLE) {
                    addUnit(object, ModuleKind.EXPLICIT_OBJECT, true, binary.input().id(), moduleOrder++, null);
                }
            }
            if (binary.input().parseError() != null) {
                event("parse", "隔离损坏对象", binary.input().fileName() + ": " + binary.input().parseError(),
                        binary.input().fileName(), null);
            }
        }
        Deque<ElfObject> queue = new ArrayDeque<>(units.values().stream().filter(u -> u.object.type() == ElfType.SHARED)
                .map(u -> u.object).toList());
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            ElfObject current = queue.poll();
            for (String need : current.needed()) {
                ElfObject dependency = sharedBySoname.get(need);
                if (dependency == null) {
                    event("dependency", "缺少动态依赖", need + " 未在导入对象中出现", current.displayName(), null);
                    continue;
                }
                String soname = dependency.soname().isBlank() ? baseName(dependency.displayName()) : dependency.soname();
                ModuleKey key = keyFor(dependency, ModuleKind.SHARED_DEPENDENCY, binaryIdFor(dependency), soname);
                if (visited.add(soname) && !units.containsKey(key)) {
                    addUnit(dependency, ModuleKind.SHARED_DEPENDENCY, true, binaryIdFor(dependency), moduleOrder++, null);
                    queue.add(dependency);
                }
            }
        }
    }

    private void prepareArchiveMembers() {
        int memberOrder = 0;
        for (BinaryLoader.LoadedBinary binary : binaries) {
            for (ElfObject object : binary.archiveMembers()) {
                if (object.displayName().contains("(")) {
                    ModuleKey key = new ModuleKey(binary.input().id(), memberName(object.displayName()), false);
                    Module module = new Module(key, object.displayName(), ModuleKind.ARCHIVE_MEMBER, Integer.MAX_VALUE,
                            object.soname(), object.needed(), false, binary.input().id(), memberOrder++);
                    Unit unit = new Unit(module, object, false);
                    units.put(key, unit);
                    archiveMembers.add(object);
                }
            }
        }
    }

    private void initializeSections() {
        for (Unit unit : units.values()) {
            for (Section section : unit.object.sections()) unit.activeSection.put(section.index(), true);
            Map<String, Unit> signatureWinner = new LinkedHashMap<>();
            for (Unit candidate : units.values()) {
                if (!candidate.extracted) continue;
                for (Group group : candidate.object.groups()) {
                    if (!group.comdat()) continue;
                    Unit winner = signatureWinner.putIfAbsent(group.signature(), candidate);
                    if (winner == null) {
                        event("comdat", "保留 COMDAT", group.signature() + " 首次由 " + candidate.module.displayName() + " 提供",
                                candidate.module.displayName(), group.signature());
                    } else if (candidate != winner) {
                        discardGroup(candidate, group, "重复 COMDAT，保留 " + winner.module.displayName());
                    }
                }
            }
        }
    }

    private void discardGroup(Unit unit, Group group, String reason) {
        for (Integer sectionIndex : group.members()) {
            unit.activeSection.put(sectionIndex, false);
            unit.discardReason.put(sectionIndex, reason);
        }
        event("comdat", "丢弃 COMDAT", group.signature() + "：" + reason, unit.module.displayName(), group.signature());
    }

    private void collectReferences() {
        for (Unit unit : units.values()) {
            if (unit.extracted) collectReferencesFromUnit(unit);
        }
    }

    private void collectReferencesFromUnit(Unit unit) {
        unit.referenceGroups.clear();
        for (ElfSymbol symbol : unit.object.symbols()) {
            List<RelocationRef> relocations = new ArrayList<>();
            boolean mentioned = false;
            for (Relocation relocation : unit.object.relocations()) {
                if (relocation.symbolIndex() != symbol.index()) continue;
                if (!Boolean.TRUE.equals(unit.activeSection.get(relocation.sectionIndex()))) continue;
                mentioned = true;
                relocations.add(new RelocationRef("R" + referenceSeq++, unit.module.key(), relocation.sectionIndex(),
                        relocation.sectionName(), relocation.offset(), relocation.type(), relocation.addend(),
                        symbol.index(), symbol.name(), relocation.copy(), relocation.pcrel()));
            }
            if (relocations.isEmpty()) continue;
            boolean localReference = symbol.binding() == Binding.LOCAL
                    || (ElfModels.definedSection(symbol) && symbol.visibility() == Visibility.HIDDEN)
                    || (ElfModels.definedSection(symbol) && symbol.visibility() == Visibility.PROTECTED
                    && unit.object.type() != ElfType.SHARED);
            if (!localReference && symbol.sectionIndex() != 0 && unit.object.type() != ElfType.SHARED) continue;
            SymbolKey key = key(symbol);
            ReferenceGroup group = unit.referenceGroups.computeIfAbsent(localReference ? "local:" + symbol.index() : groupId(symbol),
                    ignored -> new ReferenceGroup(key, symbol.binding() == Binding.WEAK || !localReference && false,
                            new LinkedHashMap<>()));
            if (!mentioned && !localReference && symbol.sectionIndex() == 0) relocations.add(syntheticRef(unit, symbol));
            group.bySymbol().put(symbol.index(),
                    new Reference(symbol, key, localReference || symbol.binding() == Binding.WEAK, List.copyOf(relocations)));
        }
    }

    private void fixedPointArchiveExtraction() {
        boolean changed = true;
        Set<ModuleKey> extractedThisPass = new HashSet<>();
        while (changed) {
            changed = false;
            extractedThisPass.clear();
            for (Unit archiveUnit : units.values()) {
                if (archiveUnit.extracted || archiveUnit.module.kind() != ModuleKind.ARCHIVE_MEMBER) continue;
                Set<String> requests = activeStrongRequests();
                boolean extracts = archiveUnit.object.symbols().stream().anyMatch(symbol ->
                        ElfModels.definedSection(symbol) && symbol.binding() != Binding.LOCAL && requests.contains(request(key(symbol))));
                if (extracts) {
                    archiveUnit.extracted = true;
                    archiveUnit.module = new Module(archiveUnit.module.key(), archiveUnit.module.displayName(),
                            archiveUnit.module.kind(), moduleOrder++, archiveUnit.module.soname(),
                            archiveUnit.module.needed(), true, archiveUnit.module.archiveInputId(),
                            archiveUnit.module.memberOrder());
                    extractedThisPass.add(archiveUnit.module.key());
                    activateArchiveSections(archiveUnit);
                    collectReferencesFromUnit(archiveUnit);
                    event("archive", "抽取 archive member", archiveUnit.module.displayName() + " 被未满足强引用惰性抽取",
                            archiveUnit.module.displayName(), null);
                    changed = true;
                }
            }
        }
        for (Unit unit : units.values()) {
            if (unit.module.kind() == ModuleKind.ARCHIVE_MEMBER && !unit.extracted) {
                event("archive", "保留 archive member", unit.module.displayName() + " 未被强引用抽取",
                        unit.module.displayName(), null);
            }
        }
    }

    private void activateArchiveSections(Unit unit) {
        Map<String, Unit> winner = new HashMap<>();
        for (Unit candidate : units.values()) {
            if (!candidate.extracted) continue;
            for (Group group : candidate.object.groups()) {
                if (group.comdat() && !winner.containsKey(group.signature())) winner.put(group.signature(), candidate);
            }
        }
        for (Group group : unit.object.groups()) {
            if (!group.comdat()) continue;
            Unit first = winner.get(group.signature());
            if (first != null && first != unit) discardGroup(unit, group, "重复 COMDAT，保留 " + first.module.displayName());
            else event("comdat", "保留 COMDAT", group.signature() + " 由后抽取的 " + unit.module.displayName() + " 首次提供",
                    unit.module.displayName(), group.signature());
        }
    }

    private Set<String> activeStrongRequests() {
        Set<String> requests = new LinkedHashSet<>();
        for (Unit unit : units.values()) {
            if (!unit.extracted) continue;
            for (ReferenceGroup group : unit.referenceGroups.values()) {
                if (!group.weak()) requests.add(request(group.key()));
            }
        }
        return requests;
    }

    private boolean symbolSectionDiscarded(Unit unit, ElfSymbol symbol) {
        return symbol.sectionIndex() < Short.toUnsignedInt(SHN_ABS_LOCAL)
                && Boolean.FALSE.equals(unit.activeSection.get(symbol.sectionIndex()));
    }

    private void collectProviders() {
        for (Unit unit : units.values()) {
            if (!unit.extracted) continue;
            for (ElfSymbol symbol : unit.object.symbols()) {
                if (symbol.binding() == Binding.LOCAL) continue;
                if (symbol.sectionIndex() == 0 || symbol.sectionIndex() == Short.toUnsignedInt(SHN_ABS_LOCAL)) continue;
                if (symbolSectionDiscarded(unit, symbol)) {
                    putProvider(unit, symbol, CandidateClass.DISCARDED);
                    continue;
                }
                if (symbol.sectionIndex() == Short.toUnsignedInt(SHN_COMMON_LOCAL)) {
                    putProvider(unit, symbol, CandidateClass.COMMON);
                    continue;
                }
                CandidateClass candidateClass = unit.object.type() == ElfType.SHARED ? CandidateClass.SHARED
                        : symbol.binding() == Binding.WEAK ? CandidateClass.WEAK : CandidateClass.STRONG;
                putProvider(unit, symbol, candidateClass);
                if (symbolSectionDiscarded(unit, symbol)) putProvider(unit, symbol, CandidateClass.DISCARDED);
            }
        }
    }

    private void putProvider(Unit unit, ElfSymbol symbol, CandidateClass candidateClass) {
        unit.providers.computeIfAbsent(providerRequest(symbol), ignored -> new ArrayList<>()).add(new Symbol(symbol, candidateClass));
    }

    private List<SymbolResolution> resolveReferences() {
        record Request(Unit unit, ReferenceGroup group) {}
        Map<String, List<Request>> grouped = new LinkedHashMap<>();
        for (Unit unit : units.values()) {
            if (!unit.extracted) continue;
            for (ReferenceGroup group : unit.referenceGroups.values()) {
                boolean local = group.bySymbol().values().stream().findFirst().map(r -> r.symbol().binding() == Binding.LOCAL).orElse(false);
                String id = local ? unit.module.key().display() + "\0" + group.key().display() + "\0LOCAL"
                        : unit.module.key().display() + "\0" + request(group.key());
                grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new Request(unit, group));
            }
        }
        List<SymbolResolution> resolutions = new ArrayList<>();
        for (List<Request> requests : grouped.values()) {
            Request first = requests.get(0);
            boolean local = first.group.bySymbol().values().stream().findFirst()
                    .map(r -> r.symbol().binding() == Binding.LOCAL).orElse(false);
            List<RelocationRef> relocations = requests.stream().flatMap(r -> r.group.bySymbol().values().stream())
                    .flatMap(reference -> reference.relocations().stream()).toList();
            boolean weak = requests.stream().allMatch(r -> r.group.weak());
            if (local) resolutions.add(resolveLocal(first.unit, first.group, relocations));
            else resolutions.add(resolveGlobal(first.unit, first.group, relocations, weak));
        }
        return resolutions.stream().sorted(Comparator.comparing(r -> r.key().display())).toList();
    }

    private SymbolResolution resolveLocal(Unit requester, ReferenceGroup group, List<RelocationRef> relocations) {
        Reference reference = group.bySymbol().values().iterator().next();
        ElfSymbol symbol = reference.symbol();
        Candidate candidate = new Candidate("C" + candidateSeq++, requester.module.key(), symbol.name(),
                symbol.versionName(), symbol.versionDefault(), symbol.binding().name(), symbol.visibility().name(),
                symbol.kind().name(), CandidateClass.LOCAL, symbol.value(), symbol.size(), symbol.sectionIndex(),
                symbol.sectionName(), false, EliminationReason.KEPT, "同一对象内的局部符号", 100);
        return new SymbolResolution(group.key(), Outcome.LOCAL, candidate, List.of(candidate), relocations,
                "局部绑定不进入全局符号裁决", List.of());
    }

    private SymbolResolution resolveGlobal(Unit requester, ReferenceGroup group, List<RelocationRef> relocations,
                                           boolean weak) {
        boolean copy = relocations.stream().anyMatch(RelocationRef::copy);
        List<Candidate> candidates = new ArrayList<>();
        List<String> aliases = aliasNames(group.key());
        for (Unit provider : units.values()) {
            if (!provider.extracted) continue;
            for (List<Symbol> providerSymbols : provider.providers.values()) {
              for (Symbol symbolEntry : providerSymbols) {
                ElfSymbol symbol = symbolEntry.elf();
                if (!symbol.name().equals(group.key().name())) continue;
                Candidate candidate = evaluateCandidate(requester, provider, symbol, symbolEntry.candidateClass(),
                        group.key(), copy, aliases);
                if (candidate != null) candidates.add(candidate);
              }
            }
        }
        addUnextractedArchiveCandidates(requester, group.key(), copy, candidates, aliases);
        List<Candidate> kept = candidates.stream().filter(c -> c.eliminationReason() == EliminationReason.KEPT).toList();
        Candidate selected = null;
        Outcome outcome;
        String reason;
        if (copy) {
            List<Candidate> copyable = kept.stream().filter(c -> c.candidateClass() == CandidateClass.SHARED
                    && c.kind().equals(SymbolKind.OBJECT.name())).toList();
            if (!policy.allowCopyRelocation() && !copyable.isEmpty()) {
                outcome = Outcome.BLOCKED_COPY;
                reason = "平台策略禁止生成 copy relocation";
            } else if (copyable.size() == 1) {
                selected = copyable.get(0);
                outcome = Outcome.RESOLVED;
                reason = "copy relocation 从共享对象复制数据对象";
            } else if (copyable.size() > 1) {
                selected = copyable.get(0);
                outcome = Outcome.AMBIGUOUS;
                reason = "多个共享对象可提供同一 copy relocation";
            } else {
                outcome = weak ? Outcome.UNRESOLVED : Outcome.UNRESOLVED;
                reason = "copy relocation 找不到共享数据对象";
            }
        } else {
            CandidateChoice choice = choose(kept);
            selected = choice.selected();
            if (choice.ambiguous() != null && choice.ambiguous().size() > 1) {
                outcome = Outcome.AMBIGUOUS;
                reason = "存在 " + choice.ambiguous().size() + " 个同权候选，不能用遍历次序裁决";
            } else if (selected != null) {
                outcome = Outcome.RESOLVED;
                reason = selected.candidateClass() == CandidateClass.SHARED ? "按显式动态加载顺序选择共享定义"
                        : selected.candidateClass() == CandidateClass.COMMON ? "选择最大 common 定义并合并"
                        : "按 strong/weak/common 规则选择";
            } else {
                outcome = weak ? Outcome.UNRESOLVED : Outcome.UNRESOLVED;
                reason = weak ? "弱未定义引用保持可空" : "未找到满足版本和可见性约束的定义";
                if (requester.module.kind() == ModuleKind.ARCHIVE_MEMBER) reason += "；archive 引用不会反向抽取其他成员";
            }
        }
        event("symbol", "符号裁决", group.key().display() + " => "
                + (selected == null ? outcome.name() : selected.module().display()), requester.module.displayName(),
                group.key().display());
        return new SymbolResolution(group.key(), outcome, selected, List.copyOf(candidates), relocations, reason, aliases);
    }

    private Candidate evaluateCandidate(Unit requester, Unit provider, ElfSymbol symbol, CandidateClass initialClass,
                                        SymbolKey requested, boolean copy, List<String> aliases) {
        EliminationReason eliminated = EliminationReason.KEPT;
        String detail = "候选与请求名称匹配";
        CandidateClass candidateClass = initialClass;
        if (candidateClass == CandidateClass.DISCARDED) {
            eliminated = EliminationReason.DISCARDED_COMDAT;
            detail = "所属 COMDAT section group 已被去重丢弃";
        }
        if (provider.module.kind() == ModuleKind.ARCHIVE_MEMBER && !provider.extracted) {
            eliminated = EliminationReason.ARCHIVE_NOT_EXTRACTED;
            detail = "archive 成员尚未被强引用抽取";
        }
        if (!versionMatches(symbol, requested, aliases)) {
            eliminated = EliminationReason.VERSION_MISMATCH;
            detail = "请求版本 " + requested.version() + "，候选版本 " + symbol.versionName();
        }
        if (symbol.visibility() == Visibility.HIDDEN && provider.module.key() != requester.module.key()) {
            eliminated = EliminationReason.HIDDEN_NOT_EXPORTED;
            detail = "hidden 符号只在定义对象内可见";
        }
        if (symbol.visibility() == Visibility.PROTECTED && provider.object.type() == ElfType.SHARED
                && !copy && requester.module.key() != provider.module.key()) {
            eliminated = EliminationReason.PLATFORM_POLICY;
            detail = "protected 共享符号不可被外部过程引用抢占";
        }
        if (copy && symbol.kind() != SymbolKind.OBJECT) {
            eliminated = EliminationReason.COPY_NOT_OBJECT;
            detail = "copy relocation 只能针对数据对象";
        }
        if (copy && symbol.visibility() == Visibility.PROTECTED && !policy.allowCopyFromProtected()) {
            eliminated = EliminationReason.COPY_PROTECTED;
            detail = "平台策略不允许从 protected 数据生成 copy relocation";
        }
        if (provider.module.kind() == ModuleKind.ARCHIVE_MEMBER && provider.module.loadOrder() == Integer.MAX_VALUE) {
            candidateClass = candidateClass;
        }
        return new Candidate("C" + candidateSeq++, provider.module.key(), symbol.name(), symbol.versionName(),
                symbol.versionDefault(), symbol.binding().name(), symbol.visibility().name(), symbol.kind().name(),
                candidateClass, symbol.value(), symbol.size(), symbol.sectionIndex(), symbol.sectionName(),
                symbol.versionDefinedHere(), eliminated, detail, rank(candidateClass));
    }

    private void addUnextractedArchiveCandidates(Unit requester, SymbolKey requested, boolean copy,
                                                 List<Candidate> candidates, List<String> aliases) {
        for (Unit provider : units.values()) {
            if (provider.extracted || provider.module.kind() != ModuleKind.ARCHIVE_MEMBER) continue;
            for (ElfSymbol symbol : provider.object.symbols()) {
                if (!symbol.name().equals(requested.name()) || !ElfModels.definedSection(symbol)) continue;
                CandidateClass candidateClass = symbol.kind() == SymbolKind.COMMON || symbol.sectionIndex() == Short.toUnsignedInt(SHN_COMMON_LOCAL)
                        ? CandidateClass.COMMON : symbol.binding() == Binding.WEAK ? CandidateClass.WEAK : CandidateClass.STRONG;
                Candidate candidate = evaluateCandidate(requester, provider, symbol, candidateClass, requested, copy, aliases);
                if (candidate.eliminationReason() == EliminationReason.KEPT) {
                    candidate = new Candidate(candidate.id(), candidate.module(), candidate.symbolName(),
                            candidate.versionName(), candidate.defaultVersion(), candidate.binding(),
                            candidate.visibility(), candidate.kind(), candidate.candidateClass(), candidate.value(),
                            candidate.size(), candidate.sectionIndex(), candidate.sectionName(),
                            candidate.versionDefinedHere(), EliminationReason.ARCHIVE_NOT_EXTRACTED,
                            "archive 成员存在同名定义，但没有强引用触发惰性抽取", candidate.rank());
                }
                candidates.add(candidate);
            }
        }
    }

    private record CandidateChoice(Candidate selected, List<Candidate> ambiguous) {}

    private CandidateChoice choose(List<Candidate> candidates) {
        if (candidates.isEmpty()) return new CandidateChoice(null, List.of());
        List<Candidate> strong = candidates.stream().filter(c -> c.candidateClass() == CandidateClass.STRONG
                && c.rank() == rank(CandidateClass.STRONG)).toList();
        if (!strong.isEmpty()) return tieStatic(strong);
        List<Candidate> weak = candidates.stream().filter(c -> c.candidateClass() == CandidateClass.WEAK).toList();
        List<Candidate> common = candidates.stream().filter(c -> c.candidateClass() == CandidateClass.COMMON).toList();
        if (!weak.isEmpty() || !common.isEmpty()) {
            if (!weak.isEmpty()) return tieStatic(weak);
            return chooseCommon(common);
        }
        List<Candidate> sharedStrong = candidates.stream().filter(c -> c.candidateClass() == CandidateClass.SHARED
                && c.binding().equals(Binding.GLOBAL.name())).toList();
        if (!sharedStrong.isEmpty()) return new CandidateChoice(sharedStrong.get(0), List.of(sharedStrong.get(0)));
        List<Candidate> sharedWeak = candidates.stream().filter(c -> c.candidateClass() == CandidateClass.SHARED
                && c.binding().equals(Binding.WEAK.name())).toList();
        return sharedWeak.isEmpty() ? new CandidateChoice(null, List.of())
                : new CandidateChoice(sharedWeak.get(0), List.of(sharedWeak.get(0)));
    }

    private CandidateChoice tieStatic(List<Candidate> candidates) {
        if (candidates.size() == 1) return new CandidateChoice(candidates.get(0), List.of());
        Candidate first = candidates.get(0);
        boolean allSame = candidates.stream().allMatch(c -> c.module().equals(first.module())
                && c.value() == first.value() && c.size() == first.size());
        return allSame ? new CandidateChoice(first, List.of()) : new CandidateChoice(null, candidates);
    }

    private CandidateChoice chooseCommon(List<Candidate> candidates) {
        long maxSize = candidates.stream().mapToLong(Candidate::size).max().orElse(0);
        List<Candidate> largest = candidates.stream().filter(c -> c.size() == maxSize).toList();
        if (largest.size() == 1) return new CandidateChoice(largest.get(0), List.of());
        return new CandidateChoice(null, largest);
    }

    private int rank(CandidateClass candidateClass) {
        return switch (candidateClass) {
            case LOCAL -> 100;
            case STRONG -> 80;
            case WEAK -> 70;
            case COMMON -> 60;
            case SHARED -> 50;
            case UNDEFINED -> 10;
            case DISCARDED -> 0;
        };
    }

    private boolean versionMatches(ElfSymbol symbol, SymbolKey requested, List<String> aliases) {
        String candidate = symbol.versionName() == null ? "" : symbol.versionName();
        String wanted = requested.version() == null ? "" : requested.version();
        if (wanted.isBlank()) return candidate.isBlank() || symbol.versionDefault();
        if (candidate.equals(wanted)) return true;
        return aliases.contains(candidate) || versionAliases(symbol).contains(wanted);
    }

    private List<String> aliasNames(SymbolKey key) {
        String wanted = key.version() == null ? "" : key.version();
        List<String> aliases = new ArrayList<>();
        for (Unit unit : units.values()) {
            for (VersionDefinition definition : unit.object.versions().definitions()) {
                if (definition.aliases().contains(wanted)) aliases.addAll(definition.aliases());
            }
        }
        return aliases.stream().distinct().toList();
    }

    private List<String> versionAliases(ElfSymbol symbol) {
        if (symbol.versionName() == null || symbol.versionName().isBlank()) return List.of();
        for (Unit unit : units.values()) {
            for (VersionDefinition definition : unit.object.versions().definitions()) {
                if (definition.aliases().contains(symbol.versionName())) return definition.aliases();
            }
        }
        return List.of(symbol.versionName());
    }

    private VersionGraph buildVersionGraph() {
        List<VersionNode> nodes = new ArrayList<>();
        List<VersionEdge> edges = new ArrayList<>();
        for (Unit unit : units.values()) {
            if (!unit.extracted) continue;
            String objectNode = "obj:" + unit.module.key().display();
            nodes.add(new VersionNode(objectNode, unit.module.displayName(), "object"));
            for (VersionDefinition definition : unit.object.versions().definitions()) {
                String node = "ver:" + unit.module.key().display() + ":" + definition.name();
                nodes.add(new VersionNode(node, definition.name(), "definition"));
                edges.add(new VersionEdge(objectNode, node, "defines"));
                for (String alias : definition.aliases()) {
                    String aliasNode = "alias:" + unit.module.key().display() + ":" + alias;
                    nodes.add(new VersionNode(aliasNode, alias, "alias"));
                    edges.add(new VersionEdge(node, aliasNode, "alias"));
                }
            }
            for (VersionRequirement requirement : unit.object.versions().requirements()) {
                String node = "need:" + unit.module.key().display() + ":" + requirement.fileName();
                nodes.add(new VersionNode(node, requirement.fileName(), "requirement"));
                edges.add(new VersionEdge(objectNode, node, "needs"));
                for (RequestedVersion version : requirement.versions()) {
                    String versionNode = "reqver:" + unit.module.key().display() + ":" + version.name();
                    nodes.add(new VersionNode(versionNode, version.name(), "requested-version"));
                    edges.add(new VersionEdge(node, versionNode, "requires"));
                }
            }
        }
        return new VersionGraph(dedupeNodes(nodes), dedupeEdges(edges));
    }

    private List<VersionNode> dedupeNodes(List<VersionNode> nodes) {
        return new ArrayList<>(new LinkedHashMap<>(nodes.stream().collect(Collectors.toMap(VersionNode::id, n -> n, (a, b) -> a))).values());
    }

    private List<VersionEdge> dedupeEdges(List<VersionEdge> edges) {
        return edges.stream().distinct().toList();
    }

    private SymbolKey key(ElfSymbol symbol) {
        return new SymbolKey(symbol.name(), symbol.versionName() == null ? "" : symbol.versionName());
    }

    private String groupId(ElfSymbol symbol) {
        return symbol.name() + "\0" + (symbol.versionName() == null ? "" : symbol.versionName());
    }

    private String request(SymbolKey key) {
        return key.name() + "\0" + (key.version() == null ? "" : key.version());
    }

    private String providerRequest(ElfSymbol symbol) {
        return request(key(symbol));
    }

    private boolean hasSymbolRelocation(Unit unit, ElfSymbol symbol) {
        return unit.object.relocations().stream().anyMatch(r -> r.symbolIndex() == symbol.index());
    }

    private RelocationRef syntheticRef(Unit unit, ElfSymbol symbol) {
        return new RelocationRef("R" + referenceSeq++, unit.module.key(), -1, "SYMBOL_TABLE", -1, -1, 0,
                symbol.index(), symbol.name(), false, false);
    }

    private void addUnit(ElfObject object, ModuleKind kind, boolean extracted, String inputId, int order,
                         String member) {
        ModuleKey key = new ModuleKey(inputId, member, object.type() == ElfType.SHARED);
        if (units.containsKey(key)) return;
        Module module = new Module(key, object.displayName(), kind, order,
                object.soname().isBlank() ? baseName(object.displayName()) : object.soname(),
                List.copyOf(object.needed()), extracted, inputId, member == null ? -1 : order);
        units.put(key, new Unit(module, object, extracted));
    }

    private ModuleKey keyFor(ElfObject object, ModuleKind kind, String inputId, String soname) {
        return new ModuleKey(inputId, object.displayName().contains("(") ? memberName(object.displayName()) : null,
                object.type() == ElfType.SHARED);
    }

    private String binaryIdFor(ElfObject object) {
        for (BinaryLoader.LoadedBinary binary : binaries) {
            if (binary.objects().contains(object)) return binary.input().id();
        }
        return object.displayName();
    }

    private String memberName(String displayName) {
        int open = displayName.indexOf('(');
        int close = displayName.lastIndexOf(')');
        return open >= 0 && close > open ? displayName.substring(open + 1, close) : displayName;
    }

    private String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void event(String kind, String title, String detail, String module, String symbol) {
        events.add(new Event(kind, title, detail, module, symbol, step++));
    }
}
