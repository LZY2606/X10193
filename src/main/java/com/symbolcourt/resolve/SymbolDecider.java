package com.symbolcourt.resolve;

import com.symbolcourt.model.*;
import com.symbolcourt.model.Enums.*;

import java.util.*;

/**
 * Builds a SymbolResolution for every referenced symbol key:
 * lists every candidate, records elimination reasons, and chooses the winner
 * (or keeps AMBIGUOUS / MULTIPLE_DEFINITION / UNSATISFIED_VERSION / UNDEFINED).
 */
public class SymbolDecider {

    private final PlatformPolicy policy;
    private final CandidateRegistry reg;
    private final LinkResult result;

    public SymbolDecider(PlatformPolicy policy, CandidateRegistry reg, LinkResult result) {
        this.policy = policy;
        this.reg = reg;
        this.result = result;
    }

    public void decide() {
        Set<SymbolKey> keys = new LinkedHashSet<>();
        for (RefRecord r : reg.refs) keys.add(r.key());
        for (SymbolKey k : reg.global.keySet()) keys.add(k);

        for (SymbolKey key : keys) {
            SymbolResolution res = new SymbolResolution();
            res.key = key;
            res.name = key.name;
            res.requestedVersion = key.version;
            for (RefRecord r : reg.refs) {
                if (r.key().equals(key)) {
                    res.referencedBy.add(r.unit.label());
                    if (r.weak) res.weakReference = true;
                }
            }
            evaluate(key, res);
            result.resolutions.put(key, res);
        }
    }

    private void evaluate(SymbolKey key, SymbolResolution res) {
        List<Candidate> sameName = reg.byNameGlobal.getOrDefault(key.name, new ArrayList<>());
        List<Candidate> exact = reg.global.getOrDefault(key, new ArrayList<>());

        // Every definition of this name is a candidate; record why some are out.
        for (Candidate c : sameName) {
            CandidateEvaluation ev = new CandidateEvaluation(c);
            ev.rank = rank(c);
            if (key.version != null) {
                if (c.key.version == null) {
                    if (c.symbol.dynamic && c.symbol.versionDefault) {
                        // default alias can answer an unversioned request, but a
                        // non-default version request must match the real node
                        ev.accepted = false;
                        ev.eliminationReason = "仅默认版本别名，不能满足非默认版本 " + key.version;
                    } else {
                        ev.accepted = false;
                        ev.eliminationReason = "未版本化定义，缺少需求版本 " + key.version;
                    }
                } else if (!c.key.version.equals(key.version)) {
                    ev.accepted = false;
                    ev.eliminationReason = "版本不匹配: 提供 " + c.key.version + "，需求 " + key.version;
                }
            } else {
                // unversioned request
                if (c.key.version != null && c.symbol.dynamic && !c.symbol.versionDefault) {
                    ev.accepted = false;
                    ev.eliminationReason = "非默认版本节点 (" + c.key.version
                            + ") 不响应未版本化引用";
                }
            }
            if (c.hidden || c.binding == Binding.LOCAL) {
                ev.accepted = false;
                ev.eliminationReason = combine(ev.eliminationReason,
                        c.binding == Binding.LOCAL ? "LOCAL 绑定不导出" : "HIDDEN 可见性不导出");
            }
            res.evaluations.add(ev);
        }

        List<Candidate> accepted = res.acceptedCandidates();

        if (accepted.isEmpty()) {
            handleEmpty(key, res, sameName);
            return;
        }

        int bestRank = accepted.stream().mapToInt(eRank()).min().orElse(0);
        List<Candidate> best = new ArrayList<>();
        for (CandidateEvaluation ev : res.evaluations) {
            if (ev.accepted && ev.rank == bestRank) best.add(ev.candidate);
        }
        // demote non-best candidates with an explanation
        for (CandidateEvaluation ev : res.evaluations) {
            if (ev.accepted && ev.rank > bestRank) {
                ev.accepted = false;
                ev.eliminationReason = "存在更强的定义类型 (rank " + ev.rank
                        + " > " + bestRank + ")";
            }
        }

        if (bestRank == RANK_COMMON) {
            Candidate pick = best.get(0);
            res.status = ResolutionStatus.COMMON_BLOCK;
            res.chosen = pick;
            res.note = "tentative COMMON 定义合并为公共块"
                    + (best.size() > 1 ? "（多个 COMMON 取最大尺寸，此处仅登记提供者）" : "");
            if (best.size() > 1) {
                long max = best.stream().mapToLong(c -> Math.max(c.size, c.value)).max().orElse(0);
                res.note += " 合并尺寸=" + max;
            }
            if (policy.commonPolicy == PlatformPolicy.CommonResolution.ERROR) {
                result.errors.add(key + ": COMMON 定义在严格策略下不允许");
            } else {
                result.warnings.add(key + ": 使用 COMMON 暂定定义合并");
            }
            return;
        }

        // strong regular definitions that clash => multiple definition
        boolean allStrongRegular = best.stream().allMatch(
                c -> c.binding != Binding.WEAK && !c.common && !c.shared);
        if (allStrongRegular && best.size() > 1) {
            Candidate first = best.get(0);
            List<Candidate> rest = best.subList(1, best.size());
            boolean distinctPositions = best.stream().map(c -> c.scanPos).distinct().count() > 1;
            if (distinctPositions) {
                res.status = ResolutionStatus.MULTIPLE_DEFINITION;
                res.note = "多个同等 strong 定义出现在显式加载顺序的不同位置";
                String msg = key + ": multiple definition (" + first.unitLabel() + " vs "
                        + rest.get(0).unitLabel() + ")";
                result.errors.add(msg);
                Step st = new Step(result.steps.size(), Step.Kind.MULTIPLE_DEFINITION,
                        "重复 strong 定义", key.toString());
                st.symbol = key.toString();
                best.forEach(c -> st.facts.add(c.describe()));
                result.steps.add(st);
                return;
            }
        }

        // tie handling: prefer earliest explicit load position, never file order
        int minPos = best.stream().mapToInt(c -> c.scanPos).min().orElse(0);
        List<Candidate> atMin = best.stream().filter(c -> c.scanPos == minPos).toList();
        if (atMin.size() > 1) {
            res.status = ResolutionStatus.AMBIGUOUS;
            res.note = "存在 " + atMin.size() + " 个加载顺序相同的同等候选，保留歧义（不以遍历次序裁决）";
            Step st = new Step(result.steps.size(), Step.Kind.CHOOSE_SYMBOL,
                    "保留歧义", key.toString());
            st.symbol = key.toString();
            atMin.forEach(c -> st.facts.add(c.describe()));
            result.steps.add(st);
            return;
        }

        Candidate winner = atMin.get(0);
        res.chosen = winner;
        res.status = ResolutionStatus.RESOLVED;
        Step st = new Step(result.steps.size(), Step.Kind.CHOOSE_SYMBOL,
                "选定提供者", key + " <- " + winner.unitLabel());
        st.symbol = key.toString();
        st.unit = winner.unitLabel();
        st.facts.add(winner.describe());
        result.steps.add(st);
    }

    private java.util.function.ToIntFunction<CandidateEvaluation> eRank() {
        return e -> rank(e.candidate);
    }

    private static final int RANK_STRONG = 1;
    private static final int RANK_SHARED_STRONG = 2;
    private static final int RANK_WEAK = 3;
    private static final int RANK_SHARED_WEAK = 4;
    private static final int RANK_COMMON = 5;
    private static final int RANK_SHARED_WEAK2 = 6;

    private int rank(Candidate c) {
        boolean weak = c.binding == Binding.WEAK;
        if (c.common) return RANK_COMMON;
        if (c.shared) {
            if (weak) return RANK_SHARED_WEAK;
            return RANK_SHARED_STRONG;
        }
        if (weak) return RANK_WEAK;
        return RANK_STRONG;
    }

    private void handleEmpty(SymbolKey key, SymbolResolution res, List<Candidate> sameName) {
        boolean versionedNameExists = false;
        Candidate versionedAt = null;
        for (Candidate c : reg.byNameGlobal.getOrDefault(key.name, List.of())) {
            if (c.key.version != null) {
                versionedNameExists = true;
                versionedAt = c;
            }
        }
        if (key.version != null && !versionedNameExists && !sameName.isEmpty()) {
            res.status = ResolutionStatus.UNDEFINED;
            res.note = "存在同名定义但没有任何版本信息";
            return;
        }
        if (key.version != null && (versionedNameExists || !sameName.isEmpty())) {
            res.status = ResolutionStatus.UNSATISFIED_VERSION;
            res.unsatisfiedVersionFoundAt = versionedAt != null ? versionedAt.unitLabel()
                    : (sameName.isEmpty() ? null : sameName.get(0).unitLabel());
            res.note = "名字可解析，但需求版本 " + key.version
                    + " 未被任何定义提供（未满足版本）";
            Step st = new Step(result.steps.size(), Step.Kind.UNSATISFIED_VERSION,
                    "未满足版本", key.toString());
            st.symbol = key.toString();
            for (Candidate c : reg.byNameGlobal.getOrDefault(key.name, List.of())) {
                st.facts.add(c.describe());
            }
            result.steps.add(st);
            if (!res.weakReference) result.errors.add(key + ": version not found");
            return;
        }
        if (res.weakReference) {
            res.status = ResolutionStatus.RESOLVED;
            res.note = "弱引用未解析，按 0 绑定";
            return;
        }
        res.status = ResolutionStatus.UNDEFINED;
        res.note = "没有任何导出候选";
    }

    private String combine(String a, String b) {
        if (a == null) return b;
        return a + "；" + b;
    }
}
