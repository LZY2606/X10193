package local.symbolcourt.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import local.symbolcourt.elf.ElfModels.ArchiveMember;
import local.symbolcourt.elf.ElfModels.BinaryKind;
import local.symbolcourt.elf.ElfModels.ElfSymbol;
import local.symbolcourt.elf.ElfModels.LoadedFile;
import local.symbolcourt.elf.ElfModels.ParsedBinary;
import local.symbolcourt.elf.ElfModels.Relocation;
import local.symbolcourt.elf.ElfModels.Visibility;
import local.symbolcourt.engine.EngineModels.*;
import org.springframework.stereotype.Component;

@Component
public class LinkAdjudicator {
    public AdjudicationResult adjudicate(List<LoadedFile> files, List<InputRef> refs, PlatformPolicy policy) {
        Map<String, LoadedFile> byName = new LinkedHashMap<>();
        for (LoadedFile file : files) byName.put(file.name(), file);
        List<ActiveUnit> isolated = new ArrayList<>();
        List<ActiveUnit> units = new ArrayList<>();
        List<ExtractionEvent> events = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        Map<String, List<ArchiveMember>> remaining = new LinkedHashMap<>();
        for (InputRef ref : refs) {
            LoadedFile file = byName.get(ref.name());
            if (file == null) {
                diagnostics.add("missing input: " + ref.name());
                continue;
            }
            if (file.error() != null || file.parsed() == null || !file.parsed().usable()) {
                isolated.add(new ActiveUnit("input:" + ref.binaryId(), ref.name(), ref.name(), ref.sha256(),
                        BinaryKind.CORRUPT, ref.loadOrder(), -1, null, false, file.parsed()));
                diagnostics.add("isolated corrupt object: " + ref.name());
                continue;
            }
            ParsedBinary parsed = file.parsed();
            if (file.archive()) {
                remaining.put(ref.name(), new ArrayList<>(file.members()));
            } else {
                units.add(new ActiveUnit(unitId(ref.name(), null), display(ref.name(), null),
                        ref.name(), ref.sha256(), parsed.kind(), ref.loadOrder(), -1, null, false, parsed));
            }
        }

        Set<String> activeKeys = new HashSet<>(units.stream().map(ActiveUnit::unitId).toList());
        Set<String> satisfied = new HashSet<>();
        for (int round = 1; round <= 100; round++) {
            Map<String, List<ExtractionProposal>> proposals = new LinkedHashMap<>();
            for (ActiveUnit unit : units) {
                for (Relocation rel : unit.binary().relocations()) {
                    ElfSymbol requested = symbol(unit, rel.symbolIndex()).orElse(null);
                    if (requested == null || requested.index() == 0) continue;
                    if (requested.defined() && !requested.common()) continue;
                    if (requested.binding().toString().equals("LOCAL") && unit.kind() == BinaryKind.RELOCATABLE) continue;
                    String key = requested.name() + "@" + (requested.version() == null ? "" : requested.version().name());
                    if (satisfied.contains(key)) continue;
                    for (Map.Entry<String, List<ArchiveMember>> archiveEntry : remaining.entrySet()) {
                        List<ArchiveMember> matches = new ArrayList<>();
                        int order = 0;
                        for (ArchiveMember member : archiveEntry.getValue()) {
                            if (member.parsed() != null && member.parsed().usable()
                                    && definesStrong(member.parsed(), requested)) {
                                matches.add(member);
                            }
                            order++;
                        }
                        if (!matches.isEmpty()) {
                            InputRef archiveRef = refs.stream().filter(r -> r.name().equals(archiveEntry.getKey())).findFirst().orElseThrow();
                            List<ExtractionProposal> list = proposals.computeIfAbsent(key, k -> new ArrayList<>());
                            for (int m = 0; m < matches.size(); m++) {
                                ArchiveMember member = matches.get(m);
                                list.add(new ExtractionProposal(archiveEntry.getKey(), archiveRef.loadOrder(),
                                        member, archiveEntry.getValue().indexOf(member), key));
                            }
                        }
                    }
                }
            }
            if (proposals.isEmpty()) break;
            boolean changed = false;
            for (Map.Entry<String, List<ExtractionProposal>> entry : proposals.entrySet()) {
                List<ExtractionProposal> ranked = entry.getValue().stream()
                        .sorted(Comparator.comparingInt(ExtractionProposal::loadOrder)
                                .thenComparingInt(ExtractionProposal::archiveOrder))
                        .toList();
                ExtractionProposal first = ranked.get(0);
                List<ExtractionProposal> equal = ranked.stream()
                        .filter(p -> p.loadOrder() == first.loadOrder() && p.archiveOrder() == first.archiveOrder())
                        .toList();
                if (equal.size() > 1 && policy.tiePolicy() == TiePolicy.STRICT_AMBIGUOUS) {
                    diagnostics.add("ambiguous archive candidates for " + entry.getKey());
                    satisfied.add(entry.getKey());
                    continue;
                }
                String memberKey = first.archive() + ":" + first.member().memberName();
                if (activeKeys.add(memberKey)) {
                    ParsedBinary parsed = first.member().parsed();
                    ActiveUnit active = new ActiveUnit(memberKey,
                            display(first.archive(), first.member().memberName()),
                            first.member().memberName(), null, parsed.kind(),
                            first.loadOrder(), first.archiveOrder(), first.archive(), true, parsed);
                    units.add(active);
                    events.add(new ExtractionEvent(round, first.archive(),
                            first.member().memberName(), List.of(entry.getKey()),
                            "lazy extraction from archive"));
                    remaining.get(first.archive()).remove(first.member());
                    satisfied.add(entry.getKey());
                    changed = true;
                }
            }
            if (!changed) break;
        }
        units.sort(Comparator.comparingInt(ActiveUnit::loadOrder).thenComparingInt(ActiveUnit::archiveOrder));
        return resolve(units, isolated, refs, policy, events, diagnostics, byName);
    }

    private AdjudicationResult resolve(List<ActiveUnit> units, List<ActiveUnit> isolated,
                                       List<InputRef> refs, PlatformPolicy policy,
                                       List<ExtractionEvent> events, List<String> diagnostics,
                                       Map<String, LoadedFile> byName) {
        Map<String, String> discardedComdat = chooseComdat(units, diagnostics);
        List<Reference> references = collectReferences(units);
        List<Decision> decisions = new ArrayList<>();
        for (Reference ref : references) {
            ElfSymbol requested = ref.requestedSymbol();
            List<Candidate> candidates = new ArrayList<>();
            for (ActiveUnit unit : units) {
                for (ElfSymbol symbol : unit.binary().symbols()) {
                    Candidate candidate = toCandidate(unit, symbol, ref, requested, discardedComdat, policy);
                    if (candidate != null) candidates.add(candidate);
                }
            }
            List<Candidate> retained = candidates.stream().filter(c -> c.eliminationReasons().isEmpty()).toList();
            int best = retained.stream().mapToInt(Candidate::rank).max().orElse(Integer.MIN_VALUE);
            List<Candidate> winners = retained.stream().filter(c -> c.rank() == best).toList();
            String status;
            String selected = null;
            String detail;
            if (requested.binding().toString().equals("WEAK") && winners.isEmpty()) {
                status = "UNRESOLVED_WEAK";
                detail = "undefined weak reference binds to zero";
            } else if (winners.isEmpty()) {
                status = "UNRESOLVED";
                detail = "no candidate satisfies name, visibility and version constraints";
            } else if (winners.size() > 1 && policy.tiePolicy() == TiePolicy.STRICT_AMBIGUOUS) {
                status = "AMBIGUOUS";
                detail = "multiple equal-rank candidates; load order is not used as a silent winner";
            } else {
                Candidate winner = winners.get(0);
                if (policy.tiePolicy() == TiePolicy.FIRST_IN_LOAD_ORDER && winners.size() > 1) {
                    winner = winners.stream().min(Comparator.comparingInt(Candidate::loadOrder)
                            .thenComparingInt(Candidate::archiveOrder)
                            .thenComparingInt(Candidate::symbolIndex)).orElseThrow();
                }
                selected = winner.candidateId();
                status = "SELECTED";
                detail = describe(winner);
            }
            String reqVersion = requested.version() == null ? null : requested.version().name();
            boolean nonDefault = requested.version() != null && requested.version().nonDefault();
            decisions.add(new Decision(ref.referenceId(), requested.name(), reqVersion, nonDefault,
                    selected, candidates, explain(candidates, selected, winners(status, selected, candidates)),
                    status, detail));
        }
        List<ObjectDependency> dependencies = dependencies(units, byName, refs);
        List<Relocation> relocations = units.stream().flatMap(u -> u.binary().relocations().stream()).toList();
        return new AdjudicationResult(refs, policy, units, isolated, dependencies,
                units.stream().flatMap(u -> u.binary().versionEdges().stream()).toList(),
                decisions, relocations, events, diagnostics);
    }

    private List<Candidate> winners(String status, String selected, List<Candidate> candidates) {
        return selected == null ? List.of() : candidates.stream().filter(c -> c.candidateId().equals(selected)).toList();
    }

    private List<String> explain(List<Candidate> candidates, String selected, List<Candidate> winners) {
        List<String> reasons = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.candidateId().equals(selected)) reasons.add("selected: " + describe(candidate));
            else if (!candidate.eliminationReasons().isEmpty()) reasons.add(candidate.displayName() + ": " + String.join("; ", candidate.eliminationReasons()));
            else reasons.add(candidate.displayName() + ": lower or equal unresolved rank");
        }
        return reasons;
    }

    private String describe(Candidate candidate) {
        return candidate.displayName() + " " + candidate.binding().toLowerCase()
                + " " + candidate.symbolName()
                + (candidate.version() == null || candidate.version().isBlank() ? "" : "@" + candidate.version())
                + " in " + candidate.section();
    }

    private Candidate toCandidate(ActiveUnit unit, ElfSymbol symbol, Reference ref, ElfSymbol requested,
                                  Map<String, String> discardedComdat, PlatformPolicy policy) {
        if (!symbol.defined() && !symbol.common()) return null;
        if (!symbol.name().equals(requested.name())) return null;
        List<String> reasons = new ArrayList<>();
        if (symbol.binding().toString().equals("LOCAL")) reasons.add("local symbol is not externally visible");
        if (symbol.visibility() == Visibility.HIDDEN || symbol.visibility() == Visibility.INTERNAL) {
            if (!unit.unitId().equals(ref.unitId())) reasons.add("hidden/internal visibility cannot bind across objects");
        }
        if (ref.relocation().copy()) {
            if (unit.kind() != BinaryKind.SHARED) reasons.add("copy relocation requires a shared-object source");
            if (symbol.visibility() != Visibility.DEFAULT) reasons.add("copy relocation requires default visibility");
            if (!symbol.type().toString().equals("OBJECT")) reasons.add("copy relocation requires an object symbol");
            if (!policy.copyRelocations()) reasons.add("platform policy disallows copy relocations");
        } else if (requested.visibility() == Visibility.HIDDEN && !unit.unitId().equals(ref.unitId())) {
            reasons.add("reference has hidden visibility");
        } else if (requested.visibility() == Visibility.PROTECTED && unit.kind() == BinaryKind.SHARED
                && !unit.unitId().equals(ref.unitId())) {
            reasons.add("protected reference cannot interpose another shared object");
        }
        if (symbol.sectionIndex() > 0) {
            String discard = discardedComdat.get(unit.unitId() + "#" + symbol.sectionIndex());
            if (discard != null) reasons.add("COMDAT section discarded in favor of " + discard);
        }
        if (!versionMatches(requested, symbol)) reasons.add("version does not match required symbol version");
        if (symbol.version() != null && symbol.version().nonDefault() && !versionExact(requested, symbol)) {
            reasons.add("non-default version is a distinct alias");
        }
        int rank = rank(unit, symbol);
        return new Candidate(candidateId(unit, symbol), unit.unitId(), unit.displayName(),
                symbol.name(), symbol.index(), symbol.version() == null ? null : symbol.version().name(),
                rank, unit.loadOrder(), unit.archiveOrder(), symbol.binding().toString(),
                symbol.visibility().toString(), symbol.type().toString(), symbol.common(),
                symbol.defined(), symbol.sectionName(), symbol.comdatSignature(), symbol.value(), symbol.size(),
                reasons);
    }

    private int rank(ActiveUnit unit, ElfSymbol symbol) {
        if (unit.kind() == BinaryKind.RELOCATABLE) {
            if (symbol.comdatSignature() != null) return symbol.binding().toString().equals("WEAK") ? 34 : 44;
            if (symbol.common()) return symbol.binding().toString().equals("WEAK") ? 30 : 40;
            return symbol.binding().toString().equals("WEAK") ? 50 : 60;
        }
        if (unit.kind() == BinaryKind.SHARED) {
            if (symbol.common()) return 10;
            return symbol.binding().toString().equals("WEAK") ? 20 : 25;
        }
        return 5;
    }

    private Map<String, String> chooseComdat(List<ActiveUnit> units, List<String> diagnostics) {
        Map<String, ActiveUnit> ownerBySignature = new HashMap<>();
        Map<String, String> discarded = new HashMap<>();
        for (ActiveUnit unit : units) {
            Set<String> seenGroup = new HashSet<>();
            for (var section : unit.binary().sections()) {
                if (section.groupSignature() == null || seenGroup.contains(section.groupSignature())) continue;
                seenGroup.add(section.groupSignature());
                ActiveUnit current = ownerBySignature.get(section.groupSignature());
                if (current == null) {
                    ownerBySignature.put(section.groupSignature(), unit);
                } else if (current.loadOrder() == unit.loadOrder() && current.archiveOrder() == unit.archiveOrder()) {
                    diagnostics.add("ambiguous COMDAT group " + section.groupSignature());
                } else {
                    String winner = current.displayName();
                    for (var related : unit.binary().sections()) {
                        if (section.groupSignature().equals(related.groupSignature())) discarded.put(unit.unitId() + "#" + related.index(), winner);
                    }
                }
            }
        }
        return discarded;
    }

    private List<Reference> collectReferences(List<ActiveUnit> units) {
        List<Reference> result = new ArrayList<>();
        for (ActiveUnit unit : units) {
            for (Relocation rel : unit.binary().relocations()) {
                ElfSymbol symbol = symbol(unit, rel.symbolIndex()).orElse(null);
                if (symbol == null || symbol.index() == 0 || symbol.name() == null || symbol.name().isBlank()) continue;
                String id = unit.unitId() + ":" + rel.sectionName() + "+" + rel.offset() + ":" + symbol.name();
                result.add(new Reference(id, unit.unitId(), unit.fileName(), rel, symbol));
            }
        }
        return result;
    }

    private Optional<ElfSymbol> symbol(ActiveUnit unit, long index) {
        return unit.binary().symbols().stream().filter(s -> s.index() == index).findFirst();
    }

    private boolean definesStrong(ParsedBinary binary, ElfSymbol requested) {
        return binary.symbols().stream().anyMatch(s -> s.defined() && !s.common()
                && !s.binding().toString().equals("LOCAL") && !s.binding().toString().equals("WEAK")
                && s.name().equals(requested.name()) && versionMatches(requested, s));
    }

    private boolean versionMatches(ElfSymbol requested, ElfSymbol candidate) {
        String need = requested.version() == null ? null : requested.version().name();
        String provide = candidate.version() == null ? null : candidate.version().name();
        boolean needBlank = need == null || need.isBlank();
        boolean provideBlank = provide == null || provide.isBlank();
        if (needBlank && provideBlank) return true;
        if (needBlank) return true;
        return need.equals(provide);
    }

    private boolean versionExact(ElfSymbol requested, ElfSymbol candidate) {
        String need = requested.version() == null ? null : requested.version().name();
        String provide = candidate.version() == null ? null : candidate.version().name();
        if (need == null) need = "";
        if (provide == null) provide = "";
        return need.equals(provide);
    }

    private String candidateId(ActiveUnit unit, ElfSymbol symbol) {
        return unit.unitId() + "#sym" + symbol.index()
                + (symbol.version() == null || symbol.version().name() == null ? "" : "#" + symbol.version().name());
    }

    private String unitId(String fileName, String member) {
        return "input:" + fileName + (member == null ? "" : ":" + member);
    }

    private String display(String fileName, String member) {
        return fileName + (member == null ? "" : "(" + member + ")");
    }

    private List<ObjectDependency> dependencies(List<ActiveUnit> units, Map<String, LoadedFile> byName,
                                                List<InputRef> refs) {
        List<ObjectDependency> result = new ArrayList<>();
        for (ActiveUnit unit : units) {
            for (String needed : unit.binary().needed()) {
                boolean available = byName.containsKey(needed) || refs.stream().anyMatch(r -> r.name().equals(needed));
                result.add(new ObjectDependency(unit.unitId(), unit.displayName(), needed,
                        available ? "DT_NEEDED" : "MISSING_DT_NEEDED"));
            }
            for (var version : unit.binary().versions()) {
                if (version.required()) result.add(new ObjectDependency(unit.unitId(), unit.displayName(),
                        version.dependencyFile() + "!" + version.name(),
                        version.nonDefault() ? "VERSION_NEEDED_WEAK" : "VERSION_NEEDED"));
            }
        }
        return result;
    }

    public SchemeComparison compare(AdjudicationResult before, AdjudicationResult after) {
        Map<String, Decision> a = new LinkedHashMap<>();
        Map<String, Decision> z = new LinkedHashMap<>();
        before.decisions().forEach(d -> a.put(d.referenceId(), d));
        after.decisions().forEach(d -> z.put(d.referenceId(), d));
        Set<String> all = new LinkedHashSet<>();
        all.addAll(a.keySet()); all.addAll(z.keySet());
        List<ReferenceChange> changes = new ArrayList<>();
        List<String> switches = new ArrayList<>();
        List<String> addresses = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        for (String id : all) {
            Decision left = a.get(id), right = z.get(id);
            String lp = left == null ? null : providerKey(before, left.selectedCandidateId());
            String rp = right == null ? null : providerKey(after, right.selectedCandidateId());
            String kind;
            if (java.util.Objects.equals(lp, rp)
                    && java.util.Objects.equals(left == null ? null : left.status(), right == null ? null : right.status())) {
                kind = "UNCHANGED";
                unchanged.add(id);
            } else if (lp != null && rp != null && sameProviderDifferentAddress(lp, rp)) {
                kind = "ADDRESS_ONLY";
                addresses.add(id);
            } else {
                kind = "PROVIDER_SWITCH";
                switches.add(id);
            }
            if (!kind.equals("UNCHANGED")) changes.add(new ReferenceChange(id, lp, rp, kind,
                    left == null ? null : left.status(), right == null ? null : right.status()));
        }
        return new SchemeComparison(changes, switches, addresses, unchanged);
    }

    private String providerKey(AdjudicationResult result, String candidateId) {
        if (candidateId == null) return null;
        return result.activeUnits().stream()
                .flatMap(u -> u.binary().symbols().stream().map(s -> Map.entry(candidateId(u, s), u.sha256() + "|" + s.value())))
                .filter(e -> e.getKey().equals(candidateId)).map(Map.Entry::getValue).findFirst().orElse(candidateId);
    }

    private boolean sameProviderDifferentAddress(String left, String right) {
        String[] lp = left.split("\\|");
        String[] rp = right.split("\\|");
        return lp.length == 2 && rp.length == 2 && lp[0].equals(rp[0]) && !lp[1].equals(rp[1]);
    }

    private record ExtractionProposal(String archive, int loadOrder, ArchiveMember member,
                                      int archiveOrder, String symbol) {}
}
