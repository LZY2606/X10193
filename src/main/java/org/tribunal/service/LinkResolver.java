package org.tribunal.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.tribunal.model.ArchiveMember;
import org.tribunal.model.BinaryKind;
import org.tribunal.model.CandidateOutcome;
import org.tribunal.model.DependencyEdge;
import org.tribunal.model.ElfFile;
import org.tribunal.model.ParsedBinary;
import org.tribunal.model.PlatformPolicy;
import org.tribunal.model.Resolution;
import org.tribunal.model.ResolutionResult;
import org.tribunal.model.ResolutionStep;
import org.tribunal.model.RelocationInfo;
import org.tribunal.model.SymbolDefinition;
import org.tribunal.model.SymbolReference;
import org.tribunal.model.VersionEdge;

@Service
public class LinkResolver {
    public ResolutionResult resolve(List<LoadedBinary> loadedBinaries, PlatformPolicy policy) {
        ResolverState state = new ResolverState(policy);
        state.warnings.addAll(loadedBinaries.stream().flatMap(binary -> binary.members().stream())
                .filter(member -> !member.parseOk())
                .map(member -> "损坏 archive member 已隔离: " + member.name() + " (" + member.parseError() + ")")
                .toList());
        for (LoadedBinary binary : loadedBinaries) {
            if (binary.elfFile() != null && binary.binary().parseOk()) {
                state.addInitial(binary.elfFile(), binary.ordinal());
                state.steps.add(new ResolutionStep(state.sequence++, "load", "加载显式对象",
                        binary.binary().name() + " 已按显式顺序加入", binary.elfFile().unitId(), null,
                        state.activeUnitIds(), Map.of("kind", binary.elfFile().kind().name())));
            } else if (binary.elfFile() == null && "ARCHIVE".equals(binary.binary().kind())) {
                state.archives.add(binary);
                state.steps.add(new ResolutionStep(state.sequence++, "load", "登记 archive",
                        binary.binary().name() + " 暂不抽取任何 member", "archive:" + binary.binary().id(),
                        null, state.activeUnitIds(), Map.of("members", binary.members().size())));
            } else {
                state.warnings.add("损坏对象已隔离: " + binary.binary().name() + " (" + binary.binary().parseError() + ")");
            }
        }
        boolean changed = true;
        while (changed) {
            changed = extractArchives(state, policy);
        }
        addReferenceResolutions(state);
        return new ResolutionResult(List.copyOf(state.activeUnitIds), List.copyOf(state.units.values()),
                List.copyOf(state.dependencyEdges), List.copyOf(state.versionEdges),
                List.copyOf(state.resolutions.values()), List.copyOf(state.steps),
                List.copyOf(state.warnings));
    }

    private boolean extractArchives(ResolverState state, PlatformPolicy policy) {
        Set<String> unresolvedStrong = state.references.stream()
                .filter(reference -> reference.strong())
                .filter(reference -> !isSatisfied(reference, state))
                .map(reference -> reference.name() + "\u0000" + nullSafe(reference.requestedVersion()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unresolvedStrong.isEmpty()) {
            return false;
        }
        for (LoadedBinary archive : state.archives) {
            if (state.exhaustedArchives.contains(archive.binary().id())) {
                continue;
            }
            List<ArchiveMember> selected = archive.members().stream()
                    .filter(ArchiveMember::parseOk)
                    .filter(member -> member.elfFile() != null)
                    .filter(member -> !state.extractedMembers.contains(memberKey(archive, member)))
                    .filter(member -> definesAnyStrong(member.elfFile(), unresolvedStrong))
                    .toList();
            if (selected.isEmpty()) {
                state.exhaustedArchives.add(archive.binary().id());
                continue;
            }
            for (ArchiveMember member : selected) {
                String key = memberKey(archive, member);
                if (state.extractedMembers.add(key)) {
                    state.addInitial(member.elfFile(), state.nextOrdinal++);
                    state.steps.add(new ResolutionStep(state.sequence++, "archive-extract",
                            "惰性抽取 archive member", archive.binary().name() + "(" + member.name() + ") 因未满足强引用被抽取",
                            member.elfFile().unitId(), String.join(",", unresolvedStrong),
                            state.activeUnitIds(), Map.of("archive", archive.binary().name(),
                                    "member", member.name(), "unresolved", List.copyOf(unresolvedStrong))));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSatisfied(SymbolReference reference, ResolverState state) {
        return state.definitions.values().stream().flatMap(List::stream)
                .filter(definition -> definition.canProvideGlobal() || definition.common())
                .anyMatch(definition -> extractionMatch(reference, definition));
    }

    private boolean definesAnyStrong(ElfFile elf, Set<String> unresolved) {
        return elf.definitions().stream()
                .filter(definition -> definition.canProvideGlobal() || definition.common())
                .anyMatch(definition -> unresolved.contains(definition.name() + "\u0000" + nullSafe(definition.versionName())));
    }

    private boolean extractionMatch(SymbolReference reference, SymbolDefinition candidate) {
        if (!candidate.name().equals(reference.name()) || "LOCAL".equals(candidate.binding())) {
            return false;
        }
        if ("HIDDEN".equals(candidate.visibility()) || "INTERNAL".equals(candidate.visibility())) {
            return false;
        }
        if (reference.versionedRequest()) {
            return candidate.versionName() != null
                    && candidate.versionName().equals(reference.requestedVersion())
                    && !candidate.hiddenVersion();
        }
        return candidate.versionName() == null || candidate.defaultVersion();
    }

    private String memberKey(LoadedBinary archive, ArchiveMember member) {
        return archive.binary().id() + ":" + member.name();
    }

    public record LoadedBinary(ParsedBinary binary, ElfFile elfFile, List<ArchiveMember> members, int ordinal) {
    }

    private void addReferenceResolutions(ResolverState state) {
        for (SymbolReference reference : state.references) {
            resolveReference(reference, state);
        }
    }

    private void resolveReference(SymbolReference reference, ResolverState state) {
        List<EvaluatedCandidate> evaluated = new ArrayList<>();
        Map<String, List<SymbolDefinition>> grouped = comdatFoldedProviders(reference, state);
        for (List<SymbolDefinition> group : grouped.values()) {
            SymbolDefinition representative = group.get(0);
            MatchResult match = matches(reference, representative, state.policy);
            List<String> reasons = new ArrayList<>(match.reasons());
            if (group.size() > 1) {
                reasons.add("COMDAT(" + representative.groupSignature() + ") 折叠 " + group.size() + " 个等价 section-group 定义");
            }
            evaluated.add(new EvaluatedCandidate(representative, group, match.compatible(), reasons));
        }
        List<EvaluatedCandidate> compatible = evaluated.stream().filter(EvaluatedCandidate::compatible).toList();
        List<EvaluatedCandidate> winners = chooseWinners(compatible, state.policy);
        List<CandidateOutcome> outcomes = evaluated.stream()
                .sorted(Comparator.comparing(candidate -> candidate.representative().unitId() + ":"
                        + candidate.representative().ordinal()))
                .map(candidate -> toOutcome(candidate, winners.contains(candidate)))
                .toList();
        List<RelocationInfo> affected = collectRelocations(reference, state);
        List<String> selectedKeys = winners.stream().map(candidate -> candidate.representative().providerKey()).toList();
        String status;
        String reason;
        if (winners.isEmpty()) {
            boolean weak = "WEAK".equals(reference.binding());
            status = weak ? "UNRESOLVED_WEAK" : "UNRESOLVED";
            reason = weak ? "弱未定义引用允许为 0，不抽取 archive" : "没有通过名称、版本、visibility 与 COMDAT 检查的候选";
        } else if (winners.size() > 1) {
            status = "AMBIGUOUS";
            reason = "存在多个同等候选；不使用文件系统遍历次序裁决";
        } else {
            status = "RESOLVED";
            reason = "候选按 binding、对象类型、加载顺序和版本规则胜出";
        }
        boolean copy = reference.copyRelocation() || affected.stream().anyMatch(RelocationInfo::copyRelocation);
        if (copy && status.equals("RESOLVED")) {
            reason += "；COPY relocation 记录为数据复制，不按普通覆盖规则处理";
        }
        state.resolutions.put(resolutionKey(reference), new Resolution(reference.requestKey(),
                reference.name(), reference.requestedVersion(), reference.unitId(),
                reference.unitName(), reference.strong(), outcomes, selectedKeys, status, reason,
                affected, copy));
    }

    private Map<String, List<SymbolDefinition>> comdatFoldedProviders(SymbolReference reference,
                                                                      ResolverState state) {
        Map<String, List<SymbolDefinition>> grouped = new LinkedHashMap<>();
        for (SymbolDefinition definition : state.definitions.values().stream().flatMap(List::stream)
                .filter(definition -> definition.canProvideGlobal() || definition.common()).toList()) {
            if (!definition.name().equals(reference.name())) {
                continue;
            }
            String groupKey = definition.groupSignature() == null
                    ? definition.unitId() + ":" + definition.ordinal()
                    : "COMDAT:" + definition.groupSignature();
            grouped.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(definition);
        }
        return grouped;
    }

    private MatchResult matches(SymbolReference reference, SymbolDefinition candidate, PlatformPolicy policy) {
        List<String> reasons = new ArrayList<>();
        boolean compatible = true;
        if (!candidate.name().equals(reference.name())) {
            compatible = false;
            reasons.add("名称不同");
        }
        if ("LOCAL".equals(candidate.binding())) {
            compatible = false;
            reasons.add("LOCAL binding 不参与全局裁决");
        }
        if ("HIDDEN".equals(candidate.visibility()) || "INTERNAL".equals(candidate.visibility())) {
            compatible = false;
            reasons.add(candidate.visibility() + " visibility 不导出");
        }
        if (candidate.common()) {
            reasons.add("COMMON 候选可参与临时合并");
        }
        if (reference.versionedRequest()) {
            if (candidate.versionName() == null) {
                compatible = false;
                reasons.add("引用要求版本 " + reference.requestedVersion() + "，候选无版本");
            } else if (!candidate.versionName().equals(reference.requestedVersion())) {
                compatible = false;
                reasons.add("版本不匹配: 引用 " + reference.requestedVersion() + "，候选 " + candidate.versionName());
            }
        } else if (candidate.versionName() != null && !candidate.defaultVersion()) {
            compatible = false;
            reasons.add("非默认版本 " + candidate.versionName() + " 不接受无版本引用");
        }
        if (candidate.hiddenVersion()) {
            compatible = false;
            reasons.add("版本带有 HIDDEN 标志，不能从外部解析");
        }
        if (compatible) {
            reasons.add("名称与版本兼容");
        }
        return new MatchResult(compatible, reasons);
    }

    private List<EvaluatedCandidate> chooseWinners(List<EvaluatedCandidate> compatible, PlatformPolicy policy) {
        if (compatible.isEmpty()) {
            return List.of();
        }
        int bestRegular = compatible.stream().mapToInt(candidate -> regularRank(candidate.representative())).min().orElse(0);
        List<EvaluatedCandidate> byRank = compatible.stream()
                .filter(candidate -> regularRank(candidate.representative()) == bestRegular).toList();
        if (byRank.size() == 1) {
            return byRank;
        }
        if (bestRegular == 0) {
            List<EvaluatedCandidate> nonCommon = byRank.stream()
                    .filter(candidate -> !candidate.representative().common()).toList();
            if (!nonCommon.isEmpty()) {
                byRank = nonCommon;
            } else {
                long maxSize = byRank.stream().mapToLong(candidate -> candidate.representative().size()).max().orElse(0);
                byRank = byRank.stream().filter(candidate -> candidate.representative().size() == maxSize).toList();
                if (byRank.size() == 1) {
                    return byRank;
                }
            }
            List<EvaluatedCandidate> strong = byRank.stream()
                    .filter(candidate -> "GLOBAL".equals(candidate.representative().binding())).toList();
            if (strong.size() == 1) {
                return strong;
            }
            if (strong.size() > 1) {
                return strong;
            }
            Integer firstOrdinal = byRank.stream().map(candidate -> candidate.representative().ordinal())
                    .min(Integer::compareTo).orElseThrow();
            return byRank.stream().filter(candidate -> candidate.representative().ordinal() == firstOrdinal).toList();
        }
        if (policy.firstSharedWins()) {
            Integer firstOrdinal = byRank.stream().map(candidate -> candidate.representative().ordinal())
                    .min(Integer::compareTo).orElseThrow();
            return byRank.stream().filter(candidate -> candidate.representative().ordinal() == firstOrdinal).toList();
        }
        return byRank;
    }

    private int regularRank(SymbolDefinition definition) {
        if (definition.unitKind() == BinaryKind.OBJECT || definition.unitKind() == BinaryKind.EXECUTABLE) {
            return 0;
        }
        if (definition.unitKind() == BinaryKind.SHARED) {
            return 1;
        }
        return 2;
    }

    private List<RelocationInfo> collectRelocations(SymbolReference reference, ResolverState state) {
        ElfFile requester = state.units.get(reference.unitId());
        if (requester == null) {
            return List.of();
        }
        return requester.relocations().stream()
                .filter(relocation -> relocation.symbol().equals(reference.name()))
                .filter(relocation -> Objects.equals(relocation.requestedVersion(), reference.requestedVersion()))
                .toList();
    }

    private CandidateOutcome toOutcome(EvaluatedCandidate candidate, boolean selected) {
        SymbolDefinition definition = candidate.representative();
        return new CandidateOutcome(definition.providerKey(), definition.unitId(), definition.unitName(),
                definition.binding(), definition.visibility(), definition.versionName(),
                definition.defaultVersion(), definition.sectionName(), definition.groupSignature(),
                definition.size(), definition.value(), selected,
                selected ? List.of("胜出") : candidate.rejectionReasons());
    }

    private String resolutionKey(SymbolReference reference) {
        return reference.unitId() + "\u0000" + reference.name() + "\u0000"
                + nullSafe(reference.requestedVersion());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record MatchResult(boolean compatible, List<String> reasons) {
    }

    private record EvaluatedCandidate(SymbolDefinition representative, List<SymbolDefinition> folded,
                                      boolean compatible, List<String> rejectionReasons) {
    }

    private static final class ResolverState {
        private final PlatformPolicy policy;
        private final Map<String, ElfFile> units = new LinkedHashMap<>();
        private final Map<String, List<SymbolDefinition>> definitions = new LinkedHashMap<>();
        private final List<SymbolReference> references = new ArrayList<>();
        private final List<LoadedBinary> archives = new ArrayList<>();
        private final Set<String> extractedMembers = new LinkedHashSet<>();
        private final Set<Long> exhaustedArchives = new LinkedHashSet<>();
        private final List<String> activeUnitIds = new ArrayList<>();
        private final List<DependencyEdge> dependencyEdges = new ArrayList<>();
        private final List<VersionEdge> versionEdges = new ArrayList<>();
        private final Map<String, Resolution> resolutions = new LinkedHashMap<>();
        private final List<ResolutionStep> steps = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private int sequence = 0;
        private int nextOrdinal = 1000;

        private ResolverState(PlatformPolicy policy) {
            this.policy = policy;
        }

        private void addInitial(ElfFile elf, int ordinal) {
            if (units.putIfAbsent(elf.unitId(), elf) != null) {
                return;
            }
            activeUnitIds.add(elf.unitId());
            definitions.put(elf.unitId(), new ArrayList<>(elf.definitions()));
            references.addAll(elf.references().stream()
                    .map(reference -> enrichRelocationFlags(reference, elf))
                    .toList());
            dependencyEdges.addAll(elf.dependencies());
            versionEdges.addAll(elf.versionEdges());
            steps.add(new ResolutionStep(sequence++, "symbol-table", "符号表更新",
                    elf.unitName() + " 提供 " + elf.definitions().size() + " 个符号、请求 "
                            + elf.references().size() + " 个符号",
                    elf.unitId(), null, List.copyOf(activeUnitIds),
                    Map.of("definitions", elf.definitions().size(), "references", elf.references().size(),
                            "relocations", elf.relocations().size())));
        }

        private SymbolReference enrichRelocationFlags(SymbolReference reference, ElfFile elf) {
            boolean copy = elf.relocations().stream()
                    .filter(relocation -> relocation.symbol().equals(reference.name()))
                    .anyMatch(RelocationInfo::copyRelocation);
            return new SymbolReference(reference.unitId(), reference.unitName(), reference.name(),
                    reference.binding(), reference.requestedVersion(), reference.requestKey(),
                    reference.versionedRequest(), reference.rawVersionIndex(), reference.relocation(),
                    reference.strong(), copy);
        }

        private List<String> activeUnitIds() {
            return List.copyOf(activeUnitIds);
        }
    }
}
