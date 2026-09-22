package com.court.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Semantic comparison of two plan versions.
 *
 * <p>For every reference (source file + symbol + required version) it tells
 * whether the provider changed (semantically meaningful), only the assigned
 * address changed (same provider, different layout), the resolution status
 * changed, or nothing changed.
 */
@Service
public class PlanDiffService {
    private final ObjectMapper mapper;

    public PlanDiffService(ObjectMapper mapper) { this.mapper = mapper; }

    public Map<String, Object> diff(String jsonA, String versionA,
                                    String jsonB, String versionB) {
        JsonNode a;
        JsonNode b;
        try {
            a = mapper.readTree(jsonA);
            b = mapper.readTree(jsonB);
        } catch (Exception e) {
            throw new IllegalArgumentException("cannot parse stored result", e);
        }
        Map<String, JsonNode> mapA = index(a.withArray("resolutions"));
        Map<String, JsonNode> mapB = index(b.withArray("resolutions"));

        List<Map<String, Object>> providerSwitched = new ArrayList<>();
        List<Map<String, Object>> addressOnly = new ArrayList<>();
        List<Map<String, Object>> statusChanged = new ArrayList<>();
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> unchanged = new ArrayList<>();

        for (Map.Entry<String, JsonNode> e : mapA.entrySet()) {
            JsonNode ra = e.getValue();
            JsonNode rb = mapB.get(e.getKey());
            if (rb == null) {
                removed.add(Map.of("reference", e.getKey(), "status", text(ra, "status"),
                        "provider", providerKey(ra)));
                continue;
            }
            String pa = providerKey(ra);
            String pb = providerKey(rb);
            String sa = text(ra, "status");
            String sb = text(rb, "status");
            long addrA = ra.path("resolvedAddress").asLong(0);
            long addrB = rb.path("resolvedAddress").asLong(0);

            Map<String, Object> base = new LinkedHashMap<>();
            base.put("reference", e.getKey());
            base.put("from", pa);
            base.put("to", pb);

            if (!Objects.equals(sa, sb)) {
                base.put("statusFrom", sa);
                base.put("statusTo", sb);
                statusChanged.add(base);
            } else if (!Objects.equals(pa, pb)) {
                providerSwitched.add(base);
            } else if (pa != null && addrA != addrB) {
                Map<String, Object> x = new LinkedHashMap<>(base);
                x.put("addressFrom", addrA);
                x.put("addressTo", addrB);
                addressOnly.add(x);
            } else {
                unchanged.add(Map.of("reference", e.getKey()));
            }
        }
        for (Map.Entry<String, JsonNode> e : mapB.entrySet()) {
            if (!mapA.containsKey(e.getKey())) {
                added.add(Map.of("reference", e.getKey(),
                        "status", text(e.getValue(), "status"),
                        "provider", providerKey(e.getValue())));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("versionA", versionA);
        out.put("versionB", versionB);
        out.put("providerSwitched", providerSwitched);
        out.put("addressOnlyChanged", addressOnly);
        out.put("statusChanged", statusChanged);
        out.put("referencesAdded", added);
        out.put("referencesRemoved", removed);
        out.put("unchangedCount", unchanged.size());
        out.put("summary", Map.of(
                "providerSwitched", providerSwitched.size(),
                "addressOnly", addressOnly.size(),
                "statusChanged", statusChanged.size(),
                "added", added.size(),
                "removed", removed.size(),
                "unchanged", unchanged.size()));
        return out;
    }

    private Map<String, JsonNode> index(Iterable<JsonNode> resolutions) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode n : resolutions) {
            String key = text(n, "source") + "#" + text(n, "symbol")
                    + "#" + (n.hasNonNull("requiredVersion") ? text(n, "requiredVersion") : "-")
                    + "#" + n.path("requiredVersionNonDefault").asBoolean(false);
            map.put(key, n);
        }
        return map;
    }

    private String providerKey(JsonNode resolution) {
        JsonNode w = resolution.get("winner");
        if (w == null || w.isNull()) return null;
        return text(w, "file") + "#" + text(w, "symbol")
                + (w.hasNonNull("version") ? "#" + text(w, "version") : "");
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
