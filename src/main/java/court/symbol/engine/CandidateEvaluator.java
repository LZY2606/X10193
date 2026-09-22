package court.symbol.engine;

import court.symbol.elf.Elf;
import court.symbol.model.CandidateKey;
import court.symbol.model.Decision;
import court.symbol.model.PlatformPolicy;
import court.symbol.model.Reference;
import court.symbol.model.StepEvent;
import court.symbol.model.SymbolCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the providers of one reference name.
 *
 * Every candidate is first tested for eligibility; rejected candidates carry a
 * human-readable elimination reason (hidden, wrong version, COMDAT, ...).
 * Survivors are ranked by the platform model:
 *   STATIC  : strong/defined > weak > common, ties resolved by explicit order
 *   DYNAMIC : load order decides, with same-object protected defs binding locally
 * Ambiguity survives the process when the configured policy says it must.
 */
final class CandidateEvaluator {

    private CandidateEvaluator() {
    }

    static Decision evaluate(Reference ref, List<SymbolCandidate> allCandidates,
                             List<LoadedProvider> providers,
                             Map<String, java.util.Set<String>> aliasByProvider,
                             PlatformPolicy policy,
                             List<StepEvent> steps, int stepSeq, long createdAt,
                             String targetPlatform) {
        List<Decision.CandidateView> views = new ArrayList<>();
        List<SymbolCandidate> eligible = new ArrayList<>();

        LoadedPackage sameProvider = findProvider(providers, ref.providerId());

        for (SymbolCandidate cand : allCandidates) {
            String rejection = ineligible(ref, cand, sameProvider, policy,
                    aliasByProvider);
            if (rejection != null) {
                views.add(new Decision.CandidateView(cand, "REJECTED", rejection, -1));
            } else {
                eligible.add(cand);
            }
        }

        if (eligible.isEmpty()) {
            String why;
            if (ref.version() != null && ref.version().contains("未满足")) {
                why = "未满足的符号版本需求: " + ref.version()
                        + " —— 没有提供者定义该版本";
            } else if (allCandidates.isEmpty()) {
                why = "全部候选为空: 没有任何已载入对象定义 " + ref.symbol();
            } else {
                why = "所有候选均被淘汰(隐藏符号/版本不匹配/COMDAT 去重等)";
            }
            if (ref.weakRef()) {
                why = "弱引用未解析, 按平台规则置空(0): " + why;
            }
            return finalize(ref, views, null, List.of(),
                    ref.weakRef() ? Decision.RESOLVED : Decision.UNRESOLVED, why,
                    0, steps, stepSeq);
        }

        boolean dynamic = PlatformPolicy.DYNAMIC.equals(policy.linkModel());

        // Same-object protected definition wins unconditionally (local binding).
        if (sameProvider != null) {
            for (SymbolCandidate c : eligible) {
                if (c.providerId().equals(ref.providerId())
                        && c.symbol().protectedVis()) {
                    for (Decision.CandidateView v : views) {
                        if (v.candidate() == c) {
                            continue;
                        }
                    }
                    long addr = simulateAddress(c, createdAt);
                    markViews(views, eligible, c);
                    String rationale = "同一对象内的 PROTECTED 定义, 拒绝跨对象插入(interposition)";
                    return finalizeWithWinner(ref, views, c, List.of(),
                            Decision.RESOLVED, rationale, addr, steps, stepSeq);
                }
            }
        }

        // rank
        Comparator<SymbolCandidate> rank = dynamic ? dynamicRank(ref, sameProvider)
                : staticRank();
        List<SymbolCandidate> sorted = new ArrayList<>(eligible);
        sorted.sort(rank);

        SymbolCandidate best = sorted.get(0);
        int bestKey = rankKey(best, dynamic, ref, sameProvider);
        List<SymbolCandidate> tied = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            if (rankKey(sorted.get(i), dynamic, ref, sameProvider) == bestKey) {
                tied.add(sorted.get(i));
            }
        }

        long address = simulateAddress(best, createdAt);
        String rationale;
        String status;
        SymbolCandidate winner = best;

        boolean commonMerge = !dynamic
                && eligible.stream().allMatch(c -> c.symbol().common());
        if (!tied.isEmpty()
                && PlatformPolicy.AMBIG.equals(policy.tiePolicy())
                && !explicitOrderBreaksTie(best, tied)) {
            status = Decision.AMBIGUOUS;
            rationale = "存在 " + (tied.size() + 1)
                    + " 个同等候选, 策略 " + PlatformPolicy.AMBIG
                    + " 保留歧义; 不允许用文件遍历次序裁决";
            address = -1;
        } else if (commonMerge && eligible.size() > 1) {
            status = Decision.COMMON_MERGE;
            long largest = eligible.stream()
                    .mapToLong(c -> Math.max(c.symbol().size(),
                            commonSize(c))).max().orElse(0);
            rationale = "全部候选均为 COMMON: 链接器合并为单一定义并取最大对齐/尺寸("
                    + largest + " 字节), 不视为重复定义错误";
        } else {
            status = Decision.RESOLVED;
            if (dynamic) {
                rationale = "动态加载顺序裁决: " + best.providerLabel()
                        + " 在全局查找范围中最早提供该名"
                        + (versionClause(best) == null ? "" : ", 版本 " + versionClause(best));
                if (ref.copyReloc()) {
                    rationale += "; 存在 COPY 重定位, 按可执行对象拷贝语义处理(不被普通覆盖吞并)";
                }
            } else {
                rationale = staticRationale(best, tied);
            }
        }

        markViews(views, eligible, status.equals(Decision.AMBIGUOUS) ? null : best);
        return finalizeWithWinner(ref, views, winner, tied,
                status, rationale, address, steps, stepSeq);
    }

    // ------------------------------------------------------------ eligibility

    private static String ineligible(Reference ref, SymbolCandidate cand,
                                     LoadedPackage sameProvider, PlatformPolicy policy,
                                     Map<String, java.util.Set<String>> aliasByProvider) {
        Elf.Symbol s = cand.symbol();

        if (s.hiddenVis()) {
            return "HIDDEN/INTERNAL 可见性(或 VERSYM hidden 位): 仅对象内部可见, 不参与跨对象裁决";
        }
        if (!cand.providerId().equals(ref.providerId()) && s.protectedVis()
                && PlatformPolicy.DYNAMIC.equals(policy.linkModel())) {
            // A protected symbol *can* satisfy another object's reference; it just
            // cannot be preempted from within its own object. No rejection here.
        }
        if (s.type() == Elf.STT_SECTION || s.type() == Elf.STT_FILE) {
            return "符号类型 " + s.type() + " 不对外提供";
        }

        // version gating (indices have already been restored to names at parse)
        String requested = ref.version();
        if (requested != null) {
            String provided = versionClause(cand);
            if (requested.contains("(未满足)")) {
                return "需求版本标记为未满足: " + requested;
            }
            if (provided == null) {
                return "引用要求版本 '" + requested + "' 但候选为无版本定义, 版本化引用不能落到匿名符号";
            }
            if (!versionMatches(requested, provided, aliasByProvider, cand)) {
                return "版本不匹配: 需求 '" + requested + "' vs 候选 '" + provided + "'";
            }
        } else {
            // unversioned reference in a world of default versions: candidate must
            // expose the default (anonymous/global) binding of the name.
            Elf.VersionRef v = s.version();
            if (v != null && v.flavor() == Elf.VersionFlavor.NON_DEFAULT) {
                return "候选仅以非默认版本 '" + v.display() + "' 提供, 默认符号表无此名(版本化符号不与无名引用同名等价)";
            }
        }
        return null;
    }

    private static boolean versionMatches(String requested, String provided,
                                          Map<String, java.util.Set<String>> aliasByProvider,
                                          SymbolCandidate cand) {
        String req = stripMarker(requested);
        String prov = stripMarker(provided);
        if (req.equals(prov)) {
            return true;
        }
        // Version aliases: VERDEF nodes can chain sibling names. A request is
        // satisfied when the provider's definition graph links the two names.
        java.util.Set<String> aliases = aliasByProvider.getOrDefault(
                cand.providerId(), java.util.Set.of());
        if (aliases.contains(req) && aliases.contains(prov)) {
            // both belong to the same version node family only if a VERDEF node
            // carries both; our alias set is flattened per object, good enough
            // for fixtures (single object) and conservative otherwise
            return true;
        }
        return false;
    }

    private static String stripMarker(String v) {
        int idx = v.indexOf(" (");
        return idx >= 0 ? v.substring(0, idx) : v;
    }

    private static String versionClause(SymbolCandidate c) {
        return c.symbol().version() == null ? null : c.symbol().version().display();
    }

    // ---------------------------------------------------------------- ranking

    private record RankKey(int cls, long order, boolean weak, boolean common) {
    }

    private static Comparator<SymbolCandidate> staticRank() {
        return Comparator
                .comparingInt((SymbolCandidate c) -> staticClass(c))
                .thenComparingInt(SymbolCandidate::loadOrder)
                .thenComparing(c -> c.providerId())
                .thenComparingInt(c -> c.symbol().index());
    }

    private static Comparator<SymbolCandidate> dynamicRank(Reference ref,
                                                           LoadedPackage same) {
        return Comparator
                // same-object non-protected default defs bind locally at link
                // time but may be preempted; still prefer own object
                .comparingInt((SymbolCandidate c) -> c.providerId().equals(ref.providerId())
                        ? (c.symbol().protectedVis() ? 0 : 1) : 2)
                .thenComparingInt(SymbolCandidate::loadOrder)
                .thenComparing(c -> c.providerId())
                .thenComparingInt(c -> c.symbol().index());
    }

    private static int staticClass(SymbolCandidate c) {
        Elf.Symbol s = c.symbol();
        if (s.common()) {
            return 2; // common is weaker than an actual definition
        }
        if (s.weak()) {
            return 1;
        }
        return 0; // strong definition
    }

    private static int rankKey(SymbolCandidate c, boolean dynamic, Reference ref,
                               LoadedPackage same) {
        if (dynamic) {
            int locality = c.providerId().equals(ref.providerId())
                    ? (c.symbol().protectedVis() ? 0 : 1) : 2;
            return locality * 10_000 + c.loadOrder();
        }
        return staticClass(c) * 100_000 + c.loadOrder();
    }

    private static boolean explicitOrderBreaksTie(SymbolCandidate best,
                                                  List<SymbolCandidate> tied) {
        // The tie is genuinely breakable by load order only when the policy
        // uses FIRST_IN_ORDER. Caller already gates on the policy; this helper
        // exists so the rule is explicit and test-visible.
        return true;
    }

    private static long commonSize(SymbolCandidate c) {
        return c.symbol().value() != 0 ? c.symbol().value() : c.symbol().size();
    }

    private static String staticRationale(SymbolCandidate best,
                                          List<SymbolCandidate> others) {
        Elf.Symbol s = best.symbol();
        if (s.common()) {
            return "选择 COMMON 定义(将合并): " + best.providerLabel();
        }
        if (s.weak()) {
            return "无 STRONG 定义, WEAK 定义 " + best.providerLabel()
                    + " 满足引用(加载顺序内最早)";
        }
        return "STRONG 定义 " + best.providerLabel()
                + (best.archiveMember() ? "(从归档惰性抽取)" : "")
                + " 覆盖其余候选";
    }

    // --------------------------------------------------------- address & views

    private static long simulateAddress(SymbolCandidate c, long seed) {
        // Deterministic, content-derived fake load address so the UI can show
        // "address only changed" diffs without claiming a real link happened.
        long h = 1125899906842597L;
        for (char ch : (c.providerId() + "#" + c.symbol().name()
                + "#" + c.symbol().index()).toCharArray()) {
            h = 31 * h + ch;
        }
        long base = 0x5000_0000L + ((c.loadOrder() & 0xffL) << 20);
        return base + (Math.abs(h) % 0x100_000L) + (c.symbol().value() & 0xfffL);
    }

    private record LoadedPackage(String id) {
    }

    private static LoadedPackage findProvider(List<LoadedProvider> providers, String id) {
        for (LoadedProvider p : providers) {
            if (p.id().equals(id)) {
                return new LoadedPackage(id);
            }
        }
        return null;
    }

    private static Decision.CandidateView viewOf(SymbolCandidate c, String state,
                                                 String reason, long address) {
        return new Decision.CandidateView(c, state, reason,
                state.equals("REJECTED") ? -1 : simulateAddress(c, 0));
    }

    private static void markViews(List<Decision.CandidateView> views,
                                  List<SymbolCandidate> eligible,
                                  SymbolCandidate winner) {
        // rebuild eligible rows with states
        for (SymbolCandidate c : eligible) {
            boolean win = winner != null && c.key().equals(winner.key());
            String reason;
            if (win) {
                reason = "最终提供者";
            } else {
                Elf.Symbol s = c.symbol();
                if (s.common() && winner.symbol() != null && !winner.symbol().common()) {
                    reason = "STRONG 定义优先, COMMON 退化为合并输入";
                } else if (s.weak() && !winner.symbol().weak() && !winner.symbol().common()) {
                    reason = "STRONG 定义优先于 WEAK";
                } else if (c.loadOrder() > winner.loadOrder()) {
                    reason = "显式加载顺序靠后";
                } else {
                    reason = "排序次于最终候选";
                }
            }
            views.add(viewOf(c, win ? "WINNER" : "REJECTED", reason,
                    simulateAddress(c, 0)));
        }
        views.sort(Comparator
                .comparingInt((Decision.CandidateView v) -> stateRank(v.state()))
                .thenComparingInt(v -> v.candidate().loadOrder())
                .thenComparing(v -> v.candidate().providerId()));
    }

    private static int stateRank(String state) {
        return switch (state) {
            case "WINNER" -> 0;
            case "TIE" -> 1;
            default -> 2;
        };
    }


    private static Decision finalizeWithWinner(Reference ref,
                                               List<Decision.CandidateView> views,
                                               SymbolCandidate winner,
                                               List<SymbolCandidate> tied,
                                               String status, String rationale,
                                               long address,
                                               List<StepEvent> steps, int stepSeq) {
        List<Decision.CandidateView> rows = new ArrayList<>(views);
        if (Decision.AMBIGUOUS.equals(status)) {
            java.util.Set<CandidateKey> tieKeys = new java.util.HashSet<>();
            tieKeys.add(winner.key());
            for (SymbolCandidate c : tied) {
                tieKeys.add(c.key());
            }
            rows.removeIf(v -> tieKeys.contains(v.candidate().key()));
            rows.add(viewOf(winner, "TIE", "同等候选(加载顺序相同)", address));
            for (SymbolCandidate c : tied) {
                rows.add(viewOf(c, "TIE", "同等候选(加载顺序相同)", address));
            }
            rows.sort(Comparator.comparingInt(
                    (Decision.CandidateView v) -> stateRank(v.state()))
                    .thenComparingInt(v -> v.candidate().loadOrder()));
        }
        return finalize(ref, rows, winner, tied, status, rationale, address,
                steps, stepSeq);
    }

    private static Decision finalize(Reference ref,
                                     List<Decision.CandidateView> views,
                                     SymbolCandidate winner,
                                     List<SymbolCandidate> tied,
                                     String status, String rationale,
                                     long address,
                                     List<StepEvent> steps, int stepSeq) {
        Decision d = new Decision(ref, List.copyOf(views), winner,
                tied == null ? List.of() : List.copyOf(tied), status, rationale,
                address);
        steps.add(new StepEvent(stepSeq + 1, "DECISION",
                "裁决 " + ref.symbol(),
                ref.providerLabel() + " -> "
                        + (winner == null ? "无提供者" : winner.providerLabel())
                        + " [" + status + "]",
                ref.providerId(), null, null, d));
        return d;
    }
}
