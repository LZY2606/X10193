package court.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic comparison of two immutable schemes. References that changed provider
 * are distinguished from references whose provider is identical but whose
 * resolved address moved.
 */
public final class SchemeDiff {

    public static final class Item {
        public String name;
        public String version;
        public String kind; // PROVIDER_CHANGED, ADDRESS_CHANGED, NEW, REMOVED, STATUS_CHANGED
        public String providerA;
        public String providerB;
        public String addressA;
        public String addressB;
        public String statusA;
        public String statusB;
        public String detail;
    }

    public Map<String, Object> compare(LinkResult a, LinkResult b) {
        Map<String, Resolution> ma = index(a);
        Map<String, Resolution> mb = index(b);
        List<Item> items = new ArrayList<>();
        for (String key : union(ma, mb)) {
            Resolution ra = ma.get(key);
            Resolution rb = mb.get(key);
            Item it = new Item();
            it.name = ra != null ? ra.name : rb.name;
            it.version = ra != null ? ra.version : rb.version;
            it.providerA = provider(ra);
            it.providerB = provider(rb);
            it.addressA = ra != null ? ra.winnerAddress : null;
            it.addressB = rb != null ? rb.winnerAddress : null;
            it.statusA = ra != null ? ra.status : null;
            it.statusB = rb != null ? rb.status : null;
            if (ra == null) {
                it.kind = "NEW";
                it.detail = "方案 B 新出现的引用";
            } else if (rb == null) {
                it.kind = "REMOVED";
                it.detail = "方案 B 中该引用消失";
            } else if (!str(it.statusA).equals(str(it.statusB))) {
                it.kind = "STATUS_CHANGED";
                it.detail = "裁决状态 " + it.statusA + " → " + it.statusB;
            } else if (!str(it.providerA).equals(str(it.providerB))) {
                it.kind = "PROVIDER_CHANGED";
                it.detail = "提供者 " + nz(it.providerA) + " → " + nz(it.providerB);
            } else if (!str(it.addressA).equals(str(it.addressB))) {
                it.kind = "ADDRESS_CHANGED";
                it.detail = "提供者未变，仅地址变化 " + nz(it.addressA) + " → " + nz(it.addressB);
            } else {
                continue;
            }
            items.add(it);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changedCount", items.size());
        out.put("items", items);
        return out;
    }

    private static String nz(String s) {
        return s == null ? "（无）" : s;
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private String provider(Resolution r) {
        if (r == null || r.winner == null) return null;
        Def d = r.winner;
        return d.unitName + "#" + d.name + (d.version != null ? "@" + d.version : "");
    }

    private Map<String, Resolution> index(LinkResult r) {
        Map<String, Resolution> m = new LinkedHashMap<>();
        for (Resolution x : r.resolutions) m.put(x.key, x);
        return m;
    }

    private List<String> union(Map<String, Resolution> a, Map<String, Resolution> b) {
        List<String> keys = new ArrayList<>();
        for (String k : a.keySet()) if (!keys.contains(k)) keys.add(k);
        for (String k : b.keySet()) if (!keys.contains(k)) keys.add(k);
        return keys;
    }
}
