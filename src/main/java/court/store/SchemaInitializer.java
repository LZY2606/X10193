package court.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SchemaInitializer {

    private final JdbcTemplate jdbc;

    @Autowired
    public SchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void init() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS scheme (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                version INTEGER NOT NULL,
                parent_id INTEGER,
                policy_json TEXT NOT NULL,
                load_order TEXT NOT NULL,
                report_json TEXT,
                input_hashes TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS input_object (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scheme_id INTEGER NOT NULL,
                seq INTEGER NOT NULL,
                file_name TEXT NOT NULL,
                kind TEXT,
                size INTEGER,
                sha256 TEXT,
                data BLOB NOT NULL,
                FOREIGN KEY(scheme_id) REFERENCES scheme(id)
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS scheme_meta (
                scheme_id INTEGER PRIMARY KEY,
                summary TEXT,
                FOREIGN KEY(scheme_id) REFERENCES scheme(id)
            )
        """);
    }
}
