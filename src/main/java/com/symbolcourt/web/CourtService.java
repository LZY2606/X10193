package com.symbolcourt.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symbolcourt.elf.BinaryLoader;
import com.symbolcourt.resolve.*;
import com.symbolcourt.store.SchemeRepository;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.*;

@Service
public class CourtService {

    private final SchemeRepository repo;
    private final BinaryLoader loader = new BinaryLoader();
    private final ObjectMapper mapper = new ObjectMapper();

    public CourtService(SchemeRepository repo) {
        this.repo = repo;
    }

    public long uploadArtifact(String filename, byte[] content) {
        String kind = BinaryLoader.isArMagic(content) ? "archive"
                : BinaryLoader.isElfMagic(content) ? "elf" : "unknown";
        String sha = sha256(content);
        return repo.insertArtifact(filename, kind, content, sha);
    }

    public Map<String, Object> createScheme(String name, String policyId, List<Long> orderedArtifactIds) {
        List<InputUnit> units = new ArrayList<>();
        int order = 0;
        for (Long aid : orderedArtifactIds) {
            byte[] content = repo.artifactContent(aid);
            Map<String, Object> meta = repo.artifactMeta(aid);
            String display = meta == null ? ("artifact#" + aid) : String.valueOf(meta.get("name"));
            InputUnit u = loader.load(content, display, order++);
            units.add(u);
        }
        PlatformPolicy policy = resolvePolicy(policyId);
        LinkResult result = new LinkEngine(policy).link(units);
        Map<String, Object> view = ViewDto.result(result);
        String resultJson = writeJson(view);
        String parsedJson = writeJson(Map.of("units", view.get("units")));
        String loadOrder = writeJson(orderedArtifactIds);
        long id = repo.insertScheme(name, policy.id, loadOrder, resultJson, parsedJson, orderedArtifactIds);
        Map<String, Object> out = new LinkedHashMap<>(view);
        out.put("schemeId", id);
        out.put("name", name);
        return out;
    }

    public Map<String, Object> schemeView(long id) {
        Map<String, Object> row = repo.schemeRow(id);
        if (row == null) return null;
        String json = String.valueOf(row.get("result_json"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> view = mapper.readValue(json, Map.class);
            view.put("schemeId", id);
            view.put("name", row.get("name"));
            view.put("createdAt", String.valueOf(row.get("created_at")));
            view.put("policyId", row.get("policy_id"));
            view.put("artifacts", repo.schemeArtifacts(id));
            return view;
        } catch (Exception e) {
            throw new IllegalStateException("stored result unreadable for scheme " + id, e);
        }
    }

    public Map<String, Object> compare(long a, long b) {
        Map<String, Object> va = schemeView(a);
        Map<String, Object> vb = schemeView(b);
        if (va == null || vb == null) throw new IllegalArgumentException("scheme not found");
        LinkResult ra = reconstruct(va);
        LinkResult rb = reconstruct(vb);
        SchemeDiff.Result diff = new SchemeDiff().compare(ra, rb);
        Map<String, Object> out = ViewDto.diff(diff);
        out.put("schemeA", a);
        out.put("schemeB", b);
        return out;
    }

    /**
     * Reconstruct a lightweight LinkResult view from stored JSON so that diffing
     * reads immutable snapshots rather than re-resolving (old schemes never drift).
     */
    @SuppressWarnings("unchecked")
    private LinkResult reconstruct(Map<String, Object> view) {
        LinkResult r = new LinkResult();
        for (Object ro : (List<Object>) view.getOrDefault("relocations", List.of())) {
            Map<String, Object> m = (Map<String, Object>) ro;
            RelocationDecision d = new RelocationDecision();
            d.unit = str(m.get("unit"));
            d.targetSection = str(m.get("section"));
            d.offset = m.get("offset") == null ? 0 : ((Number) m.get("offset")).longValue();
            d.symbolName = str(m.get("symbol"));
            d.provider = m.get("provider") == null ? null : str(m.get("provider"));
            d.requestedVersion = m.get("requestedVersion") == null ? null
                    : str(m.get("requestedVersion"));
            d.status = str(m.get("status"));
            Object bv = m.get("boundValue");
            d.boundValue = bv == null ? null : ((Number) bv).longValue();
            r.relocations.add(d);
        }
        return r;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public List<Map<String, Object>> listSchemes() {
        return repo.listSchemes();
    }

    public List<Map<String, Object>> listArtifacts() {
        return repo.listArtifacts();
    }

    private PlatformPolicy resolvePolicy(String id) {
        if (id == null) return PlatformPolicy.gnuLd();
        return switch (id) {
            case "gnu-ld-asneeded" -> PlatformPolicy.gnuLdAsNeeded();
            case "strict" -> PlatformPolicy.strict();
            default -> PlatformPolicy.gnuLd();
        };
    }

    public static List<Map<String, String>> policies() {
        List<Map<String, String>> out = new ArrayList<>();
        for (PlatformPolicy p : List.of(PlatformPolicy.gnuLd(), PlatformPolicy.gnuLdAsNeeded(),
                PlatformPolicy.strict())) {
            out.add(Map.of("id", p.id, "displayName", p.displayName));
        }
        return out;
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
