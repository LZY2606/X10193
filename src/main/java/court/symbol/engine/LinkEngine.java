package court.symbol.engine;

import court.symbol.elf.ElfRelocation;
import court.symbol.elf.ElfSymbol;
import court.symbol.elf.ParsedBinary;
import court.symbol.elf.SectionGroup;
import court.symbol.model.CandidateRejection;
import court.symbol.model.ExtractionEvent;
import court.symbol.model.PlatformPolicy;
import court.symbol.model.ResolutionDecision;
import court.symbol.model.ResolutionInput;
import court.symbol.model.Scheme;
import court.symbol.model.SymbolProvider;
import court.symbol.model.SymbolReference;
import court.symbol.model.Unit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LinkEngine {
    private Map<String, Integer> comdatOrders = Map.of();

    public Scheme resolve(ResolutionInput input, Map<String, ParsedBinary> binaries) {
        Scheme scheme = new Scheme();
        scheme.id = java.util.UUID.randomUUID().toString();
        scheme.name = input.name();
        scheme.policy = input.policy() == null ? PlatformPolicy.GNU_LOAD_ORDER : input.policy();
        scheme.binaries = binaries;
        buildUnits(scheme, input.loadOrder(), binaries);
        processArchives(scheme);
        resolveAll(scheme);
        return scheme;
    }

    private void buildUnits(Scheme scheme, List<String> loadOrder, Map<String, ParsedBinary> binaries) {
        int order = 0;
        for (String binaryId : loadOrder) {
            ParsedBinary binary = binaries.get(binaryId);
            if (binary == null) {
                scheme.warnings.add("加载项不存在: " + binaryId);
                continue;
            }
            if (binary.isArchive()) {
                for (ParsedBinary member : binary.archiveMembers) {
                    String id = binaryId + "(" + member.archiveMemberName + ")";
                    Unit unit = new Unit(id, binary.fileName, member.archiveMemberName, member, order,
                            false, binaryId);
                    scheme.availableMembers.add(unit);
                }
                binary.memberErrors.forEach(error -> scheme.warnings.add("隔离损坏 archive 成员: " + error));
            } else {
                Unit unit = new Unit(binaryId, binary.fileName, "", binary, order, true, null);
                scheme.explicitUnits.add(unit);
                scheme.activeUnits.add(unit);
            }
            order++;
        }
    }

    private void processArchives(Scheme scheme) {
        int step = 1;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Unit member : List.copyOf(scheme.availableMembers)) {
                if (scheme.activeUnits.contains(member) || isDiscardedComdat(scheme, member)) {
                    continue;
                }
                Set<String> demands = demandedStrongSymbols(scheme, member);
                if (demands.isEmpty()) {
                    continue;
                }
                if (hasSatisfyingCandidate(scheme, member, demands)) {
                    scheme.extractionEvents.add(new ExtractionEvent(step++, member.archiveId(),
                            member.memberName(), List.copyOf(demands), "SKIPPED",
                            "请求符号已经由更优候选满足"));
                    continue;
                }
                scheme.activeUnits.add(member);
                scheme.extractionEvents.add(new ExtractionEvent(step++, member.archiveId(),
                        member.memberName(), List.copyOf(demands), "EXTRACTED",
                        "存在未满足的强引用，惰性抽取该成员"));
                changed = true;
            }
        }
    }

    private Set<String> demandedStrongSymbols(Scheme scheme, Unit candidateMember) {
        Map<String, SymbolReference> references = collectReferences(scheme, scheme.activeUnits);
        Set<String> defined = new LinkedHashSet<>();
        for (ElfSymbol symbol : candidateMember.providers()) {
            if (isProvider(symbol) && !symbol.isWeak()) {
                defined.add(symbol.key());
            }
        }
        Set<String> result = new LinkedHashSet<>();
        for (String key : defined) {
            String name = key.substring(0, key.indexOf('\u0000'));
            boolean demanded = references.values().stream()
                    .filter(reference -> !reference.relocation().symbolName.startsWith("__"))
                    .anyMatch(reference -> reference.name().equals(name) && isStrongReference(reference));
            if (demanded) {
                result.add(name);
            }
        }
        return result;
    }

    private boolean isStrongReference(SymbolReference reference) {
        ElfSymbol source = reference.unit().providers().stream()
                .filter(symbol -> symbol.name.equals(reference.name()))
                .findFirst().orElse(null);
        return source == null || !source.isWeak();
    }

    private boolean isProvider(ElfSymbol symbol) {
        return symbol.isDefined() || symbol.isCommon();
    }

    private boolean hasSatisfyingCandidate(Scheme scheme, Unit candidate, Set<String> names) {
        for (String name : names) {
            List<SymbolProvider> providers = collectProviders(scheme, scheme.activeUnits).stream()
                    .filter(provider -> provider.symbol().name.equals(name))
                    .filter(provider -> provider.score() >= 70)
                    .toList();
            if (!providers.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isDiscardedComdat(Scheme scheme, Unit member) {
        for (SectionGroup group : member.binary().groups) {
            if (!group.comdat()) {
                continue;
            }
            boolean earlier = scheme.activeUnits.stream().anyMatch(unit -> unit.loadOrder() < member.loadOrder()
                    && unit.binary().groups.stream().anyMatch(other -> other.comdat()
                    && other.signature().equals(group.signature())));
            if (earlier) {
                return true;
            }
        }
        return false;
    }

    private void resolveAll(Scheme scheme) {
        comdatOrders = new LinkedHashMap<>();
        for (Unit unit : scheme.activeUnits) {
            for (SectionGroup group : unit.binary().groups) {
                if (group.comdat()) {
                    comdatOrders.merge(group.signature(), unit.loadOrder(), Math::min);
                }
            }
        }
        Map<String, List<SymbolReference>> grouped = new LinkedHashMap<>();
        for (SymbolReference reference : collectReferences(scheme, scheme.activeUnits).values()) {
            grouped.computeIfAbsent(decisionKey(reference), ignored -> new ArrayList<>()).add(reference);
        }
        List<SymbolProvider> allProviders = collectProviders(scheme, scheme.activeUnits);
        for (Map.Entry<String, List<SymbolReference>> entry : grouped.entrySet()) {
            List<SymbolReference> references = entry.getValue();
            SymbolReference first = references.get(0);
            List<SymbolProvider> eligible = new ArrayList<>();
            List<CandidateRejection> rejected = new ArrayList<>();
            for (SymbolProvider provider : allProviders) {
                if (!provider.symbol().name.equals(first.name())) {
                    continue;
                }
                String reason = rejectionReason(first, provider);
                if (reason == null) {
                    eligible.add(provider);
                } else {
                    rejected.add(new CandidateRejection(provider, reason));
                }
            }
            eligible.sort(Comparator
                    .comparingInt(SymbolProvider::score).reversed()
                    .thenComparing(provider -> provider.unit().loadOrder())
                    .thenComparing(SymbolProvider::id));
            makeDecision(scheme, entry.getKey(), first, references, eligible, rejected);
        }
    }

    private Map<String, SymbolReference> collectReferences(Scheme scheme, List<Unit> units) {
        Map<String, SymbolReference> references = new LinkedHashMap<>();
        for (Unit unit : units) {
            int ordinal = 0;
            for (ElfRelocation relocation : unit.relocationList()) {
                if (relocation.symbolIndex == 0 || relocation.symbolName == null
                        || relocation.symbolName.isEmpty()) {
                    continue;
                }
                List<ElfSymbol> table = relocation.dynamicSymbol ? unit.binary().dynamicSymbols
                        : unit.binary().staticSymbols;
                if (relocation.symbolIndex < 0 || relocation.symbolIndex >= table.size()) {
                    scheme.warnings.add(unit.displayName() + " relocation 符号索引越界: " + relocation.symbolIndex);
                    continue;
                }
                ElfSymbol symbol = table.get(relocation.symbolIndex);
                String version = relocation.dynamicSymbol ? symbol.versionName : "";
                String library = relocation.dynamicSymbol ? symbol.versionLibrary : "";
                boolean defaultVersion = !relocation.dynamicSymbol || version.isEmpty() || symbol.versionDefault;
                SymbolReference reference = new SymbolReference(unit.id() + "#rel" + ordinal++, unit,
                        relocation, symbol.name, version == null ? "" : version, defaultVersion,
                        library == null ? "" : library, relocation.isCopy(unit.binary().machine));
                references.put(reference.id(), reference);
            }
        }
        return references;
    }

    private List<SymbolProvider> collectProviders(Scheme scheme, List<Unit> units) {
        List<SymbolProvider> providers = new ArrayList<>();
        for (Unit unit : units) {
            List<ElfSymbol> symbols = unit.providers();
            for (ElfSymbol symbol : symbols) {
                if (symbol.index == 0 || symbol.name == null || symbol.name.isEmpty()) {
                    continue;
                }
                if (symbol.isLocal() && !isLocalDefinition(symbol)) {
                    continue;
                }
                if (!symbol.isDefined() && !symbol.isCommon()) {
                    continue;
                }
                String version = symbol.dynamic ? symbol.versionName : "";
                SymbolProvider provider = new SymbolProvider(unit, symbol, version == null ? "" : version,
                        version.isEmpty() || symbol.versionDefault, symbol.versionRequirement,
                        symbol.versionLibrary == null ? "" : symbol.versionLibrary,
                        symbol.groupSignature == null ? "" : symbol.groupSignature,
                        scoreProvider(unit, symbol), scoreReason(unit, symbol));
                providers.add(provider);
            }
        }
        return providers;
    }

    private boolean isLocalDefinition(ElfSymbol symbol) {
        return symbol.isLocal() && symbol.isDefined();
    }

    private int scoreProvider(Unit unit, ElfSymbol symbol) {
        if (symbol.isLocal()) {
            return 120;
        }
        if (unit.isObject() || unit.isExecutable()) {
            if (symbol.isCommon()) {
                return 50;
            }
            if (symbol.isWeak()) {
                return 80;
            }
            return 100;
        }
        return symbol.isWeak() ? 35 : 60;
    }

    private String scoreReason(Unit unit, ElfSymbol symbol) {
        if (symbol.isLocal()) {
            return "local 定义仅在本对象内绑定";
        }
        if (unit.isObject() || unit.isExecutable()) {
            if (symbol.isCommon()) {
                return "common 暂定定义，可与其他 common 合并或被强定义覆盖";
            }
            return symbol.isWeak() ? "object weak 定义弱于 strong 定义" : "object strong 定义";
        }
        return symbol.isWeak() ? "shared weak 动态定义" : "shared strong 动态定义";
    }

    private String rejectionReason(SymbolReference reference, SymbolProvider provider) {
        ElfSymbol symbol = provider.symbol();
        if (symbol.isLocal() && !provider.unit().id().equals(reference.unit().id())) {
            return "local binding 不导出到其他对象";
        }
        if (symbol.isHiddenOutside() && !provider.unit().id().equals(reference.unit().id())) {
            return "hidden visibility 只能在定义对象内部解析";
        }
        if (isDiscardedProvider(provider)) {
            return "COMDAT 组 " + provider.groupSignature() + " 已被先出现的同名组去重";
        }
        if (reference.copy()) {
            if (!provider.unit().isShared()) {
                return "copy relocation 只能从 shared object 复制对象";
            }
            if (symbol.type != 1) {
                return "copy relocation 要求 STT_OBJECT";
            }
            if (symbol.isProtected()) {
                return "protected 数据不可作为 copy relocation 提供者";
            }
            if (symbol.isHiddenOutside()) {
                return "hidden 数据不可作为 copy relocation 提供者";
            }
        }
        if (!reference.unit().isExecutable() && reference.relocation().dynamicSymbol
                && provider.unit().isObject()) {
            return "动态组件的未定义动态引用不能由普通 relocatable object 满足";
        }
        String requested = reference.requestedVersion() == null ? "" : reference.requestedVersion();
        String provided = provider.versionName() == null ? "" : provider.versionName();
        if (requested.isEmpty()) {
            if (!provided.isEmpty() && !provider.defaultVersion()) {
                return "未带版本的引用不绑定非默认版本 " + provided;
            }
        } else if (!requested.equals(provided)) {
            return "请求版本 " + requested + " 与候选版本 " + provided + " 不同";
        }
        return null;
    }

    private boolean isDiscardedProvider(SymbolProvider provider) {
        if (provider.groupSignature().isEmpty()) {
            return false;
        }
        return provider.unit().binary().groups.stream()
                .filter(SectionGroup::comdat)
                .filter(group -> group.signature().equals(provider.groupSignature()))
                .findFirst()
                .map(group -> provider.unit().loadOrder() > earliestComdatOrder(provider))
                .orElse(false);
    }

    private int earliestComdatOrder(SymbolProvider provider) {
        return comdatOrders.getOrDefault(provider.groupSignature(), provider.unit().loadOrder());
    }

    private String decisionKey(SymbolReference reference) {
        return reference.unit().id() + "\u0000" + reference.name() + "\u0000"
                + reference.requestedVersion() + "\u0000" + reference.requiredLibrary();
    }

    private void makeDecision(Scheme scheme, String key, SymbolReference first,
                              List<SymbolReference> references, List<SymbolProvider> eligible,
                              List<CandidateRejection> rejected) {
        if (eligible.isEmpty()) {
            ElfSymbol source = sourceSymbol(first);
            boolean weak = source != null && source.isWeak() && !source.isDefined();
            String status = weak ? "WEAK_UNDEFINED" : "UNRESOLVED";
            String rationale = weak
                    ? "未定义 weak 引用无候选，按弱引用规则允许为空/零地址"
                    : "没有通过可见性、版本、section group 与 relocation 类型检查的候选";
            scheme.decisions.add(new ResolutionDecision(key, first.name(), first.requestedVersion(),
                    status, List.of(), rejected, references, rationale));
            return;
        }
        int top = eligible.get(0).score();
        List<SymbolProvider> winners = eligible.stream().filter(provider -> provider.score() == top).toList();
        String status;
        String rationale;
        boolean commonMerge = winners.stream().allMatch(provider -> provider.symbol().isCommon());
        if (commonMerge) {
            status = "COMMON_MERGED";
            rationale = "多个 common 暂定定义合并，强定义出现时仍会覆盖 common";
        } else if (winners.size() > 1 && top == 100) {
            status = "AMBIGUOUS_DUPLICATE_STRONG";
            rationale = "多个 relocatable object 提供同等 strong 定义，普通加载顺序不能吞掉重复定义";
        } else if (winners.size() > 1 && scheme.policy == PlatformPolicy.STRICT_AMBIGUITY) {
            status = "AMBIGUOUS";
            rationale = "目标平台策略要求保留同分候选，不使用加载顺序打破平局";
        } else {
            SymbolProvider winner = winners.get(0);
            for (SymbolProvider lower : eligible.subList(1, eligible.size())) {
                rejected.add(new CandidateRejection(lower,
                        "候选有效，但符号作用域/绑定强度/加载顺序得分低于最终选择"));
            }
            winners = List.of(winner);
            status = "RESOLVED";
            rationale = winners.get(0).rankReason() + "；GNU 风格作用域中该候选胜出";
        }
        scheme.decisions.add(new ResolutionDecision(key, first.name(), first.requestedVersion(),
                status, winners, rejected, references, rationale));
    }

    private ElfSymbol sourceSymbol(SymbolReference reference) {
        List<ElfSymbol> table = reference.relocation().dynamicSymbol
                ? reference.unit().binary().dynamicSymbols
                : reference.unit().binary().staticSymbols;
        if (reference.relocation().symbolIndex >= 0 && reference.relocation().symbolIndex < table.size()) {
            return table.get(reference.relocation().symbolIndex);
        }
        return null;
    }
}
