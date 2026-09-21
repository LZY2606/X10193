package org.tribunal.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tribunal.model.Scheme;

@Repository
public class JdbcSchemeRepository implements SchemeRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcSchemeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Scheme save(String label, String policy, List<Long> orderedBinaryIds, String resultJson) {
        String joined = orderedBinaryIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        jdbcTemplate.update("INSERT INTO schemes(label, policy, ordered_binary_ids) VALUES (?, ?, ?)",
                label, policy, joined);
        long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        jdbcTemplate.update("INSERT INTO scheme_results(scheme_id, result_json) VALUES (?, ?)", id, resultJson);
        return new Scheme(id, label, policy, List.copyOf(orderedBinaryIds), null);
    }

    @Override
    public Optional<Scheme> findById(long id) {
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT id, label, policy, ordered_binary_ids FROM schemes WHERE id = ?",
                    (rs, row) -> mapScheme(rs), id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<Scheme> findAllMetadata() {
        return jdbcTemplate.query(
                "SELECT id, label, policy, ordered_binary_ids FROM schemes ORDER BY id",
                (rs, row) -> mapScheme(rs));
    }

    @Override
    public String findResultJson(long id) {
        return jdbcTemplate.queryForObject("SELECT result_json FROM scheme_results WHERE scheme_id = ?",
                String.class, id);
    }

    private Scheme mapScheme(ResultSet rs) throws SQLException {
        List<Long> ids = Arrays.stream(rs.getString("ordered_binary_ids").split(","))
                .filter(value -> !value.isBlank()).map(Long::parseLong).toList();
        return new Scheme(rs.getLong("id"), rs.getString("label"), rs.getString("policy"), ids, null);
    }
}
