package court.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次方案执行的完整裁决结果（创建时快照入库；旧方案永不漂移）。
 */
public final class Decision {
    public String schemeId;
    public String schemeName;
    public String policy;
    public int policyOrdinalVersion; // 同名方案的第几个版本

    public final List<LoadUnit> loadOrder = new ArrayList<>();
    public final List<DependencyEdge> dependencies = new ArrayList<>();
    public final List<ComdatDecision> comdat = new ArrayList<>();
    public final List<RefResult> refs = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    public final List<BadObject> badObjects = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public int unresolvedCount() {
        int n = 0;
        for (RefResult r : refs) {
            if ("UNRESOLVED".equals(r.status)) {
                n++;
            }
        }
        return n;
    }

    public int ambiguousCount() {
        int n = 0;
        for (RefResult r : refs) {
            if ("AMBIGUOUS".equals(r.status)) {
                n++;
            }
        }
        return n;
    }
}
