package court.engine;

import court.elf.ElfConst;
import court.elf.ElfObject;
import court.elf.Sym;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static link phase: direct ET_REL objects define eagerly; archive members are
 * pulled lazily only to satisfy outstanding strong (optionally weak) references.
 */
final class StaticPhase {

    void run(LinkResult r, Policy policy) {
        // Fixed point with explicit sweep counter so archive cycles terminate.
        for (int pass = 1; pass <= Math.max(1, policy.maxArchivePasses); pass++) {
            boolean progressed = false;
            for (Unit archive : archiveRoots(r)) {
                progressed |= scanArchive(r, policy, archive, pass);
            }
            if (!progressed) break;
        }

        // Static adjudication against active ET_REL definitions.
        List<Def> liveDefs = new ArrayList<>();
        for (Unit u : r.units) {
            if (u.active && u.elf != null && Linker.isStaticKind(u)) {
                for (Def d : u.defs) {
                    if (d.live && d.defined) liveDefs.add(d);
                }
            }
        }

        for (Unit u : r.units) {
            if (u.elf == null || !Linker.isStaticKind(u)) continue;
            for (Ref ref : new ArrayList<>(u.refs)) {
                if (ref.internal) {
                    resolveInternal(r, policy, u, ref, liveDefs);
                    continue;
                }
                adjudicate(r, policy, u, ref, liveDefs, false);
            }
        }
        adjudicateUnreferencedDefs(r, policy, liveDefs);
    }

    /** Definitions with no referencing relocation are still candidates and may
     *  themselves be ambiguous/conflicting; surface them as resolutions. */
    private void adjudicateUnreferencedDefs(LinkResult r, Policy policy, List<Def> liveDefs) {
        java.util.Map<String, List<Def>> byName = new LinkedHashMap<>();
        for (Def d : liveDefs) {
            byName.computeIfAbsent(d.name + "\u0000" + (d.version == null ? "" : d.version),
                    k -> new ArrayList<>()).add(d);
        }
        for (java.util.Map.Entry<String, List<Def>> en : byName.entrySet()) {
            List<Def> ds = en.getValue();
            boolean global = ds.stream().anyMatch(d -> d.bind != ElfConst.STB_LOCAL);
            if (!global) continue;
            String[] parts = en.getKey().split("\u0000", -1);
            String key = parts[0] + "\u0000" + parts[1];
            Res existing = ResTable.get(r).get(key);
            if (existing != null && existing.refs != null && !existing.refs.isEmpty()) continue;
            Ref synthetic = new Ref();
            synthetic.name = parts[0];
            synthetic.version = parts[1].isEmpty() ? null : parts[1];
            synthetic.unitId = ds.get(0).unitId;
            synthetic.unitName = ds.get(0).unitName;
            synthetic.bind = ElfConst.STB_GLOBAL;
            Res res = ResTable.get(r).computeIfAbsent(key,
                    k -> new Res(k, synthetic.name, synthetic.version));
            res.staticCandidates = ds;
            res.stage = "STATIC";
            Def winner = pickStatic(r, policy, synthetic, ds);
            if (winner != null) {
                res.winner = winner;
                res.status = "RESOLVED";
                res.detail = "无引用的定义，按强弱/顺序裁决";
                res.address = "0x" + Long.toHexString(winner.value);
            } else if (!"AMBIGUOUS".equals(res.status)) {
                res.status = "CONFLICT";
                res.detail = "重复定义冲突";
            }
        }
    }

    private List<Unit> archiveRoots(LinkResult r) {
        // Each archive appears once; scan in the explicit order of its members.
        Map<String, Unit> roots = new LinkedHashMap<>();
        for (Unit u : r.units) {
            if (u.kind.equals("ARCHIVE_MEMBER") && u.archiveId != null) {
                roots.putIfAbsent(u.archiveId, u);
            }
        }
        List<Unit> ordered = new ArrayList<>(roots.values());
        ordered.sort(Comparator.comparingLong((Unit u) -> u.rankMajor));
        return ordered;
    }

    private boolean scanArchive(LinkResult r, Policy policy, Unit root, int pass) {
        boolean progressed = false;
        for (Unit member : membersOf(r, root.archiveId)) {
            if (member.active || member.elf == null) continue;
            // Members themselves are ordered; within-archive order is content.
            for (Def d : member.defs) {
                if (!d.defined || !d.live) continue;
                Ref need = outstanding(r, d.name, d.version, policy);
                if (need != null) {
                    extract(r, policy, member, need, pass);
                    progressed = true;
                    break;
                }
            }
        }
        return progressed;
    }

    private List<Unit> membersOf(LinkResult r, String archiveId) {
        List<Unit> out = new ArrayList<>();
        for (Unit u : r.units) {
            if (archiveId.equals(u.archiveId)) out.add(u);
        }
        out.sort(Comparator.comparingLong((Unit u) -> u.rankMajor)
                .thenComparingLong(u -> u.rankMinor));
        return out;
    }

    private Ref outstanding(LinkResult r, String name, String version, Policy policy) {
        Ref found = null;
        for (Unit u : r.units) {
            if (!u.active || u.elf == null || !Linker.isStaticKind(u)) continue;
            for (Ref ref : u.refs) {
                if (ref.internal) continue;
                if (!ref.name.equals(name)) continue;
                if (version != null ? !version.equals(ref.version) : ref.version != null) continue;
                if (alreadySatisfied(r, ref)) continue;
                if (ref.weak && !policy.weakExtractsArchives) continue;
                if (found == null || rankRef(ref, found) < 0) found = ref;
            }
        }
        return found;
    }

    private int rankRef(Ref a, Ref b) {
        return a.unitName.compareTo(b.unitName);
    }

    private boolean alreadySatisfied(LinkResult r, Ref ref) {
        for (Unit u : r.units) {
            if (!u.active || u.elf == null || !Linker.isStaticKind(u)) continue;
            for (Def d : u.defs) {
                if (!d.live || !d.defined) continue;
                if (!d.name.equals(ref.name)) continue;
                if (d.version != null ? d.version.equals(ref.version) : ref.version == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void extract(LinkResult r, Policy policy, Unit member, Ref need, int pass) {
        member.active = true;
        member.dormant = false;
        member.extractPass = pass;
        member.extractedFor = need.unitName + " 需要 " + need.name;
        for (Def d : member.defs) d.dormant = false;
        processMemberComdat(r, member);
        // New relocations were harvested at load time from this member; their
        // refs already exist and now participate in subsequent sweeps.
        Step s = Linker.addStep(r, "ARCHIVE_EXTRACT", "惰性抽取 " + member.memberName,
                member.archiveName + " 中成员被 " + member.extractedFor + " 拉出（第 "
                        + pass + " 轮）", member.id, need.name);
        s.data.put("archive", member.archiveName);
        s.data.put("member", member.memberName);
        s.data.put("pass", pass);
        s.data.put("neededBy", need.unitName);
        s.symbolTable = Linker.snapshot(r, false);
        for (LinkResult.DependencyEdge e : r.edges) {
            if (e.from.equals(member.archiveName) && e.to.equals(member.memberName)) {
                e.kind = "LAZY_EXTRACT";
                e.satisfied = true;
            }
        }
    }

    private void processMemberComdat(LinkResult r, Unit u) {
        Map<String, String> winners = ComdatState.get(r);
        for (ElfObject.Section sec : u.elf.sections) {
            if (sec.group == null || sec.group.signature == null) continue;
            if ((sec.group.flags & ElfConst.GRP_COMDAT) == 0) continue;
            String sig = sec.group.signature;
            String winner = winners.get(sig);
            if (winner == null) {
                winners.put(sig, u.name);
            } else if (!winner.equals(u.name)) {
                discard(r, u, sec.group, winner);
            }
        }
    }

    private void discard(LinkResult r, Unit u, ElfObject.Group g, String winner) {
        Unit.UnitDiscard d = new Unit.UnitDiscard();
        d.signature = g.signature;
        d.winnerUnit = winner;
        d.reason = "重复 COMDAT 组，首见者 " + winner + " 胜出";
        for (int idx : g.members) {
            if (idx >= 0 && idx < u.elf.sectionByIndex.size()) {
                ElfObject.Section mem = u.elf.sectionByIndex.get(idx);
                if (mem != null) {
                    mem.discarded = true;
                    d.sections.add(mem.name);
                }
            }
        }
        for (ElfObject.Section sec : u.elf.sections) {
            if (sec.group == g) sec.discarded = true;
        }
        u.discards.add(d);
        for (Def def : u.defs) {
            if (def.sym != null && def.sym.section != null && def.sym.section.discarded) {
                def.live = false;
                def.eliminatedReason = "COMDAT 组被去重丢弃";
                def.eliminationStage = "COMDAT";
            }
        }
        Linker.addStep(r, "COMDAT_DISCARD", "COMDAT 去重 " + g.signature,
                u.name + " 的组成员被丢弃，保留 " + winner, u.id, g.signature);
    }

    private void resolveInternal(LinkResult r, Policy policy, Unit u, Ref ref,
                                 List<Def> liveDefs) {
        for (Def d : liveDefs) {
            if (d.unitId.equals(u.id) && d.name.equals(ref.name)) {
                record(r, u, ref, d, "STATIC_LOCAL", "本对象内部定义", liveDefs, true);
                return;
            }
        }
        // Defining section discarded (e.g. lost COMDAT): fall through as external.
        ref.internal = false;
        adjudicate(r, policy, u, ref, liveDefs, true);
    }

    private void adjudicate(LinkResult r, Policy policy, Unit u, Ref ref,
                            List<Def> liveDefs, boolean fromInternalFallback) {
        // Static scope: only same-version plain static defs (no symbol versions
        // are used by ET_REL, so require null version).
        List<Def> matches = new ArrayList<>();
        for (Def d : liveDefs) {
            if (!d.name.equals(ref.name)) continue;
            if (ref.version != null && !ref.version.equals(d.version)) continue;
            matches.add(d);
        }
        record(r, u, ref, pickStatic(r, policy, ref, matches), "STATIC",
                null, matches, false);
    }

    private Def pickStatic(LinkResult r, Policy policy, Ref ref, List<Def> matches) {
        if (matches.isEmpty()) return null;
        List<Def> strong = new ArrayList<>();
        List<Def> weak = new ArrayList<>();
        List<Def> common = new ArrayList<>();
        for (Def d : matches) {
            if (d.common) {
                if (d.bind != ElfConst.STB_WEAK) common.add(d);
                else weak.add(d);
            } else if (d.bind == ElfConst.STB_WEAK) {
                weak.add(d);
            } else {
                strong.add(d);
            }
        }
        if (strong.size() == 1) return strong.get(0);
        if (strong.size() > 1) {
            if (policy.tieMode == Policy.TieMode.FIRST) {
                strong.sort(Comparator.comparingLong(d -> d.order));
                return strong.get(0);
            }
            return Ambiguous.mark(r, ref, strong, "STATIC_DUPLICATE_STRONG",
                    "多个 strong 定义，无显式先后优先级");
        }
        if (!common.isEmpty()) {
            common.sort(Comparator.comparingLong(d -> d.order));
            Def merged = common.get(0);
            Linker.addStep(r, "COMMON_MERGE", "common 合并 " + ref.name,
                    merged.unitName + " 提供 common 块（" + common.size()
                            + " 个同名 common 合并，取最大对齐/大小）", merged.unitId, ref.name);
            return merged;
        }
        if (weak.size() == 1) return weak.get(0);
        if (weak.size() > 1) {
            if (policy.tieMode == Policy.TieMode.FIRST) {
                weak.sort(Comparator.comparingLong(d -> d.order));
                return weak.get(0);
            }
            return Ambiguous.mark(r, ref, weak, "STATIC_DUPLICATE_WEAK",
                    "多个同等 weak 提供者，不能用文件遍历次序决定");
        }
        return null;
    }

    private void record(LinkResult r, Unit u, Ref ref, Def winner, String stage,
                        String forcedReason, List<Def> candidates, boolean internalWin) {
        String key = Res.keyOf(ref);
        Res existing = ResTable.get(r).get(key);
        if (existing == null) {
            existing = new Res(key, ref.name, ref.version);
            ResTable.get(r).put(key, existing);
        }
        existing.stage = stage;
        existing.refs.add(ref);
        existing.staticCandidates = candidates;
        if (winner != null) {
            existing.winner = winner;
            existing.status = "RESOLVED";
            existing.detail = forcedReason != null ? forcedReason : staticReason(winner);
        }
        if (!internalWin && forcedReason == null && winner == null) {
            existing.status = ref.weak ? "UNRESOLVED_WEAK" : "UNRESOLVED";
            existing.detail = ref.weak
                    ? "weak 未定义引用，静态阶段留待动态阶段或解析为 0"
                    : "静态阶段无提供者，留待动态阶段";
        }
    }

    private String staticReason(Def winner) {
        if (winner.common) return "common 定义合并后胜出";
        return winner.bind == ElfConst.STB_WEAK ? "唯一 weak 定义胜出" : "strong 定义胜出";
    }
}
