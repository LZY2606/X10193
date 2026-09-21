package court.engine;

import court.model.Decision;
import court.model.Provider;
import court.model.RefDiff;
import court.model.RefResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 两个方案结果的语义比较：区分“引用换了提供者”和“只是地址变化”。
 * 结果是方案创建时的快照，因此比较不受后续加载顺序/策略改动影响。
 */
public final class SchemeComparator {

    private SchemeComparator() {
    }

    public static List<RefDiff> compare(Decision from, Decision to) {
        Map<String, RefResult> a = index(from);
        Map<String, RefResult> b = index(to);
        List<RefDiff> diffs = new ArrayList<>();

        for (Map.Entry<String, RefResult> e : a.entrySet()) {
            RefResult ra = e.getValue();
            RefResult rb = b.get(e.getKey());
            if (rb == null) {
                diffs.add(new RefDiff(e.getKey(), "GONE_REF",
                        status(ra), null, "引用在新方案中消失（其来源单元未被装载/抽取）"));
                continue;
            }
            diffs.add(pair(e.getKey(), ra, rb));
        }
        for (Map.Entry<String, RefResult> e : b.entrySet()) {
            if (!a.containsKey(e.getKey())) {
                diffs.add(new RefDiff(e.getKey(), "NEW_REF",
                        null, status(e.getValue()), "引用在新方案中新出现（来源单元被新装载/抽取）"));
            }
        }
        return diffs;
    }

    private static RefDiff pair(String key, RefResult ra, RefResult rb) {
        Provider pa = ra.winner;
        Provider pb = rb.winner;
        if (pa == null && pb == null) {
            if (ra.status.equals(rb.status)) {
                return new RefDiff(key, "SAME", ra.status, rb.status,
                        "两个方案中均未解析/弱未定义");
            }
            return new RefDiff(key, "STATUS_CHANGED", ra.status, rb.status,
                    "未解析状态变化: " + ra.status + " -> " + rb.status);
        }
        if (pa == null || pb == null) {
            return new RefDiff(key, "STATUS_CHANGED", status(ra), status(rb),
                    "解析状态变化: " + ra.status + " -> " + rb.status);
        }
        boolean sameUnit = pa.unitId.equals(pb.unitId);
        boolean sameVersion = eq(pa.versionName, pb.versionName);
        boolean sameKind = pa.bind.equals(pb.bind) && pa.common == pb.common;
        if (sameUnit && sameVersion && sameKind && pa.symbolIndex == pb.symbolIndex) {
            if (pa.value != pb.value || pa.size != pb.size) {
                return new RefDiff(key, "ADDRESS_ONLY",
                        pa.unitName + "+0x" + Long.toHexString(pa.value),
                        pb.unitName + "+0x" + Long.toHexString(pb.value),
                        "提供者相同（" + pa.unitName + " 的同一符号），仅 st_value/尺寸不同");
            }
            return new RefDiff(key, "SAME_PROVIDER", pa.unitName, pb.unitName,
                    "提供者与地址均未变化");
        }
        String from = pa.unitName + " " + pa.bind + (pa.versionName != null ? "@" + pa.versionName : "");
        String to = pb.unitName + " " + pb.bind + (pb.versionName != null ? "@" + pb.versionName : "");
        String note;
        if (sameUnit && (!sameVersion || pa.symbolIndex != pb.symbolIndex)) {
            note = "同一对象内更换了版本/符号槽位";
        } else if (sameVersion) {
            note = "版本要求相同，但绑定到了不同对象的提供者";
        } else {
            note = "提供者发生切换（顺序或策略改变了裁决）";
        }
        return new RefDiff(key, "PROVIDER_CHANGED", from, to, note);
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String status(RefResult r) {
        return r.winner != null ? r.winner.unitName : r.status;
    }

    private static Map<String, RefResult> index(Decision d) {
        Map<String, Map<String, RefResult>> byUnit = new LinkedHashMap<>();
        Map<String, RefResult> flat = new LinkedHashMap<>();
        for (RefResult r : d.refs) {
            flat.put(r.key.toString(), r);
        }
        return flat;
    }
}
