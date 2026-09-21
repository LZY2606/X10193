package court.engine;

import court.elf.ComdatGroup;
import court.elf.ElfObject;
import court.elf.ElfReloc;
import court.elf.ElfSymbol;
import court.model.*;

import java.util.*;

/**
 * 核心裁决引擎（不调用系统 linker）。同一份输入 + 同一策略必然产生同一结果：
 * 所有平局裁决只使用显式加载顺序 ordinal，禁止文件系统遍历次序。
 */
public final class LinkEngine {

    private final Policy policy;
    private final Decision decision;
    private final List<InputEntry> inputs;

    // 活跃单元（显式对象 + 展开的共享依赖 + 抽取的 archive 成员），按 ordinal 排列
    private final LinkedHashMap<String, LoadUnit> units = new LinkedHashMap<>();
    // archive 的全部成员（含未抽取），unitId -> unit
    private final LinkedHashMap<String, LoadUnit> archiveMembers = new LinkedHashMap<>();
    // archive 上传 id -> 成员 unitId 列表（保持归档内顺序）
    private final LinkedHashMap<String, List<String>> archiveMembersByUpload = new LinkedHashMap<>();
    private int ordinalCounter;
    private int stepCounter;

    private LinkEngine(Policy policy, String schemeName, List<InputEntry> inputs) {
        this.policy = policy;
        this.inputs = inputs;
        this.decision = new Decision();
        this.decision.schemeName = schemeName;
        this.decision.policy = policy.name();
    }

    public static Decision run(String schemeId, String schemeName, Policy policy,
                               List<InputEntry> inputs, List<BadObject> badObjects) {
        LinkEngine engine = new LinkEngine(policy, schemeName, inputs);
        engine.decision.schemeId = schemeId;
        engine.decision.badObjects.addAll(badObjects);
        engine.execute();
        return engine.decision;
    }

    private void execute() {
        loadExplicitUnits();
        expandDependencies();
        applyComdatDedup();
        if (policy == Policy.STATIC_LD) {
            scanArchivesLazily();
        }
        collectAndResolve();
    }

    // ---------------------------------------------------------------- 装载

    private LoadUnit addUnit(String unitId, String name, ElfObject elf,
                             boolean fromArchive, String archiveId) {
        LoadUnit unit = new LoadUnit(unitId, name, elf, ordinalCounter++, fromArchive, archiveId);
        units.put(unitId, unit);
        decision.loadOrder.add(unit);
        return unit;
    }

    private void loadExplicitUnits() {
        List<InputEntry> ordered = new ArrayList<>(inputs);
        ordered.sort(Comparator.comparingInt(e -> e.order));
        for (InputEntry e : ordered) {
            if (e.isArchive()) {
                List<String> memberIds = new ArrayList<>();
                for (InputEntry.ArchiveMemberInput m : e.members) {
                    String unitId = e.uploadId + "::" + m.name;
                    LoadUnit unit = new LoadUnit(unitId, e.name + "(" + m.name + ")",
                            m.elf, -1, true, e.uploadId);
                    if (m.elf == null) {
                        unit.active = false;
                        unit.inactiveReason = "成员解析失败: " + m.parseError;
                        decision.badObjects.add(new BadObject(unitId, unit.name, m.parseError));
                    } else if (policy == Policy.STATIC_LD) {
                        unit.ordinal = ordinalCounter++;
                        unit.active = false;
                        unit.inactiveReason = "archive 成员惰性未抽取";
                        decision.loadOrder.add(unit);
                    } else {
                        unit.ordinal = ordinalCounter++;
                        unit.active = true;
                        decision.loadOrder.add(unit);
                    }
                    archiveMembers.put(unitId, unit);
                    memberIds.add(unitId);
                    if (policy != Policy.STATIC_LD && m.elf != null) {
                        units.put(unitId, unit);
                    }
                }
                archiveMembersByUpload.put(e.uploadId, memberIds);
                step("LOAD", "登记 archive " + e.name,
                        policy == Policy.STATIC_LD
                                ? "归档成员保持惰性，待出现未满足引用时按需抽取"
                                : "动态策略下 archive 不参与惰性抽取；成员登记为可抽取候选",
                        e.uploadId, null);
            } else if (e.elf != null) {
                LoadUnit unit = addUnit(e.uploadId, e.name, e.elf, false, null);
                step("LOAD", "载入 " + e.name,
                        (e.elf.isSharedObject() ? "共享对象" : "可重定位对象")
                                + "，machine=" + e.elf.machineName,
                        unit.unitId, null);
            }
        }
    }

    // ---------------------------------------------------------------- 依赖展开

    private void expandDependencies() {
        // 以显式顺序做 BFS：依赖在其“父”之后进入作用域（ld 的 --as-needed/标准顺序近似）。
        // 共享库之间按名字匹配 DT_NEEDED：先比 soname，再比文件基名。绝不目录扫描。
        Deque<LoadUnit> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (LoadUnit u : new ArrayList<>(units.values())) {
            if (!u.elf.isSharedObject()) {
                continue;
            }
            visited.add(u.unitId);
            queue.add(u);
        }
        while (!queue.isEmpty()) {
            LoadUnit cur = queue.poll();
            for (String needed : cur.elf.needed) {
                LoadUnit found = findUnitByLibraryName(needed, visited);
                if (found == null) {
                    decision.dependencies.add(new DependencyEdge(cur.unitId, needed, null, false));
                    decision.warnings.add("未找到 DT_NEEDED 依赖: " + cur.name + " -> " + needed);
                    step("DEPENDENCY", "未满足依赖 " + needed,
                            cur.name + " 声明需要 " + needed + "，导入集合中没有该名字的共享对象",
                            cur.unitId, null);
                    continue;
                }
                decision.dependencies.add(new DependencyEdge(cur.unitId, needed, found.unitId, true));
                if (visited.add(found.unitId)) {
                    queue.add(found);
                    step("DEPENDENCY", "展开依赖 " + found.name,
                            cur.name + " -> " + needed + " 命中 " + found.name
                                    + "，按 BFS 追加到加载顺序尾部",
                            found.unitId, null);
                }
            }
        }
    }

    private LoadUnit findUnitByLibraryName(String needed, Set<String> visitedAlready) {
        LoadUnit bySoname = null;
        LoadUnit byBase = null;
        String base = baseName(needed);
        for (InputEntry e : inputs) {
            ElfObject elf = e.elf;
            String unitId = e.uploadId;
            if (elf == null || !elf.isSharedObject()) {
                continue;
            }
            if (needed.equals(elf.soname)) {
                bySoname = units.get(unitId);
                break;
            }
            if (byBase == null && baseName(e.name).equals(base)) {
                byBase = units.get(unitId);
            }
        }
        return bySoname != null ? bySoname : byBase;
    }

    private static String baseName(String path) {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    // ---------------------------------------------------------------- COMDAT 去重

    private void applyComdatDedup() {
        // COMDAT 选择是独立的去重过程，不能被普通 strong/weak 覆盖规则吞掉：
        // 同一签名只保留加载顺序中的第一组，后续组的成员 section 整体丢弃。
        Map<String, ComdatDecision> decisions = new LinkedHashMap<>();
        List<LoadUnit> ordered = activeInOrder();
        for (LoadUnit unit : ordered) {
            for (ComdatGroup g : unit.elf.groups) {
                if (!g.comdat || g.signature == null || g.signature.isEmpty()) {
                    continue;
                }
                ComdatDecision dec = decisions.computeIfAbsent(g.signature, ComdatDecision::new);
                boolean first = dec.instances.isEmpty();
                if (first) {
                    g.kept = true;
                    dec.instances.add(new ComdatDecision.Instance(unit.unitId, unit.name,
                            groupSectionIndex(unit, g), true, "加载顺序中的第一组，保留"));
                    step("COMDAT", "保留 COMDAT 组 " + g.signature,
                            unit.name + " 提供签名 " + g.signature + " 的第一份实例",
                            unit.unitId, null);
                } else {
                    g.kept = false;
                    g.discardReason = "COMDAT 签名 " + g.signature + " 已由更早加载的对象提供";
                    dec.instances.add(new ComdatDecision.Instance(unit.unitId, unit.name,
                            groupSectionIndex(unit, g), false,
                            "重复 COMDAT：按加载顺序丢弃，不进入符号覆盖"));
                    step("COMDAT", "丢弃重复 COMDAT 组 " + g.signature,
                            unit.name + " 的组被去重丢弃（" + g.discardReason + "）",
                            unit.unitId, null);
                }
            }
        }
        decision.comdat.addAll(decisions.values());
    }

    private int groupSectionIndex(LoadUnit unit, ComdatGroup group) {
        for (court.elf.ElfSection sec : unit.elf.sections) {
            if (sec.type == court.elf.ElfSection.SHT_GROUP && sec.info == group.ownerSymbolIndex) {
                // 简化：用组内第一个成员 section 代表
                return group.memberSections.isEmpty() ? -1 : group.memberSections.get(0);
            }
        }
        return group.memberSections.isEmpty() ? -1 : group.memberSections.get(0);
    }

    private List<LoadUnit> activeInOrder() {
        List<LoadUnit> list = new ArrayList<>();
        for (LoadUnit u : units.values()) {
            if (u.active && u.elf != null) {
                list.add(u);
            }
        }
        list.sort(Comparator.comparingInt(u -> u.ordinal));
        return list;
    }

    // ------------------------------------------------ archive 惰性抽取（含循环）

    /** 引用视图：引用方单元 + 未定义符号。 */
    private record UndefRef(LoadUnit unit, ElfSymbol sym, String versionName) {
    }

    private List<UndefRef> currentUndefRefs() {
        List<UndefRef> refs = new ArrayList<>();
        for (LoadUnit unit : activeInOrder()) {
            for (ElfSymbol sym : linkSymbolsOf(unit)) {
                if (sym.isLocal() || !sym.isUndef()) {
                    continue;
                }
                refs.add(new UndefRef(unit, sym, sym.version != null ? sym.version.name : null));
            }
        }
        return refs;
    }

    private boolean isSatisfiedByActive(UndefRef ref) {
        for (LoadUnit unit : activeInOrder()) {
            for (ElfSymbol sym : linkSymbolsOf(unit)) {
                if (!sym.name.equals(ref.sym().name)) {
                    continue;
                }
                ProviderEligibility elig = evaluateProvider(unit, sym, ref, ref.unit());
                if (elig == ProviderEligibility.ELIGIBLE_DEFINITION) {
                    return true;
                }
            }
        }
        return false;
    }

    private void scanArchivesLazily() {
        // 定点迭代：反复按归档顺序扫描，直到没有新抽取。这样 archive 循环依赖（a.o 引 b、
        // b.o 引 a 同处一个或相互依赖的归档）也能在多轮扫描中收敛，而不是按目录顺序猜测。
        boolean progress = true;
        int guard = 0;
        int maxRounds = archiveMembersByUpload.values().stream().mapToInt(List::size).sum() + 2;
        while (progress && guard <= maxRounds) {
            progress = false;
            guard++;
            for (Map.Entry<String, List<String>> archEntry : archiveMembersByUpload.entrySet()) {
                for (String memberId : archEntry.getValue()) {
                    LoadUnit member = archiveMembers.get(memberId);
                    if (member.extracted || member.elf == null) {
                        continue;
                    }
                    // 每次成员判定都基于*最新*未满足集合：抽取会立即引入新的未定义引用
                    List<UndefRef> undefs = unsatisfiedUndefs();
                    if (undefs.isEmpty()) {
                        break;
                    }
                    List<UndefRef> triggers = new ArrayList<>();
                    List<String> providedNames = new ArrayList<>();
                    for (UndefRef ref : undefs) {
                        for (ElfSymbol sym : linkSymbolsOf(member)) {
                            if (!sym.name.equals(ref.sym().name)) {
                                continue;
                            }
                            ProviderEligibility elig = evaluateProvider(member, sym, ref, ref.unit());
                            if (elig == ProviderEligibility.ELIGIBLE_DEFINITION) {
                                triggers.add(ref);
                                if (!providedNames.contains(sym.name)) {
                                    providedNames.add(sym.name);
                                }
                            }
                        }
                    }
                    if (!triggers.isEmpty()) {
                        extractMember(member, triggers, providedNames);
                        progress = true;
                    } else if (System.getProperty("court.dbg") != null) {
                        System.err.println("[dbg] " + member.name + " undefs="
                            + undefs.stream().map(u -> u.unit().name + ":" + u.sym().name + "@" + u.versionName()).toList()
                            + " activeUnits=" + activeInOrder().stream().map(x -> x.name + "#" + x.ordinal).toList());
                        for (var sym : linkSymbolsOf(member)) {
                            for (var ref : undefs) {
                                var e = evaluateProvider(member, sym, ref, ref.unit());
                                if (sym.name.equals(ref.sym().name))
                                    System.err.println("    match " + sym.name + " -> " + e);
                            }
                        }
                    }
                }
            }
        }
        if (guard > maxRounds) {
            decision.warnings.add("archive 抽取迭代达到上限（可能存在异常循环）");
        }
        // 记录最终未抽取成员
        for (LoadUnit member : archiveMembers.values()) {
            if (!member.extracted && member.elf != null) {
                step("ARCHIVE_SCAN", "未抽取 " + member.name,
                        "没有任何当前未满足引用需要该成员定义，保持归档外",
                        member.archiveId, null);
            }
        }
    }

    private List<UndefRef> unsatisfiedUndefs() {
        List<UndefRef> out = new ArrayList<>();
        for (UndefRef ref : currentUndefRefs()) {
            if (ref.sym().isWeak()) continue;
            if (!isSatisfiedByActive(ref)) out.add(ref);
        }
        return out;
    }

    private void extractMember(LoadUnit member, List<UndefRef> triggers, List<String> names) {
        member.extracted = true;
        member.active = true;
        member.inactiveReason = null;
        units.put(member.unitId, member);
        StringJoiner sj = new StringJoiner(", ");
        Set<String> seen = new LinkedHashSet<>();
        for (UndefRef t : triggers) {
            String label = t.unit().name + " 需要 " + t.sym().name
                    + (t.versionName() == null ? "" : "@" + t.versionName());
            if (seen.add(label)) {
                sj.add(label);
            }
        }
        step("ARCHIVE_SCAN", "抽取 " + member.name,
                "由未满足引用触发惰性抽取：" + sj + "；新增定义 " + String.join(", ", names),
                member.archiveId, String.join(",", names), symbolTableSnapshot());
        // COMDAT：新抽取的成员也可能带来重复组
        for (ComdatGroup g : member.elf.groups) {
            if (!g.comdat || g.signature == null || g.signature.isEmpty()) {
                continue;
            }
            for (LoadUnit existing : activeInOrder()) {
                if (existing == member) {
                    continue;
                }
                for (ComdatGroup other : existing.elf.groups) {
                    if (other.kept && g.signature.equals(other.signature)) {
                        g.kept = false;
                        g.discardReason = "COMDAT 签名 " + g.signature + " 已由更早抽取的成员提供";
                        step("COMDAT", "抽取成员丢弃重复 COMDAT " + g.signature,
                                member.name + " 被抽取但其 COMDAT 组重复，成员 section 丢弃",
                                member.unitId, null);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 符号视图

    /**
     * 裁决使用的符号：ET_REL 用 .symtab，ET_DYN 优先 .dynsym。
     * dynsym 里既包含定义也包含未定义，全部参与（未定义自然不算提供者）。
     */
    private List<ElfSymbol> linkSymbolsOf(LoadUnit unit) {
        ElfObject elf = unit.elf;
        if (elf == null) {
            return List.of();
        }
        if (elf.isSharedObject()) {
            List<ElfSymbol> dyn = new ArrayList<>();
            boolean hasDyn = false;
            for (ElfSymbol s : elf.symbols) {
                if (s.dynamic) {
                    dyn.add(s);
                    hasDyn = true;
                }
            }
            return hasDyn ? dyn : elf.symbols;
        }
        List<ElfSymbol> out = new ArrayList<>();
        for (ElfSymbol s : elf.symbols) {
            if (!s.dynamic) {
                out.add(s);
            }
        }
        return out;
    }

    enum ProviderEligibility {
        ELIGIBLE_DEFINITION,
        NOT_A_DEFINITION,
        LOCAL,
        VISIBILITY_HIDDEN,
        VERSION_MISMATCH,
        COMDAT_DROPPED
    }

    /**
     * 判定 provider 对某个引用是否构成“合格定义”。引用方单元用于：
     *  - PROTECTED：若提供者与引用者同属一个共享对象，则为内部绑定（合格且不可插入）；
     *  - HIDDEN/INTERNAL：只对定义所在单元自己可见，其它单元引用直接淘汰。
     */
    private ProviderEligibility evaluateProvider(LoadUnit providerUnit, ElfSymbol providerSym,
                                                 UndefRef ref, LoadUnit refUnit) {
        if (!providerSym.isDefined() || providerSym.isLocal()) {
            return providerSym.isLocal() ? ProviderEligibility.LOCAL
                    : ProviderEligibility.NOT_A_DEFINITION;
        }
        // COMDAT 丢弃的 section 上的定义不可见
        if (providerUnit.elf.isSectionDroppedByComdat(providerSym.shndx)) {
            return ProviderEligibility.COMDAT_DROPPED;
        }
        boolean sameUnit = providerUnit.unitId.equals(refUnit.unitId);
        if (!sameUnit && providerSym.isHiddenOrInternal()) {
            return ProviderEligibility.VISIBILITY_HIDDEN;
        }
        VersionMatch.Result vr = VersionMatch.check(ref.versionName(), providerSym.version);
        if (vr != VersionMatch.Result.OK) {
            return ProviderEligibility.VERSION_MISMATCH;
        }
        return ProviderEligibility.ELIGIBLE_DEFINITION;
    }

    private String ineligibilityReason(ProviderEligibility elig, UndefRef ref, ElfSymbol sym) {
        return switch (elig) {
            case LOCAL -> "LOCAL 绑定，不参与跨对象裁决";
            case NOT_A_DEFINITION -> "该符号自身也是未定义引用";
            case VISIBILITY_HIDDEN ->
                    sym.visibilityLabel() + " 可见性，仅 " + "定义所在单元可见";
            case COMDAT_DROPPED -> "所在 section 属于被去重丢弃的 COMDAT 组";
            case VERSION_MISMATCH -> VersionMatch.reason(
                    VersionMatch.check(ref.versionName(), sym.version), ref.versionName(), sym);
            case ELIGIBLE_DEFINITION -> null;
        };
    }

    // ---------------------------------------------------------------- 引用裁决

    private void collectAndResolve() {
        // 以 RefKey 归并同一单元/同名/同版本的多个引用；重定位全部挂到 affectedRelocs。
        LinkedHashMap<RefKey, RefResult> byKey = new LinkedHashMap<>();
        for (LoadUnit unit : activeInOrder()) {
            for (ElfSymbol sym : linkSymbolsOf(unit)) {
                if (sym.isLocal() || !sym.isUndef() || sym.name.isEmpty()) {
                    continue;
                }
                String verName = sym.version != null ? sym.version.name : null;
                RefKey key = new RefKey(unit.unitId, sym.name, verName);
                RefResult rr = byKey.computeIfAbsent(key,
                        k -> new RefResult(k, sym.bindLabel(), sym.typeLabel()));
                attachRelocations(unit, sym, rr);
            }
            // copy relocation：可执行/可重定位对象对共享对象 OBJECT 符号的 COPY 重定位，
            // 即便符号在 symtab 已定义也必须单列（不能被普通覆盖规则吞掉）
            collectCopyRelocations(unit, byKey);
        }
        for (RefResult rr : byKey.values()) {
            resolveOne(rr);
            decision.refs.add(rr);
        }
        // 符号表最终快照一步
        step("SYMBOL_UPDATE", "最终全局符号表",
                "全部活跃单元的裁决后符号表快照", null, null, symbolTableSnapshot());
    }

    private void attachRelocations(LoadUnit unit, ElfSymbol sym, RefResult rr) {
        for (ElfReloc r : unit.elf.relocs) {
            if (r.symbolIndex != sym.index
                    || court.elf.RelocTypes.isCopy(unit.elf.machine, r.type)) {
                continue;
            }
            // reloc 的符号表与引用符号所在表一致（reloc 通过 sh_link 指向 dyn/sym tab）
            if (!relocUsesSymbolTable(unit, r, sym.dynamic)) {
                continue;
            }
            boolean copy = court.elf.RelocTypes.isCopy(unit.elf.machine, r.type);
            rr.affectedRelocs.add(new AffectedReloc(unit.unitId, unit.name, r.sectionName,
                    r.offset, r.typeName, r.addend, r.hasAddend, copy));
        }
    }

    private boolean relocUsesSymbolTable(LoadUnit unit, ElfReloc r, boolean dynamic) {
        court.elf.ElfSection rs = unit.elf.sectionAt(r.sectionIndex);
        if (rs == null) {
            return !dynamic;
        }
        court.elf.ElfSection linked = unit.elf.sectionAt(rs.link);
        if (linked == null) {
            return !dynamic;
        }
        return linked.type == (dynamic
                ? court.elf.ElfSection.SHT_DYNSYM : court.elf.ElfSection.SHT_SYMTAB);
    }

    private void collectCopyRelocations(LoadUnit unit, LinkedHashMap<RefKey, RefResult> byKey) {
        for (ElfReloc r : unit.elf.relocs) {
            if (!court.elf.RelocTypes.isCopy(unit.elf.machine, r.type)) {
                continue;
            }
            court.elf.ElfSection rs = unit.elf.sectionAt(r.sectionIndex);
            ElfSymbol sym = relocSymbol(unit, rs, r.symbolIndex);
            if (sym == null || sym.name.isEmpty()) {
                continue;
            }
            String verName = sym.version != null ? sym.version.name : null;
            RefKey key = new RefKey(unit.unitId, sym.name, verName);
            RefResult rr = byKey.computeIfAbsent(key,
                    k -> new RefResult(k, "GLOBAL", sym.typeLabel()));
            rr.affectedRelocs.add(new AffectedReloc(unit.unitId, unit.name, r.sectionName,
                    r.offset, r.typeName, r.addend, r.hasAddend, true));
            rr.detail = (rr.detail == null ? "" : rr.detail)
                    + "存在 R_*_COPY：将在本地产生数据副本，不能按普通覆盖处理; ";
        }
    }

    private ElfSymbol relocSymbol(LoadUnit unit, court.elf.ElfSection relocSec, int idx) {
        boolean dyn = relocSec != null;
        court.elf.ElfSection targetSymTab = relocSec == null ? null
                : unit.elf.sectionAt(relocSec.link);
        boolean wantDyn = targetSymTab != null
                && targetSymTab.type == court.elf.ElfSection.SHT_DYNSYM;
        for (ElfSymbol s : linkSymbolsOf(unit)) {
            if (s.dynamic == wantDyn && s.index == idx) {
                return s;
            }
        }
        return null;
    }

    private LoadUnit unitOf(String unitId) {
        return units.get(unitId);
    }

    private void resolveOne(RefResult rr) {
        LoadUnit refUnit = unitOf(rr.key.unitId);
        ElfSymbol refSym = findRefSymbol(refUnit, rr.key);
        UndefRef ref = new UndefRef(refUnit, refSym, rr.key.versionName);

        // 候选枚举：遍历全部活跃单元的全部定义，逐个记录保留/淘汰原因。
        for (LoadUnit unit : activeInOrder()) {
            for (ElfSymbol sym : linkSymbolsOf(unit)) {
                if (!sym.name.equals(rr.key.symbol)) {
                    continue;
                }
                ProviderEligibility elig = evaluateProvider(unit, sym, ref, refUnit);
                boolean common = sym.isCommon();
                boolean comdat = unit.elf.isSectionDroppedByComdat(sym.shndx);
                boolean copy = rr.affectedRelocs.stream().anyMatch(a -> a.copy);
                Provider provider = new Provider(unit, sym, common, comdat,
                        comdatSignatureOf(unit, sym.shndx), copy);
                Candidate candidate = new Candidate(provider);
                if (elig != ProviderEligibility.ELIGIBLE_DEFINITION) {
                    candidate.eligible = false;
                    candidate.rejectReason = ineligibilityReason(elig, ref, sym);
                } else {
                    candidate.rank = Rank.of(unit, sym, common);
                    candidate.rankNote = Rank.label(candidate.rank);
                    // RTLD 作用域下，PROTECTED 同单元引用强制内部绑定
                    if (policy == Policy.RTLD_GLOBAL && unit.unitId.equals(refUnit.unitId)
                            && sym.isProtected()) {
                        candidate.rankNote += "；PROTECTED 内部绑定（不可插入）";
                    }
                }
                rr.candidates.add(candidate);
            }
        }

        boolean weakRef = "WEAK".equals(rr.binding);
        boolean hasCopy = rr.affectedRelocs.stream().anyMatch(a -> a.copy);

        if (policy == Policy.STATIC_LD) {
            resolveStatic(rr, weakRef, hasCopy);
        } else {
            resolveRtld(rr, weakRef, hasCopy);
        }
        step("RESOLVE", "裁决 " + rr.key.symbol
                        + (rr.key.versionName == null ? "" : "@" + rr.key.versionName),
                verdictText(rr), rr.key.unitId, rr.key.symbol);
    }

    private ElfSymbol findRefSymbol(LoadUnit unit, RefKey key) {
        for (ElfSymbol s : linkSymbolsOf(unit)) {
            if (s.isUndef() && s.name.equals(key.symbol)) {
                String v = s.version != null ? s.version.name : null;
                if (Objects.equals(v, key.versionName)) {
                    return s;
                }
            }
        }
        // 理论不可达（RefKey 由符号构造）
        throw new IllegalStateException("引用符号丢失: " + key);
    }

    private String comdatSignatureOf(LoadUnit unit, int shndx) {
        for (ComdatGroup g : unit.elf.groups) {
            if (g.memberSections.contains(shndx)) {
                return g.signature;
            }
        }
        return null;
    }

    // ------------------------------------------------ gABI 状态表裁决（静态）

    private void resolveStatic(RefResult rr, boolean weakRef, boolean hasCopy) {
        List<Candidate> eligible = new ArrayList<>();
        for (Candidate c : rr.candidates) {
            if (c.eligible) {
                eligible.add(c);
            }
        }
        if (eligible.isEmpty()) {
            rr.status = weakRef ? "WEAK_UNDEF_KEPT" : "UNRESOLVED";
            rr.detail = weakRef
                    ? "弱引用保持未定义（静态链接中弱未定义不报错，地址为 0）"
                    : "没有合格提供者（见各候选淘汰原因）";
            return;
        }

        // 层级裁决：STRONG_REL > COMMON_REL > WEAK_REL > SHARED_DEF > SHARED_WEAK
        int bestRank = eligible.stream().mapToInt(c -> c.rank).min().orElse(99);
        List<Candidate> best = new ArrayList<>();
        for (Candidate c : eligible) {
            if (c.rank == bestRank) {
                best.add(c);
            }
        }

        if (bestRank == Rank.STRONG_REL) {
            if (best.size() > 1) {
                // 多个同等强定义：保留歧义，不能用文件遍历顺序悄悄选一个。
                // 唯一例外：同一 COMDAT 签名已保证去重；走到这里就是真冲突。
                markAmbiguous(rr, best, "多个 relocatable 强定义，同等候选无法裁决（multiple definition）");
                return;
            }
            pick(rr, best.get(0), hasCopy);
            return;
        }

        if (bestRank == Rank.COMMON_REL) {
            if (best.size() > 1) {
                // common 合并：选尺寸最大者；同尺寸为“平局”，按显式加载顺序取先到（ld 的确定行为）。
                long maxSize = best.stream().mapToLong(c -> c.provider.size).max().orElse(0);
                List<Candidate> largest = new ArrayList<>();
                for (Candidate c : best) {
                    if (c.provider.size == maxSize) {
                        largest.add(c);
                    }
                }
                Candidate chosen = largest.stream()
                        .min(Comparator.comparingInt(c -> c.provider.unitOrdinal)).orElseThrow();
                chosen.rankNote += "；common 合并（最大对齐/尺寸 " + maxSize + "）";
                if (largest.size() > 1) {
                    chosen.rankNote += "；同尺寸平局，按显式加载顺序选择 " + chosen.provider.unitName;
                }
                pick(rr, chosen, hasCopy);
                return;
            }
            pick(rr, best.get(0), hasCopy);
            return;
        }

        if (bestRank == Rank.WEAK_REL) {
            // 多个弱定义不构成错误：按显式加载顺序取第一个，记录其余为被覆盖
            Candidate chosen = best.stream()
                    .min(Comparator.comparingInt(c -> c.provider.unitOrdinal)).orElseThrow();
            chosen.rankNote += "；多个弱定义不报错，按加载顺序取首个";
            pick(rr, chosen, hasCopy);
            return;
        }

        // 只剩共享库定义：静态链接中普通引用被共享库满足（不抽取 archive 之外的场景），
        // 加载顺序先到先得（ld 的 DT_NEEDED/命令行顺序）
        Candidate chosen = best.stream()
                .min(Comparator.comparingInt(c -> c.provider.unitOrdinal)).orElseThrow();
        if (best.size() > 1) {
            chosen.rankNote += "；多个共享库候选，按显式加载顺序选择";
        }
        pick(rr, chosen, hasCopy);
    }

    // ------------------------------------------------ RTLD 全局作用域裁决

    private void resolveRtld(RefResult rr, boolean weakRef, boolean hasCopy) {
        List<Candidate> eligible = new ArrayList<>();
        for (Candidate c : rr.candidates) {
            if (c.eligible) {
                eligible.add(c);
            }
        }
        if (eligible.isEmpty()) {
            rr.status = weakRef ? "WEAK_UNDEF_KEPT" : "UNRESOLVED";
            rr.detail = weakRef ? "弱引用在动态加载中解析为 NULL" : "全局作用域中没有合格提供者";
            return;
        }
        // 1) PROTECTED 同单元：内部绑定，优先于全局作用域
        for (Candidate c : eligible) {
            if (c.provider.unitId.equals(rr.key.unitId)
                    && "PROTECTED".equals(c.provider.visibility)) {
                c.rankNote += "；PROTECTED 内部绑定，优先于全局作用域查找";
                pick(rr, c, hasCopy);
                return;
            }
        }
        // 2) global scope BFS 顺序先到先得（ordinal 已体现）。
        //    只有当多个候选 ordinal 完全相同时才算歧义（防御性：ordinal 唯一）。
        Candidate chosen = eligible.stream()
                .min(Comparator.comparingInt(c -> c.provider.unitOrdinal)).orElseThrow();
        long ties = eligible.stream()
                .filter(c -> c.provider.unitOrdinal == chosen.provider.unitOrdinal).count();
        if (ties > 1) {
            markAmbiguous(rr, eligible, "动态作用域同序多个提供者");
            return;
        }
        if (eligible.size() > 1) {
            chosen.rankNote += "；全局作用域先到先得";
        }
        pick(rr, chosen, hasCopy);
    }

    private void markAmbiguous(RefResult rr, List<Candidate> ties, String detail) {
        rr.status = "AMBIGUOUS";
        rr.detail = detail;
        for (Candidate c : rr.candidates) {
            if (ties.contains(c)) {
                c.rankNote = (c.rankNote == null ? "" : c.rankNote) + "；同等候选（歧义保留）";
            }
        }
    }

    private void pick(Candidate chosen, boolean hasCopy) {
        chosen.winner = true;
    }

    private void pick(RefResult rr, Candidate chosen, boolean hasCopy) {
        chosen.winner = true;
        rr.winner = chosen.provider;
        if (hasCopy) {
            rr.status = "COPY";
            rr.detail = "通过 copy relocation 绑定：可执行文件在本地保留数据副本，"
                    + chosen.provider.unitName + " 的访问也被重定向到该副本";
        } else if (chosen.provider.unitId.equals(rr.key.unitId)
                && "PROTECTED".equals(chosen.provider.visibility)) {
            rr.status = "LOCAL_BOUND";
            rr.detail = "PROTECTED 内部绑定：不经过全局查找表，不可被其它对象插入";
        } else {
            rr.status = "RESOLVED";
            rr.detail = chosen.rankNote;
        }
    }

    private String verdictText(RefResult rr) {
        StringBuilder sb = new StringBuilder();
        sb.append(rr.key.unitId == null ? "" : "").append(rr.binding).append(" 引用 ");
        sb.append(rr.key.symbol);
        if (rr.key.versionName != null) {
            sb.append("@").append(rr.key.versionName);
        }
        if (rr.winner != null) {
            sb.append(" -> ").append(rr.winner.unitName)
                    .append(" [").append(rr.winner.bind).append("/").append(rr.winner.visibility);
            if (rr.winner.versionName != null) {
                sb.append(" @").append(rr.winner.versionName);
            }
            sb.append("]");
        } else {
            sb.append(" => ").append(rr.status);
        }
        if (rr.affectedRelocs.size() > 0) {
            sb.append("；受影响重定位 ").append(rr.affectedRelocs.size()).append(" 处");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 步骤/快照

    private void step(String phase, String title, String description,
                      String focusUnitId, String focusSymbol) {
        step(phase, title, description, focusUnitId, focusSymbol, null);
    }

    private void step(String phase, String title, String description,
                      String focusUnitId, String focusSymbol, Map<String, String> snapshot) {
        decision.steps.add(new Step(stepCounter++, phase, title, description,
                focusUnitId, focusSymbol, snapshot));
    }

    /**
     * 当前“全局符号表”快照：按活跃顺序扫描定义，名字+版本 -> 当前裁决描述。
     * 只用于页面动画，不参与裁决本身。
     */
    private Map<String, String> symbolTableSnapshot() {
        Map<String, String> snap = new LinkedHashMap<>();
        for (LoadUnit unit : activeInOrder()) {
            for (ElfSymbol sym : linkSymbolsOf(unit)) {
                if (sym.isLocal() || !sym.isDefined() || sym.name.isEmpty()) {
                    continue;
                }
                if (unit.elf.isSectionDroppedByComdat(sym.shndx)) {
                    continue;
                }
                String ver = sym.version != null ? "@" + sym.version.name : "";
                String key = sym.name + ver;
                String desc = unit.name + " " + sym.bindLabel() + "/" + sym.visibilityLabel()
                        + (sym.isCommon() ? "/COMMON" : "")
                        + " ord=" + unit.ordinal;
                snap.merge(key, desc, (oldVal, newVal) -> oldVal + "  ||  " + newVal);
            }
        }
        return snap;
    }
}
