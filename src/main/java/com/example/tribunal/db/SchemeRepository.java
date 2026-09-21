package com.example.tribunal.db;

import com.example.tribunal.core.model.TribunalModels.StoredScheme;
import com.example.tribunal.core.model.TribunalModels.Scheme;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class SchemeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SchemeRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void save(Scheme scheme) {
        try {
            jdbc.update("INSERT INTO schemes(id, name, created_at, policy_name, load_order_seed, json) VALUES (?,?,?,?,?,?)",
                    scheme.id(), scheme.name(), scheme.createdAt(), scheme.policyName(), scheme.loadOrderSeed(),
                    objectMapper.writeValueAsString(scheme));
        } catch (Exception e) {
            throw new IllegalStateException("failed to persist scheme", e);
        }
    }

    public Scheme find(String id) {
        String json = jdbc.queryForObject("SELECT json FROM schemes WHERE id = ?", String.class, id);
        return read(json);
    }

    public List<StoredScheme> list() {
        return jdbc.query("SELECT id, name, created_at, policy_name, load_order_seed, json FROM schemes ORDER BY created_at DESC",
                (rs, rowNum) -> new StoredScheme(rs.getString("id"), rs.getString("name"), rs.getString("created_at"),
                        rs.getString("policy_name"), rs.getInt("load_order_seed"), rs.getString("json")));
    }

    private Scheme read(String json) {
        try {
            return objectMapper.readValue(json, Scheme.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read stored scheme", e);
        }
    }
}
