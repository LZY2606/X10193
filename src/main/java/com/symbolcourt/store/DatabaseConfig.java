package com.symbolcourt.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class DatabaseConfig {

    @Value("${symbolcourt.db.path:symbol-court.db}")
    private String dbPath;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + dbPath);
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource ds) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        initialize(jdbc);
        return jdbc;
    }

    private void initialize(JdbcTemplate jdbc) {
        try (InputStream in = new ClassPathResource("db/schema.sql").getInputStream()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) jdbc.execute(trimmed);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to initialize database schema", e);
        }
    }
}
