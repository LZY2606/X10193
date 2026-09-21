package court.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CourtRepository {

    private final JdbcTemplate jdbc;

    public CourtRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // SQLite JDBC 对多语句脚本支持不稳定，逐条执行 CREATE TABLE IF NOT EXISTS
        for (String stmt : Schema.DDL.split(";")) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()) {
                jdbc.execute(trimmed);
            }
        }
    }

    public void insertUpload(UploadRow row) {
        jdbc.update("INSERT INTO upload(id,name,kind,raw,sha256,parse_error,created_at) "
                        + "VALUES(?,?,?,?,?,?,?)",
                row.id, row.name, row.kind, row.raw, row.sha256, row.parseError, row.createdAt);
    }

    public UploadRow getUpload(String id) {
        List<UploadRow> rows = jdbc.query(
                "SELECT id,name,kind,raw,sha256,parse_error,created_at FROM upload WHERE id=?",
                uploadMapper(), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<UploadRow> listUploads() {
        return jdbc.query(
                "SELECT id,name,kind,raw,sha256,parse_error,created_at FROM upload ORDER BY created_at, id",
                uploadMapper());
    }

    private RowMapper<UploadRow> uploadMapper() {
        return (rs, n) -> new UploadRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("kind"),
                rs.getBytes("raw"),
                rs.getString("sha256"),
                rs.getString("parse_error"),
                rs.getString("created_at"));
    }

    public void insertScheme(SchemeRow row) {
        jdbc.update("INSERT INTO scheme(id,name,version_no,policy,load_order_json,"
                        + "decision_json,created_at) VALUES(?,?,?,?,?,?,?)",
                row.id, row.name, row.versionNo, row.policy, row.loadOrderJson,
                row.decisionJson, row.createdAt);
        jdbc.update("DELETE FROM scheme_upload WHERE scheme_id=?", row.id);
    }

    public void linkSchemeUpload(String schemeId, String uploadId, int position) {
        jdbc.update("INSERT INTO scheme_upload(scheme_id,upload_id,position) VALUES(?,?,?)",
                schemeId, uploadId, position);
    }

    public Integer nextVersionNo(String name) {
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM scheme WHERE name=?",
                Integer.class, name);
        return n == null ? 1 : n;
    }

    public SchemeRow getScheme(String id) {
        List<SchemeRow> rows = jdbc.query(
                "SELECT id,name,version_no,policy,load_order_json,decision_json,created_at "
                        + "FROM scheme WHERE id=?", schemeMapper(), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<SchemeRow> listSchemes() {
        return jdbc.query("SELECT id,name,version_no,policy,load_order_json,decision_json,"
                + "created_at FROM scheme ORDER BY name, version_no", schemeMapper());
    }

    public List<SchemeRow> versionsOf(String name) {
        return jdbc.query("SELECT id,name,version_no,policy,load_order_json,decision_json,"
                + "created_at FROM scheme WHERE name=? ORDER BY version_no", schemeMapper(), name);
    }

    private RowMapper<SchemeRow> schemeMapper() {
        return (rs, n) -> new SchemeRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getInt("version_no"),
                rs.getString("policy"),
                rs.getString("load_order_json"),
                rs.getString("decision_json"),
                rs.getString("created_at"));
    }
}
