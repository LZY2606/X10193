package com.symtribunal.store;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Immutable scheme storage. A scheme captures its report verbatim, so changing load order or
 * platform policy creates a new scheme and old results never drift.
 */
@Component
public class SchemeStore {

    private final JdbcTemplate jdbc;

    public SchemeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<SchemeRecord> MAPPER = (rs, n) -> {
        SchemeRecord record = new SchemeRecord();
        record.id = rs.getString("id");
        record.name = rs.getString("name");
        record.policy = rs.getString("policy");
        record.machine = rs.getString("machine");
        record.createdAt = rs.getString("created_at");
        record.inputOrder = rs.getString("input_order");
        record.reportJson = rs.getString("report_json");
        return record;
    };

    public void save(SchemeRecord record) {
        jdbc.update("INSERT INTO scheme(id,name,policy,machine,created_at,input_order,report_json)"
                + " VALUES(?,?,?,?,?,?,?)", record.id, record.name, record.policy,
                record.machine, record.createdAt, record.inputOrder, record.reportJson);
    }

    public List<SchemeRecord> list() {
        return jdbc.query("SELECT id,name,policy,machine,created_at,input_order,report_json"
                + " FROM scheme ORDER BY created_at", MAPPER);
    }

    public SchemeRecord get(String id) {
        List<SchemeRecord> records = jdbc.query(
                "SELECT * FROM scheme WHERE id = ?", MAPPER, id);
        return records.isEmpty() ? null : records.get(0);
    }
}
