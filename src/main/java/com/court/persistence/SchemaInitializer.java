package com.court.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SchemaInitializer {
    private final JdbcTemplate jdbc;

    public SchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void init() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                version_tag TEXT NOT NULL,
                input_fingerprint TEXT NOT NULL,
                order_fingerprint TEXT NOT NULL,
                policy_signature TEXT NOT NULL,
                result_json TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                UNIQUE(name, version_tag)
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS input_blobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_version_tag TEXT NOT NULL,
                label TEXT NOT NULL,
                kind TEXT NOT NULL,
                ord INTEGER NOT NULL,
                blob BLOB NOT NULL
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS plan_diffs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_name TEXT NOT NULL,
                version_a TEXT NOT NULL,
                version_b TEXT NOT NULL,
                diff_json TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """);
    }
}
