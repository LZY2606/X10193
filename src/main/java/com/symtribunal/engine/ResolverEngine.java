package com.symtribunal.engine;

import com.symtribunal.elf.ArArchive;
import com.symtribunal.elf.Elf;
import com.symtribunal.elf.ElfGroup;
import com.symtribunal.elf.ElfImage;
import com.symtribunal.elf.ElfParseException;
import com.symtribunal.elf.ElfParser;
import com.symtribunal.elf.ElfRelocation;
import com.symtribunal.elf.ElfSection;
import com.symtribunal.elf.ElfSymbol;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The symbol tribunal core. Performs lazy archive extraction, COMDAT selection, version-aware
 * candidate filtering and weak/strong/common resolution without invoking a system linker.
 * All results are deterministic: explicit load order decides inter-DSO ties, never file order.
 */
@Component
public class ResolverEngine {

    private final ElfParser parser;

    public ResolverEngine(ElfParser parser) {
        this.parser = parser;
    }

    public static class Binary {
        public String id;
        public String fileName;
        public byte[] data;
    }

    public ResolutionReport resolve(String schemeId, String schemeName, LinkPolicy policy,
                                    String machine, List<Binary> inputs) {
        ResolutionReport report = new ResolutionReport();
        report.schemeId = schemeId;
        report.schemeName = schemeName;
        report.policy = policy.name();
        report.machine = machine;
        report.createdAt = Instant.now().toString();

        Map<String, Unit> unitsByKey = new LinkedHashMap<>();
        List<Unit> explicitUnits = new ArrayList<>();
        List<ArchiveEntry> archives = new ArrayList<>();

        for (Binary binary : inputs) {
            byte[] probe = binary.data;
            if (probe != null && probe.length >= 8 && probe[0] == '!' && probe[1] == '<') {
                ArchiveEntry archive = loadArchive(binary, report);
                if (archive != null) {
                    archives.add(archive);
                }
                continue;
            }
            Unit unit = loadUnit(binary.id, binary.fileName, null, binary.data,
                    explicitUnits.size(), unitsByKey, report);
            if (unit != null) {
                unit.active = true;
                explicitUnits.add(unit);
            }
        }

        // Explicitly listed archive members (archives present in the load order) are searched
        // lazily; the archive itself establishes the position used when a member is extracted.
        int archivePosition = explicitUnits.size();
        for (ArchiveEntry archive : archives) {
            archive.searchPosition = archivePosition++;
        }

        List<StepEvent> steps = new ArrayList<>();
        int stepCounter = 0;
        steps.add(loadStep(++stepCounter, explicitUnits, archives));

        // Phase 1: COMDAT selection across explicit objects (before extraction, by load order).
        selectComdats(explicitUnits, steps, stepCounter);
        stepCounter = steps.size();

        // Phase 2: fixed-point lazy archive extraction driven by still-undefined references.
        List<Unit> extracted = new ArrayList<>();
        extractFromArchives(explicitUnits, extracted, archives, policy, steps, stepCounter, report);
        stepCounter = steps.size();

        // Re-run COMDAT selection now that extracted members participate.
        selectComdats(concat(explicitUnits, extracted), steps, stepCounter);
        stepCounter = steps.size();

        List<Unit> allUnits = concat(explicitUnits, extracted);
        assignAddresses(allUnits);

        // Phase 3: build references from undefined relocations and resolve every one of them.
        resolveReferences(allUnits, archives, policy, machine, steps, stepCounter, report);
        buildViews(allUnits, archives, report);
        report.steps = steps;
        if (report.references.stream().anyMatch(r -> "AMBIGUOUS".equals(r.status))
                && report.warnings.stream().noneMatch(w -> w.contains("equal candidates"))) {
            report.warnings.add("equal candidates: ambiguous decisions were retained, "
                    + "not broken by file system order");
        }
        return report;
    }

    private static class ArchiveEntry {
        Binary binary;
        ArArchive archive;
        int searchPosition;
        final Map<Integer, String> memberKeyByOffset = new LinkedHashMap<>();
        final List<Unit> members = new ArrayList<>();
        boolean attempted;
    }

    private ArchiveEntry loadArchive(Binary binary, ResolutionReport report) {
        try {
            ArArchive archive = ArArchive.read(binary.data, binary.fileName);
            ArchiveEntry entry = new ArchiveEntry();
            entry.binary = binary;
            entry.archive = archive;
            for (ArArchive.Member member : archive.members) {
                String key = binary.id + "::" + sanitize(member.name);
                entry.memberKeyByOffset.put(member.index, key);
            }
            return entry;
        } catch (ElfParseException e) {
            report.isolatedErrors.add(binary.fileName + ": " + e.getMessage());
            return null;
        }
    }

    private Unit loadUnit(String binaryId, String fileName, String memberName, byte[] data,
                          int order, Map<String, Unit> unitsByKey, ResolutionReport report) {
        String label = memberName == null ? fileName : fileName + "(" + memberName + ")";
        try {
            ElfImage image = parser.parse(data, label);
            String key = memberName == null ? binaryId : binaryId + "::" + sanitize(memberName);
            if (unitsByKey.containsKey(key)) {
                key = key + "#" + order;
            }
            Unit unit = new Unit(key, label, binaryId, fileName, memberName, image, order);
            unitsByKey.put(key, unit);
            return unit;
        } catch (ElfParseException e) {
            report.isolatedErrors.add(label + ": " + e.getMessage());
            return null;
        }
    }

    private void selectComdats(List<Unit> units, List<StepEvent> steps, int stepCounter) {
        Map<String, List<ElfGroup>> bySignature = new LinkedHashMap<>();
        for (Unit unit : units) {
            if (!unit.active || unit.kind != Elf.ET_REL) {
                continue;
            }
            for (ElfGroup group : unit.image.groups) {
                if (!group.comdat) {
                    continue;
                }
                bySignature.computeIfAbsent(group.signature, k -> new ArrayList<>()).add(group);
            }
        }
        int local = stepCounter;
        for (Map.Entry<String, List<ElfGroup>> entry : bySignature.entrySet()) {
            List<ElfGroup> groups = entry.getValue();
            Unit winnerUnit = null;
            ElfGroup winner = null;
            for (ElfGroup group : groups) {
                Unit owner = ownerOf(units, group.sectionIndex);
                if (owner == null || !owner.active) {
                    continue;
                }
                if (winnerUnit == null || owner.explicitOrder < winnerUnit.explicitOrder
                        || (owner.explicitOrder == winnerUnit.explicitOrder
                            && owner.key.compareTo(winnerUnit.key) < 0)) {
                    winnerUnit = owner;
                    winner = group;
                }
            }
            if (winner == null) {
                continue;
            }
            winnerUnit.keptComdats.put(entry.getKey(), winner.sectionIndex);
            List<String> loserLabels = new ArrayList<>();
            for (ElfGroup group : groups) {
                if (group == winner) {
                    continue;
                }
                Unit owner = ownerOf(units, group.sectionIndex);
                if (owner == null) {
                    continue;
                }
                for (int memberSection : group.memberSections) {
                    owner.discardedSections.add(memberSection);
                }
                loserLabels.add(owner.label);
            }
            if (!loserLabels.isEmpty()) {
                StepEvent event = new StepEvent();
                event.step = ++local;
                event.kind = "COMDAT";
                event.title = "COMDAT 去重：" + entry.getKey();
                event.detail = "保留 " + winnerUnit.label + "；淘汰 " + String.join("、", loserLabels)
                        + "（按显式加载顺序裁决，相同名称不同候选）";
                event.unitKey = winnerUnit.key;
                event.symbol = entry.getKey();
                event.activeUnits = activeLabels(units);
                event.symbolTable = comdatTable(winnerUnit, winner);
                steps.add(event);
            }
        }
    }

    private Unit ownerOf(List<Unit> units, int groupSectionIndex) {
        for (Unit unit : units) {
            if (unit.image.groups.stream().anyMatch(g -> g.sectionIndex == groupSectionIndex)) {
                return unit;
            }
        }
        return null;
    }

    private void extractFromArchives(List<Unit> explicitUnits, List<Unit> extracted,
                                     List<ArchiveEntry> archives, LinkPolicy policy,
                                     List<StepEvent> steps, int stepCounter,
                                     ResolutionReport report) {
        int local = stepCounter;
        java.util.Set<String> extractedKeys = new java.util.HashSet<>();
        boolean changed = true;
        int guard = 0;
        while (changed) {
            if (guard++ > 4096) {
                report.warnings.add("archive extraction fixed-point guard reached");
                break;
            }
            changed = false;
            List<Unit> live = concat(explicitUnits, extracted);
            java.util.Set<String> undefined = undefinedNames(live);
            for (ArchiveEntry archive : archives) {
                List<ArArchive.Member> candidates = new ArrayList<>();
                for (Map.Entry<String, List<Integer>> indexEntry
                        : archive.archive.symbolIndex.entrySet()) {
                    if (!undefined.contains(indexEntry.getKey())) {
                        continue;
                    }
                    for (Integer memberIndex : indexEntry.getValue()) {
                        ArArchive.Member member = memberByIndex(archive.archive, memberIndex);
                        if (member != null && extractedKeys.add(
                                archive.binary.id + "/" + member.name)) {
                            candidates.add(member);
                        }
                    }
                }
                // Deterministic member processing order: archive listing order, de-duplicated.
                Map<String, ArArchive.Member> unique = new LinkedHashMap<>();
                for (ArArchive.Member member : candidates) {
                    unique.putIfAbsent(member.name, member);
                }
                for (ArArchive.Member member : unique.values()) {
                    int order = archive.searchPosition * 100000 + member.index;
                    Unit unit = loadUnit(archive.binary.id, archive.binary.fileName, member.name,
                            member.data, order, new LinkedHashMap<>(), report);
                    if (unit == null) {
                        continue;
                    }
                    unit.fromArchive = true;
                    unit.lazyExtracted = true;
                    unit.active = true;
                    extracted.add(unit);
                    changed = true;
                    StepEvent event = new StepEvent();
                    event.step = ++local;
                    event.kind = "ARCHIVE_EXTRACT";
                    event.title = "惰性抽取：" + archive.binary.fileName + "(" + member.name + ")";
                    event.detail = "未满足引用触发 archive member 抽取；循环依赖通过固定点再扫描处理";
                    event.unitKey = unit.key;
                    event.activeUnits = activeLabels(concat(explicitUnits, extracted));
                    event.symbolTable = definedNames(unit);
                    steps.add(event);
                }
            }
        }
    }

    private ArArchive.Member memberByIndex(ArArchive archive, int storedIndex) {
        // GNU indices store the member header offset; map by closest header offset, else by order.
        if (storedIndex < 0) {
            return null;
        }
        if (storedIndex < archive.members.size()) {
            return archive.members.get(storedIndex);
        }
        return null;
    }

    private void assignAddresses(List<Unit> units) {
        long cursor = 0x400000L;
        for (Unit unit : units) {
            unit.baseAddress = cursor;
            long span = 0x100000L;
            for (ElfSection section : unit.image.sections) {
                if (section.allocated() && section.type != Elf.SHT_NOBITS) {
                    span = Math.max(span, section.address + Math.max(section.size, 1));
                }
            }
            cursor += ((span + 0xfff) & ~0xfffL);
        }
    }

    private java.util.Set<String> undefinedNames(List<Unit> units) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Unit unit : units) {
            if (!unit.active) {
                continue;
            }
            for (ElfSymbol symbol : unit.image.symbols) {
                if (symbol.name.isEmpty()) {
                    continue;
                }
                if (symbol.tableKind.equals("dynsym") && unit.kind == Elf.ET_DYN) {
                    continue;
                }
                boolean undefined = symbol.sectionIndex == Elf.SHN_UNDEF
                        || symbol.sectionIndex == 0;
                if (undefined && symbol.binding != Elf.STB_LOCAL) {
                    names.add(symbol.name);
                }
            }
        }
        return names;
    }

    private void resolveReferences(List<Unit> allUnits, List<ArchiveEntry> archives,
                                   LinkPolicy policy, String machine,
                                   List<StepEvent> steps, int stepCounter,
                                   ResolutionReport report) {
        int local = stepCounter;
        Map<String, Reference> references = new LinkedHashMap<>();
        for (Unit unit : allUnits) {
            collectReferences(unit, references);
        }
        for (Reference reference : references.values()) {
            List<Provider> providers = collectProviders(allUnits, reference);
            List<Candidate> views = new ArrayList<>();
            List<Provider> eligible = new ArrayList<>();
            for (Provider provider : providers) {
                Candidate view = toCandidate(provider);
                String rejection = rejectionReason(reference, provider);
                view.eligible = rejection == null;
                view.elimination = rejection;
                views.add(view);
                if (rejection == null) {
                    eligible.add(provider);
                }
            }
            if (eligible.isEmpty()) {
                reference.status = reference.weakReference ? "WEAK_UNRESOLVED" : "UNRESOLVED";
                reference.note = reference.weakReference
                        ? "weak 未定义引用：允许为 0/惰性，不报错"
                        : "未满足引用：没有候选提供者";
            } else {
                choose(reference, eligible, views, policy, machine);
            }
            reference.candidates = views;
            report.references.add(reference);
            StepEvent event = new StepEvent();
            event.step = ++local;
            event.kind = "SYMBOL_DECISION";
            event.title = "裁决：" + reference.unitLabel + " :: " + reference.name;
            event.detail = reference.status
                    + (reference.winnerUnitKey == null ? "" : " -> " + reference.winnerUnitKey)
                    + (reference.note == null ? "" : "（" + reference.note + "）");
            event.unitKey = reference.unitKey;
            event.symbol = reference.name;
            event.activeUnits = activeLabels(allUnits);
            event.symbolTable = providerTable(eligible, reference);
            steps.add(event);
        }
    }

    private void collectReferences(Unit unit, Map<String, Reference> references) {
        Map<String, ElfSymbol> undefinedSymbols = new LinkedHashMap<>();
        for (ElfSymbol symbol : unit.image.symbols) {
            if (symbol.name.isEmpty() || symbol.binding == Elf.STB_LOCAL) {
                continue;
            }
            boolean undefined = symbol.sectionIndex == Elf.SHN_UNDEF
                    || symbol.sectionIndex == 0;
            if (undefined) {
                undefinedSymbols.putIfAbsent(symbol.name, symbol);
            }
        }
        Map<String, List<ElfRelocation>> byName = new LinkedHashMap<>();
        boolean[] copyByName = new boolean[0];
        java.util.Set<String> copyNames = new java.util.HashSet<>();
        java.util.Set<String> weakNames = new java.util.HashSet<>();
        for (ElfRelocation reloc : unit.image.relocations) {
            if (reloc.symbolName.isEmpty()) {
                continue;
            }
            ElfSymbol symbol = undefinedSymbols.get(reloc.symbolName);
            if (symbol == null) {
                continue;
            }
            byName.computeIfAbsent(reloc.symbolName, k -> new ArrayList<>()).add(reloc);
            if (Elf.isCopyReloc(unit.image.machine, reloc.type)) {
                copyNames.add(reloc.symbolName);
            }
            if (symbol.binding == Elf.STB_WEAK) {
                weakNames.add(reloc.symbolName);
            }
        }
        // A dynsym undefined entry with no relocation (e.g. pure DSO interposition use) still
        // represents a reference for executable/shared objects.
        if (unit.kind == Elf.ET_EXEC || unit.kind == Elf.ET_DYN) {
            for (ElfSymbol symbol : undefinedSymbols.values()) {
                if (symbol.tableKind.equals("dynsym") && !byName.containsKey(symbol.name)) {
                    byName.put(symbol.name, new ArrayList<>());
                    if (symbol.binding == Elf.STB_WEAK) {
                        weakNames.add(symbol.name);
                    }
                }
            }
        }
        int ordinal = 0;
        for (Map.Entry<String, List<ElfRelocation>> entry : byName.entrySet()) {
            ElfSymbol symbol = undefinedSymbols.get(entry.getKey());
            Reference reference = new Reference();
            reference.key = unit.key + "#" + symbol.name
                    + (symbol.version == null ? "" : "@" + symbol.version) + "#" + ordinal++;
            reference.unitKey = unit.key;
            reference.unitLabel = unit.label;
            reference.name = symbol.name;
            reference.requiredVersion = symbol.version;
            reference.weakReference = weakNames.contains(symbol.name);
            reference.copyReference = copyNames.contains(symbol.name);
            for (ElfRelocation reloc : entry.getValue()) {
                RelocView view = new RelocView();
                ElfSection section = unit.section(reloc.sectionIndex);
                view.section = section == null ? "#" + reloc.sectionIndex : section.name;
                view.offset = reloc.offset;
                view.type = reloc.type;
                view.typeName = relocTypeName(unit.image.machine, reloc.type);
                view.addend = reloc.addend;
                view.symbol = reloc.symbolName;
                view.symbolTable = reloc.symbolTable;
                reference.relocations.add(view);
            }
            references.put(reference.key, reference);
        }
    }

    private List<Provider> collectProviders(List<Unit> units, Reference reference) {
        List<Provider> providers = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Unit unit : units) {
            if (!unit.active || unit.key.equals(reference.unitKey)) {
                continue;
            }
            for (ElfSymbol symbol : unit.image.symbols) {
                if (!symbol.name.equals(reference.name)) {
                    continue;
                }
                if (symbol.binding == Elf.STB_LOCAL) {
                    continue;
                }
                if (symbol.sectionIndex == Elf.SHN_UNDEF || symbol.sectionIndex == 0) {
                    continue;
                }
                if (!unit.symbolSectionLive(symbol)) {
                    continue;
                }
                // Only the dynamic symbol table exports symbols from shared objects.
                if (unit.kind == Elf.ET_DYN && !"dynsym".equals(symbol.tableKind)) {
                    continue;
                }
                Provider candidate = new Provider(unit, symbol, unit.explicitOrder);
                String dedupe = candidate.providerKey() + "#"
                        + Elf.visibilityName(symbol.visibility) + "#" + symbol.binding;
                if (seen.add(dedupe)) {
                    providers.add(candidate);
                }
            }
        }
        providers.sort(Comparator.comparingInt((Provider p) -> p.unit.explicitOrder)
                .thenComparing(p -> p.unit.key)
                .thenComparing(p -> p.symbol.index));
        return providers;
    }

    private String rejectionReason(Reference reference, Provider provider) {
        ElfSymbol symbol = provider.symbol;
        if (symbol.visibility == Elf.STV_HIDDEN || symbol.visibility == Elf.STV_INTERNAL) {
            return "visibility=" + Elf.visibilityName(symbol.visibility) + "：不参与跨对象解析";
        }
        if (reference.requiredVersion != null) {
            if (provider.version == null) {
                return "需求版本 " + reference.requiredVersion + "，候选未版本化";
            }
            if (!provider.version.equals(reference.requiredVersion)) {
                return "版本索引还原为 " + provider.version + "，与需求 "
                        + reference.requiredVersion + " 不同";
            }
        } else if (provider.version != null && !provider.defaultVersion) {
            return "候选仅以非默认版本 " + provider.version + " 提供，未版本化引用不可绑定";
        }
        return null;
    }

    private void choose(Reference reference, List<Provider> eligible, List<Candidate> views,
                        LinkPolicy policy, String machine) {
        Comparator<Provider> comparator;
        if (policy == LinkPolicy.LOAD_ORDER) {
            comparator = Comparator.comparingInt((Provider p) -> p.unit.explicitOrder)
                    .thenComparing(p -> p.unit.kind == Elf.ET_DYN ? 1 : 0)
                    .thenComparing(p -> p.unit.key)
                    .thenComparing(p -> p.symbol.index);
        } else {
            comparator = (a, b) -> {
                if (a.sourceClass != b.sourceClass) {
                    return Integer.compare(a.sourceClass, b.sourceClass);
                }
                if (a.sourceClass == 3) {
                    return Integer.compare(a.unit.explicitOrder, b.unit.explicitOrder);
                }
                return 0;
            };
        }
        Provider best = null;
        List<Provider> tied = new ArrayList<>();
        for (Provider provider : eligible) {
            if (best == null) {
                best = provider;
                tied.add(provider);
                continue;
            }
            int cmp = comparator.compare(provider, best);
            if (cmp < 0) {
                best = provider;
                tied.clear();
                tied.add(provider);
            } else if (cmp == 0) {
                tied.add(provider);
            }
        }

        boolean objectTie = policy == LinkPolicy.GNU_STATIC
                ? tied.stream().anyMatch(p -> p.sourceClass != 3)
                : tied.size() > 1 && tied.stream().allMatch(p -> p.unit.kind != Elf.ET_DYN);
        if (objectTie && tied.size() > 1) {
            reference.status = "AMBIGUOUS";
            reference.note = "存在 " + tied.size() + " 个同等等级候选，保留歧义（不以文件次序决胜）";
            markWinners(views, tied, reference);
            return;
        }

        // Common-symbol merge: multiple COMMON of equal rank are merged into the largest one.
        if (best.sourceClass == 2 && tied.size() > 1) {
            Provider largest = tied.stream()
                    .max(Comparator.comparingLong((Provider p) -> p.symbol.size)
                            .thenComparing(p -> p.unit.explicitOrder))
                    .orElse(best);
            best = largest;
        }

        if (reference.copyReference) {
            if (best.symbol.visibility == Elf.STV_PROTECTED) {
                reference.status = "COPY_BLOCKED_PROTECTED";
                reference.note = "copy relocation 不能捕获 protected 符号（不允许跨 DSO 截获）";
                reference.winnerUnitKey = best.providerKey();
                return;
            }
            reference.status = "COPY_SHADOW";
            reference.note = "copy relocation：在可执行文件中建立影子对象定义，源 DSO 运行时仍被截获";
        } else {
            reference.status = "RESOLVED";
        }
        reference.winnerUnitKey = best.unit.key;
        reference.winnerVersion = best.version;
        reference.address = best.unit.baseAddress + best.symbol.value;
        markWinners(views, List.of(best), reference);
    }

    private void markWinners(List<Candidate> views, List<Provider> winners, Reference reference) {
        for (Candidate view : views) {
            for (Provider provider : winners) {
                if (view.unitKey.equals(provider.unit.key)
                        && java.util.Objects.equals(view.version, provider.version)
                        && view.symbol.equals(provider.symbol.name)) {
                    view.winner = true;
                }
            }
        }
    }

    private void buildViews(List<Unit> allUnits, List<ArchiveEntry> archives,
                            ResolutionReport report) {
        Map<String, UnitView> views = new LinkedHashMap<>();
        for (Unit unit : allUnits) {
            UnitView view = new UnitView();
            view.key = unit.key;
            view.label = unit.label;
            view.binaryId = unit.binaryId;
            view.fileName = unit.fileName;
            view.member = unit.member;
            view.kind = Elf.typeOfObject(unit.kind);
            view.machine = Elf.machineName(unit.image.machine);
            view.fromArchive = unit.fromArchive;
            view.lazyExtracted = unit.lazyExtracted;
            view.baseAddress = unit.baseAddress;
            view.needed = new ArrayList<>(unit.image.needed);
            for (ElfSymbol symbol : unit.image.symbols) {
                if (symbol.name.isEmpty() || symbol.binding == Elf.STB_LOCAL) {
                    continue;
                }
                if (symbol.sectionIndex == Elf.SHN_UNDEF || symbol.sectionIndex == 0) {
                    continue;
                }
                if (unit.kind == Elf.ET_DYN && !"dynsym".equals(symbol.tableKind)) {
                    continue;
                }
                if (!unit.symbolSectionLive(symbol)) {
                    continue;
                }
                view.provides.add(symbol.name
                        + (symbol.version == null ? "" : "@" + symbol.version));
            }
            for (Integer sectionIndex : unit.discardedSections) {
                ElfSection section = unit.section(sectionIndex);
                view.discardedComdats.add(section == null ? "#" + sectionIndex : section.name);
            }
            report.units.add(view);
            views.put(unit.key, view);
        }

        // Dependency edges: DT_NEEDED (declared), archive extraction and reference binding.
        Map<String, String> byFile = new LinkedHashMap<>();
        for (Unit unit : allUnits) {
            byFile.put(unit.fileName, unit.key);
        }
        for (Unit unit : allUnits) {
            for (String need : unit.image.needed) {
                ResolutionReport.GraphEdge edge = new ResolutionReport.GraphEdge();
                edge.from = unit.key;
                edge.to = byFile.getOrDefault(need, "missing:" + need);
                edge.kind = "NEEDED";
                edge.label = "DT_NEEDED " + need;
                report.graph.add(edge);
                if (!byFile.containsKey(need)) {
                    report.warnings.add(unit.label + " 需要 " + need + "，但该对象未导入");
                }
            }
        }
        for (Reference reference : report.references) {
            if (reference.winnerUnitKey == null) {
                continue;
            }
            ResolutionReport.GraphEdge edge = new ResolutionReport.GraphEdge();
            edge.from = reference.unitKey;
            edge.to = reference.winnerUnitKey;
            edge.kind = "BINDS";
            edge.label = reference.name
                    + (reference.requiredVersion == null ? "" : "@" + reference.requiredVersion);
            report.graph.add(edge);
        }

        for (Unit unit : allUnits) {
            buildVersionGraph(unit, report);
        }
    }

    private void buildVersionGraph(Unit unit, ResolutionReport report) {
        var byName = new LinkedHashMap<String, VersionNode>();
        for (var def : unit.image.versionDefs) {
            VersionNode node = new VersionNode();
            node.id = unit.key + "|DEF|" + def.name;
            node.unitKey = unit.key;
            node.kind = "DEF";
            node.name = def.name;
            node.label = (def.name.isEmpty() ? "BASE" : def.name)
                    + ((def.flags & Elf.VER_FLG_BASE) != 0 ? " (base)" : "");
            node.defaultVersion = (def.flags & Elf.VERSYM_HIDDEN) == 0;
            node.aliases = new ArrayList<>(def.aliases);
            byName.put(def.name, node);
            report.versionGraph.add(node);
        }
        for (var need : unit.image.versionNeeds) {
            for (var entry : need.entries) {
                VersionNode node = new VersionNode();
                node.id = unit.key + "|NEED|" + entry.name;
                node.unitKey = unit.key;
                node.kind = "NEED";
                node.name = entry.name;
                node.label = entry.name + "@" + need.file + (entry.weak ? " (weak)" : "");
                node.weak = entry.weak;
                report.versionGraph.add(node);
            }
        }
        for (ElfSymbol symbol : unit.image.symbols) {
            if (!"dynsym".equals(symbol.tableKind) || symbol.version == null) {
                continue;
            }
            VersionNode node = byName.get(symbol.version);
            if (node != null) {
                node.symbols.add(symbol.name);
            }
        }
    }

    private Candidate toCandidate(Provider provider) {
        Candidate candidate = new Candidate();
        candidate.unitKey = provider.unit.key;
        candidate.unitLabel = provider.unit.label;
        candidate.binaryId = provider.unit.binaryId;
        candidate.member = provider.unit.member;
        candidate.symbol = provider.symbol.name;
        candidate.version = provider.version;
        candidate.defaultVersion = provider.defaultVersion;
        candidate.binding = Elf.bindingName(provider.symbol.binding);
        candidate.type = Elf.typeName(provider.symbol.type);
        candidate.visibility = Elf.visibilityName(provider.symbol.visibility);
        candidate.source = provider.sourceClass == 0 ? "STRONG_OBJECT"
                : provider.sourceClass == 1 ? "WEAK_OBJECT"
                : provider.sourceClass == 2 ? "COMMON" : "SHARED";
        candidate.rankClass = provider.sourceClass;
        candidate.loadRank = provider.unit.explicitOrder;
        return candidate;
    }

    private List<String> activeLabels(List<Unit> units) {
        List<String> labels = new ArrayList<>();
        for (Unit unit : units) {
            if (unit.active) {
                labels.add(unit.label);
            }
        }
        return labels;
    }

    private List<String> definedNames(Unit unit) {
        List<String> names = new ArrayList<>();
        for (ElfSymbol symbol : unit.image.symbols) {
            if (!symbol.name.isEmpty() && symbol.defined && symbol.binding != Elf.STB_LOCAL) {
                names.add(Elf.bindingName(symbol.binding) + " " + symbol.name
                        + (symbol.version == null ? "" : "@" + symbol.version));
            }
        }
        return names;
    }

    private List<String> comdatTable(Unit unit, ElfGroup group) {
        List<String> names = new ArrayList<>();
        names.add("signature=" + group.signature);
        for (int memberSection : group.memberSections) {
            ElfSection section = unit.section(memberSection);
            names.add("retain " + (section == null ? "#" + memberSection : section.name));
        }
        return names;
    }

    private List<String> providerTable(List<Provider> providers, Reference reference) {
        List<String> names = new ArrayList<>();
        for (Provider provider : providers) {
            names.add(Elf.bindingName(provider.symbol.binding) + " "
                    + provider.symbol.name
                    + (provider.version == null ? "" : "@" + provider.version)
                    + " <- " + provider.unit.label);
        }
        return names;
    }

    private StepEvent loadStep(int step, List<Unit> explicitUnits, List<ArchiveEntry> archives) {
        StepEvent event = new StepEvent();
        event.step = step;
        event.kind = "LOAD";
        event.title = "导入对象与显式加载顺序";
        StringBuilder detail = new StringBuilder();
        for (Unit unit : explicitUnits) {
            detail.append(unit.label).append("  ");
        }
        for (ArchiveEntry archive : archives) {
            detail.append("[").append(archive.binary.fileName)
                    .append(" archive, 惰性]  ");
        }
        event.detail = detail.toString().trim();
        event.activeUnits = activeLabels(explicitUnits);
        return event;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String relocTypeName(int machine, int type) {
        if (machine == Elf.EM_X86_64) {
            return switch (type) {
                case Elf.R_X86_64_COPY -> "R_X86_64_COPY";
                case Elf.R_X86_64_GLOB_DAT -> "R_X86_64_GLOB_DAT";
                case Elf.R_X86_64_JUMP_SLOT -> "R_X86_64_JUMP_SLOT";
                default -> "R_X86_64_" + type;
            };
        }
        if (machine == Elf.EM_AARCH64) {
            return switch (type) {
                case Elf.R_AARCH64_COPY -> "R_AARCH64_COPY";
                case Elf.R_AARCH64_GLOB_DAT -> "R_AARCH64_GLOB_DAT";
                case Elf.R_AARCH64_JUMP_SLOT -> "R_AARCH64_JUMP_SLOT";
                default -> "R_AARCH64_" + type;
            };
        }
        return "R_" + type;
    }

    /** Semantic diff: distinguish provider changes from pure address changes. */
    public ComparisonResult compare(ResolutionReport a, ResolutionReport b) {
        ComparisonResult result = new ComparisonResult();
        result.schemeA = a.schemeName;
        result.schemeB = b.schemeName;
        Map<String, Reference> mapA = new LinkedHashMap<>();
        Map<String, Reference> mapB = new LinkedHashMap<>();
        for (Reference reference : a.references) {
            mapA.put(identity(reference), reference);
        }
        for (Reference reference : b.references) {
            mapB.put(identity(reference), reference);
        }
        for (Map.Entry<String, Reference> entry : mapA.entrySet()) {
            Reference ra = entry.getValue();
            Reference rb = mapB.get(entry.getKey());
            if (rb == null) {
                result.onlyInA.add(change(ra, null));
                continue;
            }
            classify(ra, rb, result);
        }
        for (Map.Entry<String, Reference> entry : mapB.entrySet()) {
            if (!mapA.containsKey(entry.getKey())) {
                result.onlyInB.add(change(null, entry.getValue()));
            }
        }
        return result;
    }

    private String identity(Reference reference) {
        return reference.unitLabel + "#" + reference.name
                + (reference.requiredVersion == null ? "" : "@" + reference.requiredVersion);
    }

    private void classify(Reference a, Reference b, ComparisonResult result) {
        boolean sameProvider = java.util.Objects.equals(a.winnerUnitKey, b.winnerUnitKey)
                && java.util.Objects.equals(a.winnerVersion, b.winnerVersion);
        if (!sameProvider || !java.util.Objects.equals(a.status, b.status)) {
            ComparisonResult.Change change = change(a, b);
            if (java.util.Objects.equals(a.winnerUnitKey, b.winnerUnitKey)
                    && !java.util.Objects.equals(a.status, b.status)) {
                change.note = "提供者相同但裁决状态变化";
                result.resolvedDifferently.add(change);
            } else {
                change.note = "引用换了提供者";
                result.providerChanged.add(change);
            }
            return;
        }
        if (!java.util.Objects.equals(a.address, b.address)) {
            ComparisonResult.Change change = change(a, b);
            change.note = "提供者不变，仅地址变化";
            result.addressOnly.add(change);
        }
    }

    private ComparisonResult.Change change(Reference a, Reference b) {
        ComparisonResult.Change change = new ComparisonResult.Change();
        Reference any = a != null ? a : b;
        change.key = any.key;
        change.unit = any.unitLabel;
        change.symbol = any.name;
        change.requiredVersion = any.requiredVersion;
        if (a != null) {
            change.fromProvider = a.winnerUnitKey;
            change.fromAddress = a.address;
            change.fromStatus = a.status;
        }
        if (b != null) {
            change.toProvider = b.winnerUnitKey;
            change.toAddress = b.address;
            change.toStatus = b.status;
        }
        return change;
    }
}
