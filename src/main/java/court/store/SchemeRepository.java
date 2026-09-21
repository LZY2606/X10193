package court.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import court.domain.Scheme;
import court.domain.StoredInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class SchemeRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SchemeRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private static final RowMapper<Scheme> SCHEME_MAPPER = (rs, n) -> {
        Scheme s = new Scheme();
        s.id = rs.getLong("id");
        s.name = rs.getString("name");
        s.version = rs.getInt("version");
        long p = rs.getLong("parent_id");
        s.parentId = rs.wasNull() ? null : p;
        s.reportJson = rs.getString("report_json");
        s.inputHashes = rs.getString("input_hashes");
        s.createdAt = rs.getString("created_at");
        String policy = rs.getString("policy_json");
        try {
            s.policy = new ObjectMapper().readValue(policy, court.link.LinkPolicy.class);
            s.loadOrder = new ObjectMapper().readValue(rs.getString("load_order"),
                    new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return s;
    };

    public long insertScheme(Scheme s, String reportJson) {
        try {
            String policy = mapper.writeValueAsString(s.policy);
            String order = mapper.writeValueAsString(s.loadOrder);
            KeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO scheme(name, version, parent_id, policy_json, load_order, report_json, input_hashes)"
                                + " VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, s.name);
                ps.setInt(2, s.version);
                if (s.parentId == null) ps.setNull(3, java.sql.Types.INTEGER);
                else ps.setLong(3, s.parentId);
                ps.setString(4, policy);
                ps.setString(5, order);
                ps.setString(6, reportJson);
                ps.setString(7, s.inputHashes);
                return ps;
            }, kh);
            return kh.getKey().longValue();
        } catch (Exception e) {
            throw new IllegalStateException("failed to persist scheme", e);
        }
    }

    public void addInput(long schemeId, int seq, String fileName, String kind, long size,
                         String sha, byte[] data) {
        jdbc.update("INSERT INTO input_object(scheme_id, seq, file_name, kind, size, sha256, data)"
                        + " VALUES(?,?,?,?,?,?,?)",
                schemeId, seq, fileName, kind, size, sha, data);
    }

    public List<Scheme> listSchemes() {
        return jdbc.query("SELECT * FROM scheme ORDER BY id DESC", SCHEME_MAPPER);
    }

    public Scheme getScheme(long id) {
        List<Scheme> r = jdbc.query("SELECT * FROM scheme WHERE id=?", SCHEME_MAPPER, id);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<StoredInput> inputs(long schemeId) {
        return jdbc.query("SELECT * FROM input_object WHERE scheme_id=? ORDER BY seq",
                (rs, n) -> {
                    StoredInput in = new StoredInput();
                    in.id = rs.getLong("id");
                    in.schemeId = rs.getLong("scheme_id");
                    in.seq = rs.getInt("seq");
                    in.fileName = rs.getString("file_name");
                    in.kind = rs.getString("kind");
                    in.size = rs.getLong("size");
                    in.sha256 = rs.getString("sha256");
                    in.data = rs.getBytes("data");
                    return in;
                }, schemeId);
    }

    public int nextVersion(String baseName) {
        Integer v = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version),0)+1 FROM scheme WHERE name=?", Integer.class, baseName);
        return v == null ? 1 : v;
    }

    public void setMeta(long schemeId, String summary) {
        jdbc.update("INSERT INTO scheme_meta(scheme_id, summary) VALUES(?,?) "
                + "ON CONFLICT(scheme_id) DO UPDATE SET summary=excluded.summary", schemeId, summary);
    }
}
