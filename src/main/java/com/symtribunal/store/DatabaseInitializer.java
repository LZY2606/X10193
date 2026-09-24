package com.symtribunal.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() throws Exception {
        String home = System.getProperty("SYM_TRIBUNAL_HOME",
                System.getenv().getOrDefault("SYM_TRIBUNAL_HOME", "data"));
        Path directory = Paths.get(home, "binaries");
        Files.createDirectories(directory);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS binary (
                    id TEXT PRIMARY KEY,
                    file_name TEXT NOT NULL,
                    sha256 TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    kind TEXT,
                    machine TEXT,
                    error TEXT,
                    uploaded_at TEXT NOT NULL,
                    bytes BLOB NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS scheme (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    policy TEXT NOT NULL,
                    machine TEXT,
                    created_at TEXT NOT NULL,
                    input_order TEXT NOT NULL,
                    report_json TEXT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_scheme_created ON scheme(created_at)
                """);
    }
}
