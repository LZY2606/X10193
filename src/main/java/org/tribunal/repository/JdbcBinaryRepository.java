package org.tribunal.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tribunal.model.ArchiveMember;
import org.tribunal.model.ParsedBinary;

@Repository
public class JdbcBinaryRepository implements BinaryRepository {
    private final JdbcTemplate jdbcTemplate;
    private final Path storageRoot = Path.of("data", "binaries");

    public JdbcBinaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create binary storage", ex);
        }
    }

    @Override
    public ParsedBinary save(String originalName, byte[] payload, ParsedBinary parsed) {
        String sha256 = sha256(payload);
        String storedName = sha256 + ".bin";
        Path target = storageRoot.resolve(storedName);
        try {
            if (!Files.exists(target)) {
                Path temp = Files.createTempFile(storageRoot, "upload-", ".tmp");
                Files.write(temp, payload);
                Files.move(temp, target);
            }
            setReadOnly(target);
        } catch (IOException ex) {
            try {
                if (!Files.exists(target)) {
                    Files.write(target, payload);
                }
                setReadOnly(target);
            } catch (IOException ignored) {
                throw new IllegalStateException("Cannot store binary read-only", ex);
            }
        }
        jdbcTemplate.update("""
                INSERT INTO binaries(name, kind, path, sha256, size, parse_ok, parse_error)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, originalName, parsed.kind(), target.toAbsolutePath().toString(), sha256,
                payload.length, parsed.parseOk() ? 1 : 0, parsed.parseError());
        long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        for (ArchiveMember member : parsed.members()) {
            jdbcTemplate.update("""
                    INSERT INTO archive_members(binary_id, name, parse_ok, parse_error, payload)
                    VALUES (?, ?, ?, ?, ?)
                    """, id, member.name(), member.parseOk() ? 1 : 0, member.parseError(), new byte[0]);
        }
        return parsed.withId(id, target.toAbsolutePath().toString());
    }

    private void setReadOnly(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target,
                    java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ));
        } catch (UnsupportedOperationException ignored) {
            target.toFile().setReadOnly();
        }
    }

    @Override
    public Optional<ParsedBinary> findById(long id) {
        try {
            ParsedBinary binary = jdbcTemplate.queryForObject(
                    "SELECT id, name, kind, path, sha256, size, parse_ok, parse_error FROM binaries WHERE id = ?",
                    this::mapRow, id);
            return Optional.ofNullable(binary);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<ParsedBinary> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, kind, path, sha256, size, parse_ok, parse_error FROM binaries ORDER BY id",
                this::mapRow);
    }

    @Override
    public byte[] readPayload(long id) {
        ParsedBinary binary = findById(id).orElseThrow(() -> new IllegalArgumentException("Binary not found: " + id));
        try {
            return Files.readAllBytes(Path.of(binary.path()));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read stored binary", ex);
        }
    }

    private ParsedBinary mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ParsedBinary(rs.getLong("id"), rs.getString("name"), rs.getString("kind"),
                rs.getString("path"), rs.getString("sha256"), rs.getLong("size"),
                rs.getInt("parse_ok") == 1, rs.getString("parse_error"), null, List.of());
    }

    private String sha256(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
