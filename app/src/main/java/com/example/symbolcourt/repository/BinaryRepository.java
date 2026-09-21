package com.example.symbolcourt.repository;

import com.example.symbolcourt.domain.Models.InputBinary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class BinaryRepository {
    private final JdbcTemplate jdbc;

    public BinaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InputBinary save(String displayName, String path, byte[] content, InputBinary.Kind kind) {
        InputBinary binary = new InputBinary(UUID.randomUUID().toString(), displayName, path, content, kind);
        jdbc.update("INSERT INTO binaries(id, display_name, path, kind, content, created_at) VALUES (?,?,?,?,?,?)",
                binary.id(), binary.displayName(), binary.path(), binary.kind().name(), binary.content(),
                Instant.now().toString());
        return binary;
    }

    public InputBinary findById(String id) {
        return jdbc.queryForObject("SELECT id, display_name, path, content, kind FROM binaries WHERE id = ?",
                (rs, rowNum) -> new InputBinary(rs.getString("id"), rs.getString("display_name"),
                        rs.getString("path"), rs.getBytes("content"),
                        InputBinary.Kind.valueOf(rs.getString("kind"))), id);
    }

    public List<InputBinary> findAll() {
        return jdbc.query("SELECT id, display_name, path, content, kind FROM binaries ORDER BY created_at DESC",
                (rs, rowNum) -> new InputBinary(rs.getString("id"), rs.getString("display_name"),
                        rs.getString("path"), rs.getBytes("content"),
                        InputBinary.Kind.valueOf(rs.getString("kind"))));
    }
}
