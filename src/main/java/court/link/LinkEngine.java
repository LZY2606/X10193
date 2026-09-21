package court.link;

import court.elf.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * The symbol arbitration court core. Performs a static-link-flavoured resolution over
 * the explicit load order WITHOUT invoking a system linker:
 *
 *   1. standalone objects/shared objects enter immediately;
 *   2. archive members are extracted lazily only to satisfy current undefined refs
 *      (REPEAT re-scans for circular dependencies; SINGLE_PASS never revisits);
 *   3. COMDAT groups are deduplicated by signature;
 *   4. each key is arbitrated (strong > common > weak; object > shared) with every
 *      candidate and its rejection reason recorded;
 *   5. copy relocations are handled specially (never swallowed by normal override),
 *      and ties remain AMBIGUOUS instead of being broken by filesystem order.
 */
@Component
public class LinkEngine {

    private final BinaryLoader loader;
    private final UnitExtractor extractor;

    public LinkEngine(BinaryLoader loader, UnitExtractor extractor) {
        this.loader = loader;
        this.extractor = extractor;
    }

    public LinkReport resolve(List<LoadedFile> files, String schemeName, LinkPolicy policy) {
        LinkReport rep = new LinkReport();
        rep.schemeName = schemeName;
        rep.policy = policy;
        rep.policySummary = policy.platformName + " / archive=" + policy.archivePolicy
                + " / ties=" + policy.tiePolicy;

        Deque<StepEvent> steps = new ArrayDeque<>();
        int[] seq = {0};

        // units keyed by stable unitId
        LinkedHashMap<String, UnitModel> active = new LinkedHashMap<>();
        List<LoadedFile> archives = new ArrayList<>();
        List<UnitModel> comdatOwners = new ArrayList<>();
        Map<String, UnitModel> comdatWinner = new HashMap<>();

        for (LoadedFile f : files) {
            if (f.kind.equals("ARCHIVE")) {
                archives.add(f);
                steps.add(new StepEvent(seq[0]++, "LOAD", "导入归档 " + f.fileName,
                        f.archive.members.size() + " 个成员，符号表含 "
                                + f.archive.symbolToMember.size() + " 个定义名"));
                continue;
            }
            if (f.corrupt || f.kind.equals("CORRUPT")) {
                UnitModel bad = new UnitModel();
                bad.unitId = f.fileName;
                bad.displayName = f.fileName;
                bad.sourceFile = f.fileName;
                bad.inputOrder = f.order;
                bad.corrupt = true;
                bad.error = f.error;
                active.put(bad.unitId, bad);
                rep.warnings.add("已隔离损坏对象 " + f.fileName + "：" + f.error);
                steps.add(new StepEvent(seq[0]++, "LOAD", "隔离损坏对象 " + f.fileName, f.error));
                continue;
            }
            UnitModel u = extractor.extract(f.standalone(), f.order);
            addUnit(u, active, policy, rep, steps, seq, comdatWinner, comdatOwners);
        }

        lazyExtract(files, archives, active, policy, rep, steps, seq, comdatWinner, comdatOwners);
        rep.steps.addAll(steps);
        assignOrder(active, rep);
        buildDependencyGraph(active, rep);
        buildVersionGraph(files, rep);
        arbitrate(active, policy, rep, steps, seq);
        assignAddresses(active, rep, policy);
        rep.counts.put("units", rep.units.size());
        rep.counts.put("verdicts", rep.verdicts.size());
        rep.counts.put("steps", rep.steps.size());
        return rep;
    }

    private void addUnit(UnitModel u, LinkedHashMap<String, UnitModel> active, LinkPolicy policy,
                         LinkReport rep, Deque<StepEvent> steps, int[] seq,
                         Map<String, UnitModel> comdatWinner, List<UnitModel> comdatOwners) {
        if (u.corrupt) {
            active.put(u.unitId, u);
            rep.warnings.add("已隔离损坏对象 " + u.displayName + "：" + u.error);
            steps.add(new StepEvent(seq[0]++, "LOAD", "隔离损坏对象 " + u.displayName, u.error));
            return;
        }
        if (u.machine != policy.machine) {
            u.incompatible = true;
            active.put(u.unitId, u);
            rep.warnings.add("平台不匹配 " + u.displayName + "（machine=" + u.machine + "）");
            steps.add(new StepEvent(seq[0]++, "LOAD", "平台不匹配 " + u.displayName,
                    "目标 " + policy.platformName + "，对象 machine=" + u.machine));
            return;
        }
        dedupeComdat(u, rep, steps, seq, comdatWinner);
        active.put(u.unitId, u);
        if (u.fromArchive) u.extracted = true;
        String action = u.fromArchive ? "抽取成员 " + u.displayName : "载入对象 " + u.displayName;
        steps.add(new StepEvent(seq[0]++, u.fromArchive ? "EXTRACT" : "LOAD", action,
                u.providers.size() + " 个提供者，" + u.references.size() + " 个引用"));
    }

    private void dedupeComdat(UnitModel u, LinkReport rep, Deque<StepEvent> steps, int[] seq,
                              Map<String, UnitModel> comdatWinner) {
        Map<String, List<Provider>> bySig = new LinkedHashMap<>();
        for (Provider p : u.providers) {
            if (p.comdatSignature != null) bySig.computeIfAbsent(p.comdatSignature, k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<String, List<Provider>> e : bySig.entrySet()) {
            String sig = e.getKey();
            UnitModel owner = comdatWinner.get(sig);
            if (owner == null) {
                comdatWinner.put(sig, u);
                StepEvent st = new StepEvent(seq[0]++, "COMDAT", "接纳 COMDAT `" + sig + "`",
                        u.displayName + " 保留该 section group");
                st.unitId = u.unitId; st.symbol = sig;
                steps.add(st);
            } else {
                // drop every provider belonging to the duplicate group in this unit
                Set<Provider> drop = new HashSet<>(e.getValue());
                u.providers.removeIf(drop::contains);
                StepEvent st = new StepEvent(seq[0]++, "COMDAT", "折叠重复 COMDAT `" + sig + "`",
                        u.displayName + " 中的副本被丢弃，保留 " + owner.displayName + " 的版本（非普通覆盖）");
                st.unitId = u.unitId; st.symbol = sig;
                steps.add(st);
                rep.warnings.add("COMDAT 去重：" + u.displayName + " 中 `" + sig + "` 已折叠到 " + owner.displayName);
            }
        }
    }

    private void lazyExtract(List<LoadedFile> files, List<LoadedFile> archives,
                             LinkedHashMap<String, UnitModel> active, LinkPolicy policy,
                             LinkReport rep, Deque<StepEvent> steps, int[] seq,
                             Map<String, UnitModel> comdatWinner, List<UnitModel> comdatOwners) {
        Set<String> extractedMemberUnits = new HashSet<>();
        boolean repeat = "REPEAT".equalsIgnoreCase(policy.archivePolicy);
        boolean everProgressed;
        do {
            Set<SymbolKey> missing = collectMissing(active);
            boolean progressed = false;
            for (LoadedFile ar : archives) {
                for (SymbolKey key : new ArrayList<>(missing)) {
                    List<Integer> memberIdxs = ar.archive.symbolToMember.get(key.name);
                    if (memberIdxs == null) continue;
                    for (Integer mi : memberIdxs) {
                        ArReader.Member member = ar.archive.members.get(mi);
                        String unitId = ar.fileName + "(" + member.name + ")";
                        if (extractedMemberUnits.contains(unitId)) continue;
                        LoadedBinary bin = loader.loadMember(ar, member);
                        UnitModel u = extractor.extract(bin, ar.order);
                        if (u.corrupt) {
                            rep.warnings.add("归档成员损坏已隔离：" + unitId + "：" + u.error);
                            steps.add(new StepEvent(seq[0]++, "EXTRACT", "跳过损坏成员 " + unitId, u.error));
                            extractedMemberUnits.add(unitId);
                            continue;
                        }
                        boolean definesKey = u.providers.stream().anyMatch(p -> matches(p, key));
                        if (!definesKey) {
                            extractedMemberUnits.add(unitId);
                            continue;
                        }
                        extractedMemberUnits.add(unitId);
                        addUnit(u, active, policy, rep, steps, seq, comdatWinner, comdatOwners);
                        progressed = true;
                        StepEvent st = new StepEvent(seq[0]++, "EXTRACT",
                                "惰性抽取 " + ar.fileName + "(" + member.name + ")",
                                "用于满足未定义引用 " + key);
                        st.unitId = unitId;
                        st.symbol = key.toString();
                        st.after.put("undefinedBefore", missing.size());
                        steps.add(st);
                        break;
                    }
                }
            }
            everProgressed = progressed;
        } while (repeat && everProgressed);
    }

    /** Collect currently unsatisfied reference keys across active units. */
    private Set<SymbolKey> collectMissing(LinkedHashMap<String, UnitModel> active) {
        List<Provider> allProviders = new ArrayList<>();
        for (UnitModel u : active.values()) if (!u.incompatible && !u.corrupt) allProviders.addAll(u.providers);
        Set<SymbolKey> missing = new LinkedHashSet<>();
        for (UnitModel u : active.values()) {
            if (u.incompatible || u.corrupt) continue;
            for (Reference r : u.references) {
                SymbolKey key = new SymbolKey(r.name, r.version, "WEAK".equals(r.binding));
                if (!satisfiedBy(key, allProviders)) missing.add(key);
            }
        }
        return missing;
    }

    private boolean satisfiedBy(SymbolKey key, List<Provider> providers) {
        for (Provider p : providers) if (matches(p, key)) return true;
        return false;
    }

    /** Version-aware matching: name equal AND the version requirement is answered. */
    private boolean matches(Provider p, SymbolKey key) {
        if (!p.name.equals(key.name)) return false;
        if ("HIDDEN".equals(p.visibility) || "INTERNAL".equals(p.visibility)) return false;
        if (key.version.isEmpty()) {
            // unversioned ref binds to unversioned/default definition, not to a hidden version alias
            return p.version.isEmpty();
        }
        return p.version.equals(key.version);
    }

    private void assignOrder(LinkedHashMap<String, UnitModel> active, LinkReport rep) {
        int order = 0;
        for (UnitModel u : active.values()) {
            u.resolvedOrder = order++;
            rep.units.add(u);
        }
    }

    private void buildDependencyGraph(LinkedHashMap<String, UnitModel> active, LinkReport rep) {
        Set<String> unitNames = new HashSet<>();
        for (UnitModel u : active.values()) unitNames.add(u.soname != null ? u.soname : u.displayName);
        for (UnitModel u : active.values()) {
            for (String need : u.needed) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("from", u.displayName);
                edge.put("to", need);
                edge.put("resolved", unitNames.contains(need));
                rep.dependencyGraph.add(edge);
            }
        }
    }

    private void buildVersionGraph(List<LoadedFile> files, LinkReport rep) {
        for (LoadedFile f : files) {
            ElfImage img = f.image;
            if (img == null) continue;
            for (VersionDef d : img.versionDefs) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("file", f.fileName);
                n.put("kind", "DEF");
                n.put("name", d.name);
                n.put("index", d.index);
                n.put("nonDefault", true);
                n.put("aliases", d.aliases);
                rep.versionGraph.add(n);
            }
            for (VersionNeed vn : img.versionNeeds) {
                for (VersionNeed.Entry e : vn.entries) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("file", f.fileName);
                    n.put("kind", "NEED");
                    n.put("name", e.name);
                    n.put("from", vn.file);
                    n.put("weak", e.weak);
                    rep.versionGraph.add(n);
                }
            }
        }
    }

    // ---- arbitration ------------------------------------------------------

    private void arbitrate(LinkedHashMap<String, UnitModel> active, LinkPolicy policy,
                           LinkReport rep, Deque<StepEvent> steps, int[] seq) {
        // group references by (name + required version). A weak and a strong reference to the
        // same symbol share one arbitration; the group is only weak when every reference is weak.
        LinkedHashMap<String, List<Reference>> refGroups = new LinkedHashMap<>();
        for (UnitModel u : active.values()) {
            if (u.incompatible || u.corrupt) continue;
            for (Reference r : u.references) {
                String gk = r.name + " " + r.version;
                refGroups.computeIfAbsent(gk, k -> new ArrayList<>()).add(r);
            }
        }

        List<Provider> all = new ArrayList<>();
        for (UnitModel u : active.values()) if (!u.incompatible && !u.corrupt) all.addAll(u.providers);

        for (Map.Entry<String, List<Reference>> e : refGroups.entrySet()) {
            List<Reference> refs = e.getValue();
            boolean allWeak = refs.stream().allMatch(r -> "WEAK".equals(r.binding));
            String rname = refs.get(0).name;
            String rver = refs.get(0).version;
            SymbolKey key = new SymbolKey(rname, rver, allWeak);
            Verdict v = new Verdict();
            v.name = rname;
            v.requestedVersion = rver;
            v.weakReference = allWeak;
            v.key = key.toString();
            v.affectedReferences.addAll(refs);
            v.copyRelocation = refs.stream().anyMatch(r -> r.copyRelocation);

            List<Provider> nameMatch = new ArrayList<>();
            for (Provider p : all) if (p.name.equals(key.name)) nameMatch.add(p);

            // build candidates with elimination reasons
            List<Provider> eligible = new ArrayList<>();
            for (Provider p : nameMatch) {
                Candidate c = new Candidate();
                c.unitId = p.unitId;
                c.displayName = p.displayName;
                c.version = p.version;
                c.binding = p.binding;
                c.visibility = p.visibility;
                c.definitionClass = p.definitionClass;
                String reject = rejection(p, key, v);
                if (reject == null) {
                    c.status = "TIE";
                    eligible.add(p);
                } else {
                    c.status = "ELIMINATED";
                    c.reason = reject;
                }
                v.candidates.add(c);
            }

            decide(v, eligible, key, policy);
            rep.verdicts.add(v);

            StepEvent st = new StepEvent(seq[0]++, "RESOLVE", "裁决 " + key, v.summary);
            st.symbol = key.toString();
            if (v.winner != null) st.unitId = v.winner.unitId;
            steps.add(st);
        }
    }

    private String rejection(Provider p, SymbolKey key, Verdict v) {
        if ("HIDDEN".equals(p.visibility) || "INTERNAL".equals(p.visibility)) {
            return "visibility=" + p.visibility + "，不参与跨对象裁决（仅本对象内可见）";
        }
        // version rules
        if (!key.version.isEmpty()) {
            if (!p.version.equals(key.version)) {
                return "版本不匹配：引用需要 " + key.version + "，该定义是 "
                        + (p.version.isEmpty() ? "无版本/默认" : p.version);
            }
        } else {
            if (!p.version.isEmpty()) {
                return "同名但非默认版本 `" + p.version + "`：无版本引用不能绑定到隐藏版本别名";
            }
        }
        if (v.copyRelocation) {
            if (!"SHARED_DEF".equals(p.definitionClass)) {
                return "copy relocation 只能复制共享对象中的数据，不能由可重定位/可执行定义满足";
            }
            if (!"OBJECT".equals(p.type) && !"TLS".equals(p.type) && !"NOTYPE".equals(p.type)) {
                return "copy relocation 目标必须是数据对象（" + p.type + " 不适用）";
            }
        }
        return null;
    }

    private void decide(Verdict v, List<Provider> eligible, SymbolKey key, LinkPolicy policy) {
        if (eligible.isEmpty()) {
            if (!key.version.isEmpty() && anyName(v)) {
                v.outcome = "UNMET_VERSION";
                v.summary = "存在同名符号但没有版本 `" + key.version + "` 的提供者（未满足版本需求）";
            } else if (v.copyRelocation) {
                v.outcome = "UNRESOLVED";
                v.summary = "copy relocation 找不到可复制的共享数据对象";
            } else if (key.weakRef) {
                v.outcome = "RESOLVED";
                v.summary = "弱引用未定义：按 0 解析（weak undefined，非错误）";
            } else {
                v.outcome = "UNRESOLVED";
                v.summary = "无可见且版本匹配的提供者，未定义符号";
            }
            return;
        }

        // precedence rank: OBJECT_DEF strong > COMMON > SHARED_DEF strong
        //                  > OBJECT_DEF weak > SHARED_DEF weak
        List<Provider> ranked = new ArrayList<>(eligible);
        ranked.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        int best = rank(ranked.get(0));
        List<Provider> winners = new ArrayList<>();
        for (Provider p : ranked) {
            if (rank(p) != best) break;
            winners.add(p);
        }
        for (Candidate c : v.candidates) {
            for (Provider p : eligible) {
                if (c.unitId.equals(p.unitId) && c.version.equals(p.version) && c.status.equals("TIE")) {
                    c.rank = rank(p);
                }
            }
        }

        if (winners.size() > 1) {
            // genuinely equal candidates: must not be silently broken by filesystem order
            boolean allCommon = winners.stream().allMatch(Provider::isCommon);
            if (allCommon) {
                Provider largest = winners.get(0);
                for (Provider p : winners) if (p.size > largest.size) largest = p;
                finalize(v, largest, "多个 COMMON 同名，选择最大尺寸 " + largest.size
                        + "（对齐后合并），其余作为候选保留", policy);
                return;
            }
            if ("LOAD_ORDER".equalsIgnoreCase(policy.tiePolicy)) {
                winners.sort(Comparator.comparingInt(this::orderOf));
                Provider pick = winners.get(0);
                finalize(v, pick, "存在 " + winners.size() + " 个同等候选；按显式加载顺序裁决为 "
                        + pick.displayName + "（policy.tiePolicy=LOAD_ORDER，非文件系统遍历序）", policy);
                markLosers(v, winners.subList(1, winners.size()),
                        "同等强度但显式加载顺序靠后", policy);
                return;
            }
            for (Provider p : winners) markCandidate(v, p, "AMBIGUOUS", "同等候选，保留歧义");
            v.outcome = "AMBIGUOUS";
            StringBuilder sb = new StringBuilder("存在 ").append(winners.size())
                    .append(" 个同等强度的提供者，无法仅凭加载顺序以外的信息裁决，保留歧义：");
            for (Provider p : winners) sb.append(' ').append(p.displayName);
            v.summary = sb.toString();
            return;
        }

        Provider win = winners.get(0);
        List<Provider> losers = new ArrayList<>();
        for (Provider p : ranked) if (p != win) losers.add(p);
        finalize(v, win, winReason(win), policy);
        markLosers(v, losers, loseReason(win), policy);
    }

    private boolean anyName(Verdict v) {
        return v.candidates.stream().anyMatch(c -> c.status.equals("ELIMINATED")
                && c.reason != null && c.reason.startsWith("版本不匹配"));
    }

    private int rank(Provider p) {
        boolean strong = "GLOBAL".equals(p.binding);
        switch (p.definitionClass) {
            case "OBJECT_DEF":
                return strong ? 0 : 3;
            case "COMMON":
                return 1;
            case "SHARED_DEF":
                return strong ? 2 : 4;
            default:
                return 5;
        }
    }

    private int orderOf(Provider p) { return p.loadOrder; }

    private String winReason(Provider p) {
        switch (p.definitionClass) {
            case "OBJECT_DEF":
                return "GLOBAL".equals(p.binding)
                        ? "strong 定义优先于 weak/common/共享定义"
                        : "weak 定义仍可覆盖共享/弱候选";
            case "COMMON":
                return "COMMON（暂定公共块）优先于共享库中的定义";
            case "SHARED_DEF":
                return "唯一可用的共享库导出定义（可被前序对象拦截）";
            default:
                return "选中";
        }
    }

    private String loseReason(Provider win) {
        switch (win.definitionClass) {
            case "OBJECT_DEF":
                return "被 " + win.displayName + " 的 strong 定义覆盖（非错误：正常覆盖规则）";
            case "COMMON":
                return "被 COMMON 公共块压过（链接结束时可被强定义提升）";
            case "SHARED_DEF":
                return "加载顺序中位于被选共享库之后或同为弱导出";
            default:
                return "优先级较低";
        }
    }

    private void finalize(Verdict v, Provider p, String reason, LinkPolicy policy) {
        v.winner = p;
        v.outcome = "RESOLVED";
        v.summary = reason;
        markCandidate(v, p, "WINNER", reason);
        v.predictedAddress = p.predictedAddress;
    }

    private void markLosers(Verdict v, List<Provider> losers, String reason, LinkPolicy policy) {
        for (Provider p : losers) markCandidate(v, p, "ELIMINATED", reason);
    }

    private void markCandidate(Verdict v, Provider p, String status, String reason) {
        for (Candidate c : v.candidates) {
            if (c.unitId.equals(p.unitId) && Objects.equals(c.version, p.version)) {
                c.status = status;
                c.reason = reason;
                c.predictedAddress = p.predictedAddress;
            }
        }
    }

    // ---- address prediction ----------------------------------------------

    private void assignAddresses(LinkedHashMap<String, UnitModel> active, LinkReport rep, LinkPolicy policy) {
        long textBase = 0x400000;
        long dataBase = 0x600000;
        for (UnitModel u : rep.units) {
            if (u.corrupt || u.incompatible) continue;
            long t = textBase + u.resolvedOrder * 0x10000L;
            long d = dataBase + u.resolvedOrder * 0x10000L;
            for (Provider p : u.providers) {
                long base = "OBJECT".equals(p.type) || "TLS".equals(p.type) ? d : t;
                p.loadOrder = u.resolvedOrder;
                p.predictedAddress = base + (p.sectionIndex & 0xfff);
            }
        }
        for (Verdict v : rep.verdicts) {
            if (v.winner != null) v.predictedAddress = v.winner.predictedAddress;
            for (Candidate c : v.candidates) {
                if (v.winner != null && c.unitId.equals(v.winner.unitId)
                        && Objects.equals(c.version, v.winner.version)) {
                    c.predictedAddress = v.winner.predictedAddress;
                }
            }
        }
    }
}
