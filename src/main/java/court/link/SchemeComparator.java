package court.link;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic diff of two scheme versions. It matches the same reference identity
 * (referencing unit + symbol + required version) in each side and classifies each:
 *   PROVIDER_CHANGED - the supplying unit/version changed (real semantic difference)
 *   ADDRESS_ONLY     - same provider, only predicted address changed
 *   RESOLVED_STATE   - resolution outcome changed (e.g. UNRESOLVED -> RESOLVED)
 * plus added/removed reference identities.
 */
@Component
public class SchemeComparator {

    public static final class RefChange {
        public String identity;
        public String name;
        public String version;
        public String fromProvider;
        public String toProvider;
        public Long fromAddress;
        public Long toAddress;
        public String fromOutcome;
        public String toOutcome;
        public String classification;
        public String note;
    }

    public static final class Diff {
        public String schemeA;
        public String schemeB;
        public final List<RefChange> providerChanged = new ArrayList<>();
        public final List<RefChange> addressOnly = new ArrayList<>();
        public final List<RefChange> stateChanged = new ArrayList<>();
        public final List<RefChange> added = new ArrayList<>();
        public final List<RefChange> removed = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public int identicalProviderCount;
    }

    private static final class Row {
        Verdict v;
        String providerId;
        Long address;
    }

    public Diff compare(LinkReport a, LinkReport b) {
        Diff d = new Diff();
        d.schemeA = label(a);
        d.schemeB = label(b);
        Map<String, Row> ra = index(a);
        Map<String, Row> rb = index(b);

        for (String id : ra.keySet()) {
            Row x = ra.get(id);
            Row y = rb.get(id);
            RefChange c = new RefChange();
            c.identity = id;
            c.name = x.v.name;
            c.version = x.v.requestedVersion;
            c.fromProvider = provider(x);
            c.fromAddress = x.address;
            c.fromOutcome = x.v.outcome;
            if (y == null) {
                c.note = "新版本中该引用消失";
                d.removed.add(c);
                continue;
            }
            c.toProvider = provider(y);
            c.toAddress = y.address;
            c.toOutcome = y.v.outcome;
            if (!eq(x.v.outcome, y.v.outcome)) {
                c.classification = "RESOLVED_STATE";
                c.note = x.v.outcome + " → " + y.v.outcome;
                d.stateChanged.add(c);
            } else if (!eq(x.providerId, y.providerId)) {
                c.classification = "PROVIDER_CHANGED";
                c.note = "提供者由 " + safe(x.providerId) + " 换成 " + safe(y.providerId);
                d.providerChanged.add(c);
            } else if (!eq(x.address, y.address)) {
                c.classification = "ADDRESS_ONLY";
                c.note = "提供者未变（" + safe(x.providerId) + "），仅预测地址变化 "
                        + x.address + " → " + y.address;
                d.addressOnly.add(c);
            } else {
                d.identicalProviderCount++;
            }
        }
        for (String id : rb.keySet()) {
            if (ra.containsKey(id)) continue;
            Row y = rb.get(id);
            RefChange c = new RefChange();
            c.identity = id;
            c.name = y.v.name;
            c.version = y.v.requestedVersion;
            c.toProvider = provider(y);
            c.toAddress = y.address;
            c.toOutcome = y.v.outcome;
            c.note = "新版本中新出现的引用";
            d.added.add(c);
        }
        d.notes.add("相同提供者的引用：" + d.identicalProviderCount
                + "；换提供者：" + d.providerChanged.size()
                + "；仅地址变化：" + d.addressOnly.size()
                + "；解析状态变化：" + d.stateChanged.size());
        return d;
    }

    private Map<String, Row> index(LinkReport r) {
        Map<String, Row> out = new LinkedHashMap<>();
        for (Verdict v : r.verdicts) {
            for (Reference ref : v.affectedReferences) {
                String id = ref.unitId + "\u0000" + ref.name + "\u0000" + v.requestedVersion;
                Row row = new Row();
                row.v = v;
                row.providerId = v.winner == null ? null
                        : v.winner.unitId + "\u0000" + v.winner.version;
                row.address = v.predictedAddress;
                out.put(id, row);
            }
        }
        return out;
    }

    private String provider(Row r) { return r.v.winner == null ? "(无)" : r.v.winner.displayName; }
    private String safe(String s) { return s == null ? "(无)" : s; }
    private boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }
    private String label(LinkReport r) {
        return r.schemeName + (r.policy == null ? "" : " [" + r.policySummary + "]");
    }
}
