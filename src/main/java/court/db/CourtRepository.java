package court.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Repository
public class CourtRepository {

    private final JdbcTemplate jdbc;

    public CourtRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Content-addressed storage: identical bytes never duplicate; raw input kept verbatim. */
    public String putObject(String name, byte[] data, String kind, String corruptReason) {
        String sha = sha256(data);
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM objects WHERE sha = ?", Integer.class, sha);
        if (existing == null || existing == 0) {
            jdbc.update("INSERT INTO objects(sha, name, kind, size, corrupt_reason, data) "
                            + "VALUES (?,?,?,?,?,?)",
                    sha, name, kind, data.length, corruptReason, data);
        }
        return sha;
    }

    public List<Map<String, Object>> listObjects() {
        return jdbc.queryForList(
                "SELECT sha, name, kind, size, corrupt_reason FROM objects ORDER BY rowid DESC");
    }

    public byte[] objectData(String sha) {
        List<byte[]> rows = jdbc.query(
                "SELECT data FROM objects WHERE sha = ?",
                (RowMapper<byte[]>) (rs, i) -> rs.getBytes(1), sha);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String objectName(String sha) {
        List<String> rows = jdbc.query("SELECT name FROM objects WHERE sha = ?",
                (RowMapper<String>) (rs, i) -> rs.getString(1), sha);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long createScheme(String label, String policyJson, String resultJson,
                             List<String> orderedShas) {
        jdbc.update("INSERT INTO schemes(label, policy_json, result_json) VALUES (?,?,?)",
                label, policyJson, resultJson);
        long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        for (int i = 0; i < orderedShas.size(); i++) {
            jdbc.update("INSERT INTO scheme_inputs(scheme_id, position, object_sha) VALUES (?,?,?)",
                    id, i, orderedShas.get(i));
        }
        return id;
    }

    public Map<String, Object> schemeRow(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, label, policy_json, result_json, created_at FROM schemes WHERE id = ?",
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listSchemes() {
        return jdbc.queryForList(
                "SELECT id, label, policy_json, created_at FROM schemes ORDER BY id DESC");
    }

    public List<String> schemeShas(long id) {
        return jdbc.query("SELECT object_sha FROM scheme_inputs WHERE scheme_id=? ORDER BY position",
                (RowMapper<String>) (rs, i) -> rs.getString(1), id);
    }

    public long saveDiff(long a, long b, String diffJson) {
        jdbc.update("INSERT INTO diffs(scheme_a, scheme_b, diff_json) VALUES (?,?,?)",
                a, b, diffJson);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public String diff(long a, long b) {
        List<String> rows = jdbc.query(
                "SELECT diff_json FROM diffs WHERE scheme_a=? AND scheme_b=?",
                (RowMapper<String>) (rs, i) -> rs.getString(1), a, b);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
