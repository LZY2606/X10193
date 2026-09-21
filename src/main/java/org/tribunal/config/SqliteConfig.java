package org.tribunal.config;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

@Configuration
public class SqliteConfig {
    @Bean
    public DataSource dataSource(@Value("${TRIBUNAL_DB:./data/tribunal.sqlite}") String databasePath) {
        try {
            Path path = Path.of(databasePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + path.toAbsolutePath());
            return dataSource;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot initialize SQLite database", ex);
        }
    }
}
