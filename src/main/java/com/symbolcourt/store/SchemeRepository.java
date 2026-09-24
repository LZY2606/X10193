package com.symbolcourt.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class SchemeRepository {

    private final JdbcTemplate jdbc;

    public SchemeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertArtifact(String name, String kind, byte[] content, String sha256) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO artifacts(name, kind, size, sha256, content, uploaded_at) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, kind);
            ps.setInt(3, content.length);
            ps.setString(4, sha256);
            ps.setBytes(5, content);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            return ps;
        }, kh);
        return kh.getKey() == null ? -1L : kh.getKey().longValue();
    }

    public byte[] artifactContent(long id) {
        List<byte[]> rows = jdbc.query("SELECT content FROM artifacts WHERE id=?",
                (rs, n) -> rs.getBytes(1), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> artifactMeta(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,name,kind,size,sha256,uploaded_at FROM artifacts WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listArtifacts() {
        return jdbc.queryForList(
                "SELECT id,name,kind,size,sha256,uploaded_at FROM artifacts ORDER BY id DESC");
    }

    public long insertScheme(String name, String policyId, String loadOrder, String resultJson,
                             String parsedJson, List<Long> artifactIds) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO schemes(name, policy_id, load_order, result_json, parsed_json, created_at)"
                            + " VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, policyId);
            ps.setString(3, loadOrder);
            ps.setString(4, resultJson);
            ps.setString(5, parsedJson);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            return ps;
        }, kh);
        long schemeId = kh.getKey().longValue();
        int pos = 0;
        for (Long aid : artifactIds) {
            jdbc.update("INSERT INTO scheme_artifacts(scheme_id, artifact_id, position) VALUES(?,?,?)",
                    schemeId, aid, pos++);
        }
        return schemeId;
    }

    public Map<String, Object> schemeRow(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,name,policy_id,load_order,result_json,parsed_json,created_at FROM schemes WHERE id=?",
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listSchemes() {
        return jdbc.queryForList(
                "SELECT id,name,policy_id,load_order,created_at FROM schemes ORDER BY id DESC");
    }

    public List<Map<String, Object>> schemeArtifacts(long schemeId) {
        return jdbc.queryForList(
                "SELECT a.id, a.name, a.kind, a.size, a.sha256, sa.position FROM scheme_artifacts sa"
                        + " JOIN artifacts a ON a.id = sa.artifact_id WHERE sa.scheme_id=?"
                        + " ORDER BY sa.position", schemeId);
    }
}
