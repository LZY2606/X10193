package court.symbol.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;

@Component
public class DatabaseInitializer {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        new File("data").mkdirs();
        jdbcTemplate.execute("""
                create table if not exists binaries (
                  id text primary key,
                  file_name text not null,
                  content_hash text not null unique,
                  storage_path text not null,
                  parse_status text not null,
                  parse_error text,
                  created_at text not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists schemes (
                  id text primary key,
                  name text not null,
                  policy text not null,
                  payload text not null,
                  load_order text not null,
                  created_at text not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists scheme_binaries (
                  scheme_id text not null,
                  binary_id text not null,
                  load_order integer not null,
                  primary key(scheme_id, binary_id)
                )
                """);
    }
}
