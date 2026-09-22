package com.symbolcourt.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symbolcourt.model.Policy;
import com.symbolcourt.model.ResolutionResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class SchemeRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SchemeRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record StoredScheme(String id, String parentId, String lineageId, String name,
                               int version, Policy policy, String inputSpecJson,
                               String resultJson, Instant createdAt) {
    }

    private static final RowMapper<StoredScheme> MAPPER = (rs, n) -> new StoredScheme(
            rs.getString("id"), rs.getString("parent_id"), rs.getString("lineage_id"),
            rs.getString("name"), rs.getInt("version"),
            null, rs.getString("input_spec_json"), rs.getString("result_json"),
            Instant.parse(rs.getString("created_at")));

    public void insert(String id, String parentId, String lineageId, String name, int version,
                       Policy policy, String inputSpecJson, String resultJson, Instant now) {
        jdbc.update("INSERT INTO schemes(id,parent_id,lineage_id,name,version,policy_json,"
                        + "input_spec_json,result_json,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id, parentId, lineageId, name, version, write(policy), inputSpecJson,
                resultJson, now.toString());
    }

    public void attachFiles(String schemeId, List<FileEntry> files) {
        int ordinal = 0;
        for (FileEntry f : files) {
            jdbc.update("INSERT INTO scheme_files(scheme_id,ordinal,file_id,display_name,kind)"
                    + " VALUES (?,?,?,?,?)", schemeId, ordinal++, f.fileId(), f.displayName(),
                    f.kind());
        }
    }

    public List<FileEntry> files(String schemeId) {
        return jdbc.query("SELECT file_id, display_name, kind FROM scheme_files "
                        + "WHERE scheme_id=? ORDER BY ordinal",
                (rs, n) -> new FileEntry(rs.getString(1), rs.getString(2), rs.getString(3)),
                schemeId);
    }

    public StoredScheme find(String id) {
        List<StoredScheme> list = jdbc.query(
                "SELECT * FROM schemes WHERE id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public StoredScheme latestInLineage(String lineageId) {
        List<StoredScheme> list = jdbc.query(
                "SELECT * FROM schemes WHERE lineage_id=? ORDER BY version DESC LIMIT 1",
                MAPPER, lineageId);
        return list.isEmpty() ? null : list.get(0);
    }

    public int nextVersion(String lineageId) {
        StoredScheme s = latestInLineage(lineageId);
        return s == null ? 1 : s.version() + 1;
    }

    public List<StoredScheme> recent() {
        return jdbc.query("SELECT * FROM schemes ORDER BY created_at DESC, version DESC LIMIT 100",
                MAPPER);
    }

    public List<StoredScheme> lineage(String lineageId) {
        return jdbc.query(
                "SELECT * FROM schemes WHERE lineage_id=? ORDER BY version", MAPPER, lineageId);
    }

    public Policy policyOf(StoredScheme s) {
        try {
            return mapper.readValue(jdbc.queryForObject(
                    "SELECT policy_json FROM schemes WHERE id=?", String.class, s.id()),
                    Policy.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public ResolutionResult resultOf(StoredScheme s) {
        try {
            String json = jdbc.queryForObject(
                    "SELECT result_json FROM schemes WHERE id=?", String.class, s.id());
            if (json == null) {
                return null;
            }
            return mapper.readValue(json, ResolutionResult.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record FileEntry(String fileId, String displayName, String kind) {
    }
}
