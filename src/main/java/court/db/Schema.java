package court.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Schema {
    public Schema(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS objects (
                    sha TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    kind TEXT,
                    size INTEGER NOT NULL,
                    corrupt_reason TEXT,
                    data BLOB NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS schemes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    label TEXT NOT NULL,
                    policy_json TEXT NOT NULL,
                    result_json TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS scheme_inputs (
                    scheme_id INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    object_sha TEXT NOT NULL,
                    PRIMARY KEY (scheme_id, position)
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS diffs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scheme_a INTEGER NOT NULL,
                    scheme_b INTEGER NOT NULL,
                    diff_json TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )""");
    }
}
