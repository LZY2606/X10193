package com.example.symbolcourt.repository;

import com.example.symbolcourt.domain.Models.Plan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PlanRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PlanRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Plan save(Plan plan) {
        try {
            jdbc.update("INSERT INTO plans(id, name, policy, created_at, load_order, result_json) VALUES (?,?,?,?,?,?)",
                    plan.id(), plan.name(), plan.policy(), plan.createdAt(),
                    objectMapper.writeValueAsString(plan.loadOrder()),
                    objectMapper.writeValueAsString(plan));
            return plan;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("方案序列化失败", ex);
        }
    }

    public Plan findById(String id) {
        return jdbc.queryForObject("SELECT result_json FROM plans WHERE id = ?",
                (rs, rowNum) -> read(rs.getString("result_json")), id);
    }

    public List<Plan> findAll() {
        return jdbc.query("SELECT result_json FROM plans ORDER BY created_at DESC",
                (rs, rowNum) -> read(rs.getString("result_json")));
    }

    private Plan read(String json) {
        try {
            return objectMapper.readValue(json, Plan.class);
        } catch (Exception ex) {
            throw new IllegalStateException("方案快照损坏", ex);
        }
    }
}
