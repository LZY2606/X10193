package com.symbolcourt.resolve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Semantic comparison of two link schemes. */
public class SchemeDiff {

    public static class RefChange {
        public String symbol;
        public String referencedFrom;
        public String providerBefore;
        public String providerAfter;
        public String beforeVersion;
        public String afterVersion;
        public boolean providerChanged;
        public boolean addressOnly;
        public String note;
    }

    public static class Result {
        public final List<RefChange> changes = new ArrayList<>();
        public final List<String> onlyInA = new ArrayList<>();
        public final List<String> onlyInB = new ArrayList<>();
        public final List<String> statusChanges = new ArrayList<>();
        public int providerSwitchCount;
        public int addressOnlyCount;
    }

    public Result compare(LinkResult a, LinkResult b) {
        Result out = new Result();
        Map<String, RelocationDecision> am = index(a);
        Map<String, RelocationDecision> bm = index(b);

        for (Map.Entry<String, RelocationDecision> e : am.entrySet()) {
            RelocationDecision rb = bm.get(e.getKey());
            RelocationDecision ra = e.getValue();
            if (rb == null) {
                out.onlyInA.add(e.getKey());
                continue;
            }
            boolean sameProvider = eq(ra.provider, rb.provider);
            boolean sameVersion = eq(ra.requestedVersion, rb.requestedVersion);
            boolean sameStatus = eq(ra.status, rb.status);
            if (!sameProvider || !sameStatus) {
                RefChange c = new RefChange();
                c.symbol = ra.symbolName;
                c.referencedFrom = ra.unit;
                c.providerBefore = ra.provider;
                c.providerAfter = rb.provider;
                c.beforeVersion = ra.requestedVersion;
                c.afterVersion = rb.requestedVersion;
                c.providerChanged = !sameProvider;
                c.addressOnly = sameProvider && !sameStatus == false && sameProvider;
                if (!sameProvider) {
                    c.note = "引用由 " + na(ra.provider) + " 改为 " + na(rb.provider);
                    out.changes.add(c);
                    out.providerSwitchCount++;
                } else {
                    out.statusChanges.add(e.getKey() + ": " + ra.status + " -> " + rb.status);
                }
            } else if (sameProvider && valueDiffers(ra, rb)) {
                RefChange c = new RefChange();
                c.symbol = ra.symbolName;
                c.referencedFrom = ra.unit;
                c.providerBefore = ra.provider;
                c.providerAfter = rb.provider;
                c.providerChanged = false;
                c.addressOnly = true;
                c.note = "提供者未变（" + ra.provider + "），仅绑定地址/尺寸发生变化";
                out.changes.add(c);
                out.addressOnlyCount++;
            }
        }
        for (String k : bm.keySet()) if (!am.containsKey(k)) out.onlyInB.add(k);
        return out;
    }

    private boolean valueDiffers(RelocationDecision a, RelocationDecision b) {
        return !eq(a.boundValue, b.boundValue);
    }

    private Map<String, RelocationDecision> index(LinkResult r) {
        Map<String, RelocationDecision> m = new LinkedHashMap<>();
        for (RelocationDecision d : r.relocations) {
            m.put(d.unit + "|" + d.targetSection + "+" + d.offset + "|" + d.symbolName, d);
        }
        return m;
    }

    private boolean eq(Object x, Object y) {
        return x == null ? y == null : x.equals(y);
    }

    private String na(String s) {
        return s == null ? "(无)" : s;
    }
}
