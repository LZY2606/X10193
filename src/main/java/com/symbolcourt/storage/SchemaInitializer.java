package com.symbolcourt.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SchemaInitializer {

    private final JdbcTemplate jdbc;

    public SchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS binaries (
                    file_id TEXT PRIMARY KEY,
                    original_name TEXT NOT NULL,
                    storage_path TEXT NOT NULL,
                    sha256 TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    created_at TEXT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS schemes (
                    id TEXT PRIMARY KEY,
                    parent_id TEXT,
                    lineage_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    policy_json TEXT NOT NULL,
                    input_spec_json TEXT NOT NULL,
                    result_json TEXT,
                    created_at TEXT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS scheme_files (
                    scheme_id TEXT NOT NULL,
                    ordinal INTEGER NOT NULL,
                    file_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    PRIMARY KEY (scheme_id, ordinal)
                )
                """);
    }
}
