package com.tribunal.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 两个方案版本的语义比较：区分“换了提供者”与“仅地址变化”。 */
@Service
public class CompareService {

    private final JdbcTemplate jdbc;

    public CompareService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> compare(long a, long b) {
        Map<String, VerdictRow> va = load(a);
        Map<String, VerdictRow> vb = load(b);
        List<Map<String, Object>> providerChanged = new ArrayList<>();
        List<Map<String, Object>> addressOnly = new ArrayList<>();
        List<Map<String, Object>> onlyA = new ArrayList<>();
        List<Map<String, Object>> onlyB = new ArrayList<>();
        List<Map<String, Object>> unchanged = new ArrayList<>();

        for (Map.Entry<String, VerdictRow> e : va.entrySet()) {
            VerdictRow x = e.getValue();
            VerdictRow y = vb.get(e.getKey());
            if (y == null) {
                onlyA.add(row(e.getKey(), x, null, "仅存在于方案 A"));
                continue;
            }
            boolean sameProvider = strEq(x.chosen, y.chosen);
            if (!sameProvider) {
                providerChanged.add(row(e.getKey(), x, y, "引用换了提供者"));
            } else if (!numEq(x.chosenValue, y.chosenValue)) {
                addressOnly.add(row(e.getKey(), x, y, "提供者相同，仅地址变化"));
            } else if (!strEq(x.status, y.status)) {
                providerChanged.add(row(e.getKey(), x, y, "裁决状态变化"));
            } else {
                unchanged.add(row(e.getKey(), x, y, "一致"));
            }
        }
        for (Map.Entry<String, VerdictRow> e : vb.entrySet()) {
            if (!va.containsKey(e.getKey())) {
                onlyB.add(row(e.getKey(), null, e.getValue(), "仅存在于方案 B"));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("providerChanged", providerChanged);
        out.put("addressOnly", addressOnly);
        out.put("onlyA", onlyA);
        out.put("onlyB", onlyB);
        out.put("unchanged", unchanged);
        return out;
    }

    private Map<String, Object> row(String key, VerdictRow x, VerdictRow y, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", key);
        m.put("note", note);
        if (x != null) {
            m.put("aChosen", x.chosen);
            m.put("aValue", x.chosenValue);
            m.put("aStatus", x.status);
        }
        if (y != null) {
            m.put("bChosen", y.chosen);
            m.put("bValue", y.chosenValue);
            m.put("bStatus", y.status);
        }
        return m;
    }

    private Map<String, VerdictRow> load(long schemeId) {
        Map<String, VerdictRow> out = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT symbol_name, version, status, chosen, chosen_value FROM verdict WHERE scheme_id = ?", schemeId)) {
            String name = (String) r.get("symbol_name");
            String ver = (String) r.get("version");
            VerdictRow v = new VerdictRow();
            v.status = (String) r.get("status");
            v.chosen = (String) r.get("chosen");
            Object cv = r.get("chosen_value");
            v.chosenValue = cv == null ? null : ((Number) cv).longValue();
            out.put(ver == null ? name : name + "@" + ver, v);
        }
        return out;
    }

    private static boolean strEq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean numEq(Long a, Long b) {
        return a == null ? b == null : a.equals(b);
    }

    private static final class VerdictRow {
        String status;
        String chosen;
        Long chosenValue;
    }
}
