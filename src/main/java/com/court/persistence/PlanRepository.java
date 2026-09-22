package com.court.persistence;

import com.court.resolution.LoadEntry;
import com.court.resolution.Policies;
import com.court.resolution.ResolutionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Stores immutable plan versions. Changing load order or platform policy
 * produces a new version tag; a stored result is never rewritten, so old
 * adjudications never drift.
 */
@Repository
public class PlanRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PlanRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record StoredPlan(long id, String name, String versionTag, String resultJson) { }

    public String inputFingerprint(List<LoadEntry> entries) {
        MessageDigest md = sha();
        for (LoadEntry e : entries) {
            md.update(e.label.getBytes());
            md.update((byte) 0);
            md.update(e.kind.name().getBytes());
            md.update((byte) 0);
            md.update(e.content);
            md.update((byte) 0);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    public String orderFingerprint(List<LoadEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (LoadEntry e : entries) sb.append(e.order).append(':').append(e.label).append(';');
        return shaHex(sb.toString());
    }

    public String versionTag(List<LoadEntry> entries, Policies policies) {
        String raw = orderFingerprint(entries) + "|" + policies.signature();
        return "v" + shaHex(raw).substring(0, 12);
    }

    public StoredPlan save(String name, String versionTag, List<LoadEntry> entries,
                           Policies policies, ResolutionResult result) {
        StoredPlan existing = find(name, versionTag);
        String json;
        try {
            json = mapper.writeValueAsString(new ResultView(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize result", e);
        }
        if (existing != null) {
            return existing;
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                INSERT INTO plans(name, version_tag, input_fingerprint, order_fingerprint,
                                  policy_signature, result_json)
                VALUES (?,?,?,?,?,?)
                """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, versionTag);
            ps.setString(3, inputFingerprint(entries));
            ps.setString(4, orderFingerprint(entries));
            ps.setString(5, policies.signature());
            ps.setString(6, json);
            return ps;
        }, kh);
        long id = kh.getKey() == null ? -1 : kh.getKey().longValue();
        return new StoredPlan(id, name, versionTag, json);
    }

    public StoredPlan find(String name, String versionTag) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, version_tag, result_json FROM plans WHERE name=? AND version_tag=?",
                name, versionTag);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        return new StoredPlan(((Number) row.get("id")).longValue(),
                (String) row.get("name"), (String) row.get("version_tag"),
                (String) row.get("result_json"));
    }

    public List<StoredPlan> listVersions(String name) {
        return jdbc.queryForList(
                "SELECT id, name, version_tag, result_json FROM plans WHERE name=? ORDER BY id", name)
                .stream().map(row -> new StoredPlan(((Number) row.get("id")).longValue(),
                        (String) row.get("name"), (String) row.get("version_tag"),
                        (String) row.get("result_json"))).toList();
    }

    public List<StoredPlan> listAll() {
        return jdbc.queryForList(
                "SELECT id, name, version_tag, result_json FROM plans ORDER BY id DESC")
                .stream().map(row -> new StoredPlan(((Number) row.get("id")).longValue(),
                        (String) row.get("name"), (String) row.get("view_tag") != null
                                ? (String) row.get("version_tag") : (String) row.get("version_tag"),
                        (String) row.get("result_json"))).toList();
    }

    private MessageDigest sha() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private String shaHex(String s) {
        MessageDigest md = sha();
        return HexFormat.of().formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** View used for JSON serialization. */
    public record ResultView(ResolutionResult result) { }
}
