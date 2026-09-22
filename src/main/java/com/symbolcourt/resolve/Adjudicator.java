package com.symbolcourt.resolve;

import com.symbolcourt.elf.Archive;
import com.symbolcourt.elf.ArchiveParser;
import com.symbolcourt.elf.ComdatGroup;
import com.symbolcourt.elf.ElfFile;
import com.symbolcourt.elf.ElfParser;
import com.symbolcourt.elf.Kind;
import com.symbolcourt.elf.Relocation;
import com.symbolcourt.elf.Section;
import com.symbolcourt.elf.Symbol;
import com.symbolcourt.elf.SymbolBinding;
import com.symbolcourt.elf.Visibility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local symbol-resolution engine. Reproduces the decisive parts of a static and
 * first-stage dynamic link without invoking a system linker:
 *
 * <ul>
 *   <li>strong / weak / common precedence;</li>
 *   <li>local / hidden / protected visibility filtering;</li>
 *   <li>default vs non-default symbol versions, indices resolved to names;</li>
 *   <li>lazy, fixpoint archive extraction (handles archive cycles);</li>
 *   <li>COMDAT group deduplication;</li>
 *   <li>COPY relocation handling that is not swallowed by override rules;</li>
 *   <li>deterministic ordering: ties are kept ambiguous, never decided by the
 *       file-system traversal order.</li>
 * </ul>
 */
public final class Adjudicator {

    private final PlatformPolicy policy;
    private AdjudicationResult result;
    private int eventSeq;
    private int unitSeq;

    // global static definition table: name -> merged definition state
    private final Map<String, GlobalDef> globals = new LinkedHashMap<>();
    // all providers ever considered for a name (static + extracted)
    private final Map<String, List<Provider>> allStaticProviders = new LinkedHashMap<>();

    // loaded units and archives
    private final List<LoadedUnit> objectUnits = new ArrayList<>();
    private final List<LoadedUnit> sharedUnits = new ArrayList<>();
    private final List<ArchiveHolder> archives = new ArrayList<>();
    private final Map<String, LoadedUnit> unitById = new LinkedHashMap<>();

    // COMDAT: signature -> winning unit/group
    private final Map<String, ComdatWinner> comdatWinners = new LinkedHashMap<>();

    private long addressCursor = 0x400000L;

    public Adjudicator(PlatformPolicy policy) {
        this.policy = policy;
    }

    public AdjudicationResult adjudicate(String schemeName, List<InputEntry> inputs) {
        result = new AdjudicationResult(schemeName, policy.name);
        eventSeq = 0;
        unitSeq = 0;

        ingest(inputs);
        loadSharedClosure();
        runFixpointArchiveScan();
        chooseComdatGroups();
        buildGlobalDefinitions();
        recordVersionGraph();
        resolveStaticReferences();
        resolveDynamicReferences();
        checkVersionRequirements();
        checkDuplicateStrong();
        assignAddresses();
        attachRelocations();
        orderEventsAndUnits();
        return result;
    }

    private static final class ArchiveHolder {
        final Archive archive;
        final String label;
        final int order;
        final Map<String, LoadedUnit> extracted = new LinkedHashMap<>();

        ArchiveHolder(Archive archive, String label, int order) {
            this.archive = archive;
            this.label = label;
            this.order = order;
        }
    }

    private static final class ComdatWinner {
        final LoadedUnit unit;
        final ComdatGroup group;

        ComdatWinner(LoadedUnit unit, ComdatGroup group) {
            this.unit = unit;
            this.group = group;
        }
    }
}

    // ------------------------------------------------------------- ingestion

    private void ingest(List<InputEntry> inputs) {
        for (int i = 0; i < inputs.size(); i++) {
            InputEntry in = inputs.get(i);
            result.loadOrder.add(in.label);
            byte[] content = in.content;
            if (ArchiveParser.looksLikeArchive(content)) {
                try {
                    Archive ar = new ArchiveParser(content).parse(in.label);
                    archives.add(new ArchiveHolder(ar, in.label, i));
                    event(PlayEventKinds.INPUT, "归档入库 " + in.label,
                            "含 " + ar.members.size() + " 个惰性成员，暂不抽取")
                            .put("archive", in.label)
                            .put("members", new ArrayList<>(ar.members.keySet()));
                } catch (Exception ex) {
                    result.parseFailures.add(new ParseFailure(in.label, ex.getMessage()));
                }
                continue;
            }
            ElfFile elf = parseElf(in.label, content);
            if (elf == null) {
                continue;
            }
            elf.sourceName = in.label;
            LoadedUnit unit = addUnit(in.label, unitKind(elf), elf, null, null, i);
            if (elf.kind == Kind.RELOCATABLE) {
                objectUnits.add(unit);
            } else {
                sharedUnits.add(unit);
            }
        }
    }

    private ElfFile parseElf(String label, byte[] content) {
        try {
            return new ElfParser(content).parse();
        } catch (Exception ex) {
            result.parseFailures.add(new ParseFailure(label,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            return null;
        }
    }

    private UnitKind unitKind(ElfFile elf) {
        return switch (elf.kind) {
            case RELOCATABLE -> UnitKind.OBJECT;
            case EXECUTABLE -> UnitKind.EXECUTABLE;
            default -> UnitKind.SHARED;
        };
    }

    private LoadedUnit addUnit(String label, UnitKind kind, ElfFile elf,
                               String archiveLabel, String memberName, int order) {
        String id = archiveLabel == null
                ? "u" + unitSeq
                : "u" + unitSeq;
        unitSeq++;
        LoadedUnit unit = new LoadedUnit(id, label, kind, elf, archiveLabel, memberName, order);
        unitById.put(id, unit);
        result.units.add(unit);
        return unit;
    }

    /**
     * Expand DT_NEEDED closure against the objects already provided. A NEEDED
     * name is satisfied when another supplied shared object's soname (or label)
     * matches; otherwise an unsatisfied edge is recorded.
     */
    private void loadSharedClosure() {
        Map<String, LoadedUnit> bySoname = new LinkedHashMap<>();
        for (LoadedUnit u : new ArrayList<>(sharedUnits)) {
            if (u.elf.soname != null) {
                bySoname.put(u.elf.soname, u);
            }
            bySoname.put(u.label, u);
        }
        List<LoadedUnit> queue = new ArrayList<>(sharedUnits);
        for (int qi = 0; qi < queue.size(); qi++) {
            LoadedUnit u = queue.get(qi);
            for (String need : u.elf.needed) {
                LoadedUnit dep = bySoname.get(need);
                if (dep != null) {
                    result.dependencyEdges.add(new DependencyEdge(u.id, dep.id, need, true));
                } else {
                    result.dependencyEdges.add(new DependencyEdge(u.id, null, need, false));
                }
            }
        }
    }

    // ------------------------------------------------- lazy archive extraction

    /**
     * ld-style fixpoint scan. Each pass walks archives in explicit command-line
     * order; a member is extracted only when it defines a currently unresolved
     * (non-weak) symbol. Extracted objects may introduce new undefined symbols,
     * so scanning repeats until a full pass extracts nothing. This is what
     * resolves circular archive dependencies.
     */
    private void runFixpointArchiveScan() {
        boolean changed = true;
        int pass = 0;
        while (changed) {
            changed = false;
            pass++;
            for (ArchiveHolder ah : archives) {
                List<String> pulledThisArchive = new ArrayList<>();
                for (Map.Entry<String, byte[]> member : ah.archive.members.entrySet()) {
                    if (ah.extracted.containsKey(member.getKey())) {
                        continue;
                    }
                    byte[] content = member.getValue();
                    ElfFile elf = parseElf(ah.label + "(" + member.getKey() + ")", content);
                    if (elf == null) {
                        // corrupt member: isolated, skip this member only
                        continue;
                    }
                    elf.sourceName = ah.label + "(" + member.getKey() + ")";
                    List<String> neededDefs = memberSatisfies(elf);
                    boolean comdatPull = memberComdatSatisfies(elf);
                    if (!neededDefs.isEmpty() || comdatPull) {
                        LoadedUnit unit = addUnit(ah.label + "(" + member.getKey() + ")",
                                UnitKind.OBJECT, elf, ah.label, member.getKey(), ah.order);
                        ah.extracted.put(member.getKey(), unit);
                        objectUnits.add(unit);
                        pulledThisArchive.add(member.getKey());
                        changed = true;
                        event(PlayEventKinds.ARCHIVE_EXTRACT,
                                "抽取 " + ah.label + "(" + member.getKey() + ")",
                                "惰性成员提供了未决符号 " + neededDefs
                                        + (comdatPull ? "（含待选 COMDAT）" : ""))
                                .put("archive", ah.label)
                                .put("member", member.getKey())
                                .put("pass", pass)
                                .put("satisfies", neededDefs)
                                .put("unit", unit.id);
                    }
                }
                if (!pulledThisArchive.isEmpty()) {
                    event(PlayEventKinds.ARCHIVE_SCAN,
                            "扫描 " + ah.archive.sourceName + "（第 " + pass + " 轮）",
                            "本轮抽取 " + pulledThisArchive.size() + " 个成员")
                            .put("archive", ah.archive.sourceName)
                            .put("pass", pass)
                            .put("extracted", pulledThisArchive);
                }
            }
        }
    }

    /** Returns the strong/global defined names this member would resolve now. */
    private List<String> memberSatisfies(ElfFile elf) {
        List<String> out = new ArrayList<>();
        for (String undef : currentUndefinedStrong()) {
            for (Symbol s : elf.staticSymbols) {
                if (s.isDefined() && s.binding != SymbolBinding.LOCAL
                        && s.name.equals(undef) && !s.isCommon()
                        && !sectionDiscardedByComdat(elf, s)) {
                    if (!out.contains(s.name)) {
                        out.add(s.name);
                    }
                }
            }
        }
        return out;
    }

    private boolean memberComdatSatisfies(ElfFile elf) {
        // COMDAT sections are pulled when another input already references the
        // group signature's section symbol; represented via undefined name equal
        // to a group signature.
        for (ComdatGroup g : elf.groups) {
            if (g.comdat && g.signature != null && currentUndefinedStrong().contains(g.signature)) {
                return true;
            }
        }
        return false;
    }

    /** Undefined, non-weak references across all objects currently loaded. */
    private List<String> currentUndefinedStrong() {
        List<String> out = new ArrayList<>();
        for (LoadedUnit u : objectUnits) {
            for (Symbol s : u.elf.staticSymbols) {
                if (s.isUndefined() && s.binding != SymbolBinding.WEAK
                        && !s.name.isEmpty() && !out.contains(s.name)) {
                    // already defined globally?
                    GlobalDef def = globals.get(s.name);
                    if (def == null || !def.hasStrong()) {
                        out.add(s.name);
                    }
                }
            }
        }
        return out;
    }

    private boolean sectionDiscardedByComdat(ElfFile elf, Symbol s) {
        // Decided later in chooseComdatGroups; archive members are pulled before
        // COMDAT selection, so this returns false at extraction time.
        return false;
    }

    // ------------------------------------------------------------------ COMDAT

    private void chooseComdatGroups() {
        // Iterate units in deterministic load order; the first COMDAT group for a
        // signature wins. Later duplicate groups are discarded (section + group).
        for (LoadedUnit u : objectUnits) {
            for (ComdatGroup g : u.elf.groups) {
                if (!g.comdat) {
                    continue;
                }
                ComdatWinner existing = comdatWinners.get(g.signature);
                if (existing == null) {
                    comdatWinners.put(g.signature, new ComdatWinner(u, g));
                    event(PlayEventKinds.COMDAT,
                            "COMDAT 选定 '" + g.signature + "'",
                            "首个出现的定义位于 " + u.label)
                            .put("signature", g.signature)
                            .put("winner", u.id)
                            .put("sections", g.memberSections);
                } else {
                    for (int sec : g.memberSections) {
                        u.discardedSections.add(sec);
                    }
                    u.discardedSections.add(g.groupSectionIndex);
                    event(PlayEventKinds.COMDAT,
                            "COMDAT 去重 '" + g.signature + "'",
                            u.label + " 的重复组被剔除，保留 " + existing.unit.label)
                            .put("signature", g.signature)
                            .put("winner", existing.unit.id)
                            .put("discardedFrom", u.id)
                            .put("sections", g.memberSections);
                }
            }
        }
    }

    private boolean symbolSectionDiscarded(LoadedUnit u, Symbol s) {
        if (s.sectionIndex < 0 || s.sectionIndex >= u.elf.sections.size()) {
            return false;
        }
        return u.discardedSections.contains((int) s.sectionIndex);
    }

    // ------------------------------------------------------- global definition

    private static final class GlobalDef {
        final String name;
        // strong (non-common) definitions in encounter order
        final List<Provider> strong = new ArrayList<>();
        // weak definitions in encounter order
        final List<Provider> weak = new ArrayList<>();
        // common (tentative) definitions
        final List<Provider> common = new ArrayList<>();

        GlobalDef(String name) {
            this.name = name;
        }

        boolean hasStrong() {
            return !strong.isEmpty();
        }
    }

    private void buildGlobalDefinitions() {
        for (LoadedUnit u : objectUnits) {
            for (Symbol s : u.elf.staticSymbols) {
                if (s.name.isEmpty() || s.binding == SymbolBinding.LOCAL || s.isUndefined()) {
                    continue;
                }
                if (symbolSectionDiscarded(u, s)) {
                    continue;
                }
                String section = sectionName(u, s);
                Provider p = new Provider(u, s, section);
                GlobalDef def = globals.computeIfAbsent(s.name, GlobalDef::new);
                allStaticProviders.computeIfAbsent(s.name, k -> new ArrayList<>()).add(p);
                GlobalDef before = snapshot(def);
                if (s.isCommon()) {
                    if (!contains(def.common, p)) {
                        def.common.add(p);
                    }
                } else if (s.binding == SymbolBinding.WEAK) {
                    if (!def.hasStrong() && !contains(def.weak, p)) {
                        def.weak.add(p);
                    }
                } else {
                    if (!contains(def.strong, p)) {
                        def.strong.add(p);
                        // strong supersedes tentative/weak resolution
                        def.weak.clear();
                    }
                }
                emitSymbolUpdateIfChanged(u, s, def, before);
            }
        }
    }

    private GlobalDef snapshot(GlobalDef def) {
        GlobalDef copy = new GlobalDef(def.name);
        copy.strong.addAll(def.strong);
        copy.weak.addAll(def.weak);
        copy.common.addAll(def.common);
        return copy;
    }

    private boolean contains(List<Provider> list, Provider p) {
        return list.stream().anyMatch(q -> q.identity().equals(p.identity()));
    }

    private void emitSymbolUpdateIfChanged(LoadedUnit u, Symbol s, GlobalDef def,
                                           GlobalDef before) {
        boolean changed = def.strong.size() != before.strong.size()
                || def.weak.size() != before.weak.size()
                || def.common.size() != before.common.size();
        if (!changed) {
            return;
        }
        String state;
        if (!def.strong.isEmpty()) {
            state = "strong <- " + def.strong.get(0).unitLabel;
        } else if (!def.weak.isEmpty()) {
            state = "weak <- " + def.weak.get(0).unitLabel;
        } else {
            state = "common <- " + def.common.get(0).unitLabel;
        }
        event(PlayEventKinds.SYMBOL_UPDATE,
                "符号表更新 '" + s.name + "'",
                "处理 " + u.label + " 后状态为 " + state)
                .put("symbol", s.name)
                .put("unit", u.id)
                .put("binding", s.binding.name())
                .put("state", state)
                .put("strongCount", def.strong.size())
                .put("commonCount", def.common.size());
    }

    private String sectionName(LoadedUnit u, Symbol s) {
        if (s.isUndefined()) {
            return "UND";
        }
        if (s.isCommon()) {
            return "COMMON";
        }
        if (s.isAbsolute()) {
            return "ABS";
        }
        if (s.sectionIndex >= 0 && s.sectionIndex < u.elf.sections.size()) {
            return u.elf.sections.get((int) s.sectionIndex).name;
        }
        return "?";
    }
