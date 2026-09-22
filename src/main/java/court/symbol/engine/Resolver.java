package court.symbol.engine;

import court.symbol.ar.Archive;
import court.symbol.elf.Elf;
import court.symbol.elf.ElfException;
import court.symbol.elf.ElfFile;
import court.symbol.model.Decision;
import court.symbol.model.DependencyNode;
import court.symbol.model.InputObject;
import court.symbol.model.PlatformPolicy;
import court.symbol.model.Reference;
import court.symbol.model.ResolutionReport;
import court.symbol.model.StepEvent;
import court.symbol.model.SymbolCandidate;
import court.symbol.model.VersionGraph;
import court.symbol.model.VersionNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The symbol arbitration court. Pure Java: no invocation of ld/ld.so.
 *
 * Pipeline:
 *   1. parse each input in the user's explicit order (corrupt ones isolated)
 *   2. STATIC: archive member lazy extraction until a fixed point (cycles safe)
 *   3. COMDAT (section group) deduplication across the extracted set
 *   4. collect references (relocations + dangling undefined dynamic symbols)
 *   5. for each reference: enumerate candidates, reject with explicit reasons,
 *      rank, keep ambiguity when ranking cannot decide
 *   6. record replayable steps and the version graph
 */
public final class Resolver {

    private final List<InputObject> inputs;
    private final PlatformPolicy policy;
    private final String targetPlatform;
    private final String planId;
    private final int planVersion;
    private final long createdAt;

    private final List<LoadedProvider> providers = new ArrayList<>();
    private final List<ArchiveEntry> archiveEntries = new ArrayList<>();
    private final List<String> parseErrors = new ArrayList<>();
    private final List<StepEvent> steps = new ArrayList<>();

    private int stepSeq = 0;

    private record ArchiveEntry(InputObject input, Archive archive, List<ElfFile> memberElfs) {
    }

    public Resolver(List<InputObject> inputs, PlatformPolicy policy, String targetPlatform,
                    String planId, int planVersion, long createdAt) {
        this.inputs = List.copyOf(inputs);
        this.policy = policy == null ? PlatformPolicy.staticDefault() : policy;
        this.targetPlatform = targetPlatform;
        this.planId = planId;
        this.planVersion = planVersion;
        this.createdAt = createdAt;
    }

    public ResolutionReport resolve() {
        loadInputs();
        if (PlatformPolicy.STATIC.equals(policy.linkModel())) {
            lazyArchiveExtraction();
        }
        dedupeComdat();
        Map<RefKeyLite, Reference> refs = collectReferences();
        Map<String, List<SymbolCandidate>> providersByName = collectCandidates();

        Map<String, java.util.Set<String>> aliasByProvider = new LinkedHashMap<>();
        for (LoadedProvider p : providers) {
            aliasByProvider.put(p.id(), p.versionAliases());
        }
        List<Decision> decisions = new ArrayList<>();
        for (Reference ref : refs.values()) {
            Decision d = CandidateEvaluator.evaluate(
                    ref, providersByName.getOrDefault(ref.symbol(), List.of()),
                    providers, aliasByProvider,
                    policy, steps, stepSeq, createdAt, targetPlatform);
            // keep global sequence in sync with any steps the evaluator emitted
            while (!steps.isEmpty() && steps.get(steps.size() - 1).sequence() > stepSeq) {
                stepSeq = steps.get(steps.size() - 1).sequence();
            }
            decisions.add(d);
        }
        decisions.sort(Comparator
                .comparing((Decision d) -> d.reference().loadOrder())
                .thenComparing(d -> d.reference().providerId())
                .thenComparing(d -> d.reference().symbol())
                .thenComparing(d -> d.reference().version() == null ? "" : d.reference().version()));

        steps.add(new StepEvent(++stepSeq, "FINISH", "裁决完成",
                decisions.size() + " 个引用, "
                        + decisions.stream().filter(d -> d.status().equals(Decision.AMBIGUOUS)).count()
                        + " 个歧义, "
                        + decisions.stream().filter(d -> d.status().equals(Decision.UNRESOLVED)).count()
                        + " 个未解析",
                null, activeProviderIds(), tableCounts(), null));

        return new ResolutionReport(planId, planVersion, policy, targetPlatform,
                buildDependencyGraph(), buildVersionGraph(), List.copyOf(decisions),
                List.copyOf(steps), List.copyOf(parseErrors), createdAt);
    }

    // ------------------------------------------------------------- 1. loading

    private void loadInputs() {
        for (InputObject in : inputs) {
            try {
                if (in.archive()) {
                    Archive ar = Archive.parse(in.content());
                    List<ElfFile> memberElfs = new ArrayList<>();
                    for (Archive.Member m : ar.members()) {
                        try {
                            memberElfs.add(m.parseElf());
                        } catch (ElfException e) {
                            parseErrors.add("归档成员隔离 " + in.label() + "!" + m.name()
                                    + ": " + e.getMessage());
                            memberElfs.add(null);
                        }
                    }
                    archiveEntries.add(new ArchiveEntry(in, ar, memberElfs));
                } else {
                    ElfFile elf = ElfFile.parse(in.content());
                    String id = providerId(in, null);
                    providers.add(new LoadedProvider(id, in.label(), in.order(), false,
                            null, null, in, elf, true, false, java.util.Set.of(), versionAliases(elf)));
                    steps.add(new StepEvent(++stepSeq, "SCAN", "载入 " + in.label(),
                            elf.relocatable() ? "可重定位对象" : "共享对象"
                                    + ", " + elf.symbols().size() + " 个符号, "
                                    + elf.needed().size() + " 个 DT_NEEDED",
                            id, activeProviderIds(), tableCounts(), null));
                }
            } catch (RuntimeException e) {
                parseErrors.add("对象隔离(不参与裁决) " + in.label() + ": " + e.getMessage());
            }
        }
    }

    private String providerId(InputObject in, String member) {
        return member == null ? "obj-" + in.order() : "obj-" + in.order() + "::" + member;
    }

    private static java.util.Set<String> versionAliases(ElfFile elf) {
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        for (Elf.VersionDefinition d : elf.versionDefinitions()) {
            all.add(d.name());
            all.addAll(d.aliases());
        }
        return all;
    }

    private List<String> activeProviderIds() {
        List<String> out = new ArrayList<>();
        for (LoadedProvider p : providers) {
            if (p.extracted() && !p.comdatEliminated()) {
                out.add(p.id());
            }
        }
        return out;
    }

    private Map<String, Integer> tableCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (LoadedProvider p : providers) {
            if (p.extracted() && !p.comdatEliminated()) {
                long n = p.elf().symbols().stream().filter(s -> s.defined()
                        && (s.strong() || s.weak())).count();
                out.put(p.id(), (int) n);
            }
        }
        return out;
    }

    // ------------------------------------------------- 2. archive lazy extract

    private void lazyArchiveExtraction() {
        boolean changed = true;
        int guard = 0;
        Set<String> extractedKeys = new LinkedHashSet<>();
        while (changed) {
            changed = false;
            if (++guard > 100_000) {
                parseErrors.add("archive 抽取迭代上限保护触发(疑似异常归档)");
                break;
            }
            Set<String> neededNow = collectUndefinedNames();
            for (ArchiveEntry e : archiveEntries) {
                List<Archive.Member> members = e.archive().members();
                for (int i = 0; i < members.size(); i++) {
                    Archive.Member member = members.get(i);
                    ElfFile elf = i < e.memberElfs().size() ? e.memberElfs().get(i) : null;
                    if (elf == null) {
                        continue;
                    }
                    String pid = providerId(e.input(), member.name());
                    if (extractedKeys.contains(pid)) {
                        continue;
                    }
                    List<String> satisfies = new ArrayList<>();
                    for (Elf.Symbol s : elf.symbols()) {
                        if (extractableDefinition(s) && neededNow.contains(s.name())) {
                            satisfies.add(s.name());
                        }
                    }
                    if (!satisfies.isEmpty()) {
                        LoadedProvider lp = new LoadedProvider(pid,
                                e.input().label() + "(" + member.name() + ")",
                                e.input().order(), true, "obj-" + e.input().order(),
                                e.input().label(), e.input(), elf, true, false,
                                java.util.Set.of(), versionAliases(elf));
                        providers.add(lp);
                        extractedKeys.add(pid);
                        steps.add(new StepEvent(++stepSeq, "ARCHIVE_EXTRACT",
                                "抽取归档成员 " + lp.label(),
                                "惰性抽取: 该成员定义当前未决符号 "
                                        + String.join(", ", new LinkedHashSet<>(satisfies)),
                                pid, activeProviderIds(), tableCounts(), null));
                        changed = true;
                    }
                }
            }
        }
    }

    /**
     * Definitions that may pull a member out of an archive. A common symbol
     * only satisfies a missing *strong* definition when no stronger provider
     * is present; archive common symbols are extracted when needed too.
     */
    private boolean extractableDefinition(Elf.Symbol s) {
        if (!s.defined() || s.local()) {
            return false;
        }
        if (s.type() == Elf.STT_FILE || s.type() == Elf.STT_SECTION) {
            return false;
        }
        if (s.hiddenVis()) {
            return false; // hidden symbols never resolve cross-object references
        }
        return s.strong() || s.weak() || s.common();
    }

    private Set<String> collectUndefinedNames() {
        Set<String> names = new LinkedHashSet<>();
        for (LoadedProvider p : providers) {
            if (!p.extracted() || p.comdatEliminated()) {
                continue;
            }
            ElfFile elf = p.elf();
            for (Elf.Symbol s : elf.symbols()) {
                if (!s.defined() && !s.local()) {
                    // weak undefined refs do not force extraction on their own
                    if (s.weak() && !hasRelocationAgainst(elf, s)) {
                        continue;
                    }
                    names.add(s.name());
                }
            }
        }
        // subtract names already defined by a strong/weak extracted provider
        Set<String> defined = new LinkedHashSet<>();
        for (LoadedProvider p : providers) {
            if (!p.extracted() || p.comdatEliminated()) {
                continue;
            }
            for (Elf.Symbol s : p.elf().symbols()) {
                if (s.defined() && !s.local() && !s.hiddenVis()
                        && (s.strong() || s.weak() || s.common())) {
                    defined.add(s.name());
                }
            }
        }
        names.removeAll(defined);
        return names;
    }

    private boolean hasRelocationAgainst(ElfFile elf, Elf.Symbol symbol) {
        for (Elf.SymbolTable t : elf.symbolTables()) {
            for (List<Elf.Relocation> rels : t.relocationsBySection().values()) {
                for (Elf.Relocation rel : rels) {
                    if (rel.symbolIndex() == symbol.index()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------- 3. COMDAT

    private void dedupeComdat() {
        // signature -> "pid::groupSectionIdx" chosen winner (first in explicit order).
        Map<String, String> winners = new LinkedHashMap<>();
        List<LoadedProvider> ordered = new ArrayList<>(providers);
        ordered.sort(Comparator.comparingInt(LoadedProvider::loadOrder));
        for (LoadedProvider p : ordered) {
            if (!p.extracted()) {
                continue;
            }
            for (Elf.SectionGroup g : p.elf().groups()) {
                if (!g.comdat()) {
                    continue;
                }
                String sig = g.signature(p.elf());
                if (sig == null) {
                    sig = "group-" + p.id() + "-" + g.sectionIndex();
                }
                winners.computeIfAbsent("comdat:" + sig,
                        k -> p.id() + "::" + g.sectionIndex());
            }
        }
        for (int pi = 0; pi < providers.size(); pi++) {
            LoadedProvider p = providers.get(pi);
            if (!p.extracted() || p.elf().groups().isEmpty()) {
                continue;
            }
            Set<Integer> dead = new LinkedHashSet<>();
            Set<String> killedSigs = new LinkedHashSet<>();
            for (Elf.SectionGroup g : p.elf().groups()) {
                if (!g.comdat()) {
                    continue;
                }
                String sig = g.signature(p.elf());
                String mine = p.id() + "::" + g.sectionIndex();
                String winner = winners.get("comdat:" + sig);
                if (winner != null && !winner.equals(mine)) {
                    dead.addAll(g.memberSections());
                    killedSigs.add(sig);
                }
            }
            if (!dead.isEmpty()) {
                LoadedProvider np = p.withEliminatedSections(dead);
                providers.set(pi, np);
                steps.add(new StepEvent(++stepSeq, "COMDAT",
                        "COMDAT 去重: " + p.label(),
                        "section group " + killedSigs
                                + " 已在别处保留; 丢弃本组节区与其中局部定义(普通覆盖规则不适用)",
                        p.id(), activeProviderIds(), tableCounts(), null));
            }
        }
    }

    // ----------------------------------------------------- 4. reference model

    private static final class RefKeyLite {
        final String providerId;
        final String symbol;
        final String version;

        RefKeyLite(String providerId, String symbol, String version) {
            this.providerId = providerId;
            this.symbol = symbol;
            this.version = version;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RefKeyLite other)) {
                return false;
            }
            return providerId.equals(other.providerId) && symbol.equals(other.symbol)
                    && java.util.Objects.equals(version, other.version);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(providerId, symbol, version);
        }
    }

    private Map<RefKeyLite, Reference> collectReferences() {
        Map<RefKeyLite, Reference> refs = new LinkedHashMap<>();
        for (LoadedProvider p : providers) {
            if (!p.extracted() || p.comdatEliminated()) {
                continue;
            }
            ElfFile elf = p.elf();
            Map<Integer, List<Elf.Relocation>> relsBySym = new LinkedHashMap<>();
            for (Elf.SymbolTable t : elf.symbolTables()) {
                for (Map.Entry<Integer, List<Elf.Relocation>> e
                        : t.relocationsBySection().entrySet()) {
                    for (Elf.Relocation rel : e.getValue()) {
                        relsBySym.computeIfAbsent(rel.symbolIndex(), k -> new ArrayList<>())
                                .add(rel);
                    }
                }
            }
            List<Elf.SymbolTable> tables = elf.symbolTables();
            for (Elf.SymbolTable t : tables) {
                for (Elf.Symbol s : t.symbols()) {
                    if (s.name() == null || s.name().isEmpty()) {
                        continue;
                    }
                    boolean reloc = relsBySym.containsKey(s.index());
                    if (s.defined()) {
                        // Defined symbols only generate a reference under dynamic
                        // interposition rules (non-protected default-visible defs
                        // may still be preempted), or when R_*_COPY targets them.
                        boolean copyReloc = relsBySym.getOrDefault(s.index(), List.of())
                                .stream().anyMatch(Elf.Relocation::copyReloc);
                        if (!PlatformPolicy.DYNAMIC.equals(policy.linkModel())
                                || !t.dynamic() || !copyReloc) {
                            continue;
                        }
                        addReference(refs, p, s, relsBySym, true);
                        continue;
                    }
                    if (s.local()) {
                        continue;
                    }
                    if (!t.dynamic() && !elf.relocatable()) {
                        continue; // static symtab of an .so is not a live reference set
                    }
                    // undefined in a .dynsym without any relocation: only count
                    // for shared objects; in relocatable objects a bare undefined
                    // symbol (no reloc) carries no actual use.
                    if (!reloc && elf.relocatable()) {
                        continue;
                    }
                    addReference(refs, p, s, relsBySym, false);
                }
            }
        }
        return refs;
    }

    private void addReference(Map<RefKeyLite, Reference> refs, LoadedProvider p,
                              Elf.Symbol s,
                              Map<Integer, List<Elf.Relocation>> relsBySym,
                              boolean definedCopySource) {
        String version = null;
        if (s.version() != null) {
            version = s.version().display();
        }
        RefKeyLite key = new RefKeyLite(p.id(), s.name(), version);
        List<Elf.Relocation> rels = relsBySym.getOrDefault(s.index(), List.of());
        List<Reference.RelocRef> relocViews = new ArrayList<>();
        boolean copyReloc = false;
        for (Elf.Relocation rel : rels) {
            relocViews.add(new Reference.RelocRef(rel.targetSection(), rel.offset(),
                    rel.addend(), rel.type(), RelocationNames.name(headerMachine(p), rel.type())));
            copyReloc |= rel.copyReloc();
        }
        String requestedBy = rels.isEmpty() ? "动态符号表(无对应重定位)"
                : rels.get(0).targetSection();
        Reference existing = refs.get(key);
        if (existing != null) {
            List<Reference.RelocRef> merged = new ArrayList<>(existing.relocations());
            merged.addAll(relocViews);
            refs.put(key, new Reference(existing.id(), existing.providerId(),
                    existing.providerLabel(), existing.loadOrder(), existing.symbol(),
                    existing.version(), existing.weakRef() || s.weak(),
                    existing.copyReloc() || copyReloc, requestedBy,
                    List.copyOf(merged)));
            return;
        }
        String id = "ref-" + p.id() + ":" + s.name()
                + (version == null ? "" : "@" + version);
        refs.put(key, new Reference(id, p.id(), p.label(), p.loadOrder(), s.name(),
                version, s.weak(), copyReloc, requestedBy, List.copyOf(relocViews)));
    }

    private int headerMachine(LoadedProvider p) {
        return p.elf().header().machine();
    }

    // ----------------------------------------------------- 5. candidates view

    private Map<String, List<SymbolCandidate>> collectCandidates() {
        Map<String, List<SymbolCandidate>> byName = new LinkedHashMap<>();
        for (LoadedProvider p : providers) {
            if (!p.extracted() || p.comdatEliminated()) {
                continue;
            }
            boolean shared = p.elf().sharedObject();
            Elf.SymbolTable table = shared ? p.elf().dynamicTable() : p.elf().primaryTable();
            if (table == null) {
                continue;
            }
            for (Elf.Symbol s : table.symbols()) {
                if (s.name() == null || s.name().isEmpty() || !s.defined() || s.local()) {
                    continue;
                }
                if (!p.symbolAlive(s)) {
                    continue; // eliminated COMDAT section's definitions
                }
                if (s.type() == Elf.STT_FILE || s.type() == Elf.STT_SECTION) {
                    continue;
                }
                SymbolCandidate c = new SymbolCandidate(p.id(), p.label(), p.loadOrder(),
                        p.archiveMember(), p.label(), s);
                byName.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(c);
            }
        }
        return byName;
    }

    // ------------------------------------------------------------- graph views

    private List<DependencyNode> buildDependencyGraph() {
        List<DependencyNode> out = new ArrayList<>();
        for (LoadedProvider p : providers) {
            out.add(new DependencyNode(p.id(), p.label(),
                    p.elf().relocatable() ? "OBJECT" : "SHARED", p.loadOrder(),
                    p.extracted(), p.archiveMember() ? p.archiveLabel() : null,
                    p.elf().needed()));
        }
        out.sort(Comparator.comparingInt(DependencyNode::order)
                .thenComparing(DependencyNode::id));
        return out;
    }

    private List<VersionNode> buildVersionGraph() {
        List<VersionNode> out = new ArrayList<>();
        for (LoadedProvider p : providers) {
            if (!p.extracted() || p.comdatEliminated()) {
                continue;
            }
            List<VersionGraph.DefVersion> defs = new ArrayList<>();
            for (Elf.VersionDefinition d : p.elf().versionDefinitions()) {
                defs.add(new VersionGraph.DefVersion(d.ndx(), d.name(), d.aliases(),
                        d.base(), false));
            }
            List<VersionGraph.NeedVersion> needs = new ArrayList<>();
            for (Elf.VersionNeed n : p.elf().versionNeeds()) {
                needs.add(new VersionGraph.NeedVersion(n.ndx(), n.name(), n.soname(), n.weak()));
            }
            if (!defs.isEmpty() || !needs.isEmpty()) {
                out.add(new VersionNode(p.id(), p.label(), defs, needs));
            }
        }
        return out;
    }
}
