package court.symbol.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the SQLite database file and its schema. Reports are stored as immutable
 * JSON snapshots per plan version, so changing load order/platform never mutates
 * a previously produced adjudication.
 */
@Component
public class SchemaInitializer {

    private final String url;

    public SchemaInitializer(@Value("${symbolcourt.db:./data/symbol-court.db}") String dbPath) {
        try {
            Path p = Path.of(dbPath);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            this.url = "jdbc:sqlite:" + p.toAbsolutePath();
            try (Connection c = open(); Statement st = c.createStatement()) {
                st.executeUpdate("PRAGMA journal_mode=WAL");
                st.executeUpdate("PRAGMA foreign_keys=ON");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS binary_object (
                            sha256 TEXT PRIMARY KEY,
                            filename TEXT NOT NULL,
                            kind TEXT NOT NULL,
                            size INTEGER NOT NULL,
                            content BLOB NOT NULL,
                            stored_at INTEGER NOT NULL
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS plan (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            target_platform TEXT NOT NULL,
                            created_at INTEGER NOT NULL
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS plan_version (
                            plan_id TEXT NOT NULL,
                            version INTEGER NOT NULL,
                            link_model TEXT NOT NULL,
                            tie_policy TEXT NOT NULL,
                            allow_interposition INTEGER NOT NULL,
                            spec_json TEXT NOT NULL,
                            report_json TEXT,
                            created_at INTEGER NOT NULL,
                            PRIMARY KEY (plan_id, version),
                            FOREIGN KEY (plan_id) REFERENCES plan(id)
                        )""");
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化 SQLite: " + e.getMessage(), e);
        }
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url);
    }
}
