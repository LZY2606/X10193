package com.symtribunal.store;

import com.symtribunal.elf.Elf;
import com.symtribunal.elf.ElfParser;
import com.symtribunal.elf.ElfParseException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class BinaryStore {

    private final JdbcTemplate jdbc;
    private final ElfParser parser;

    public BinaryStore(JdbcTemplate jdbc, ElfParser parser) {
        this.jdbc = jdbc;
        this.parser = parser;
    }

    private static final RowMapper<BinaryRecord> META_MAPPER = (rs, n) -> {
        BinaryRecord record = new BinaryRecord();
        record.id = rs.getString("id");
        record.fileName = rs.getString("file_name");
        record.sha256 = rs.getString("sha256");
        record.size = rs.getLong("size");
        record.kind = rs.getString("kind");
        record.machine = rs.getString("machine");
        record.error = rs.getString("error");
        record.uploadedAt = rs.getString("uploaded_at");
        return record;
    };

    public BinaryRecord save(String fileName, byte[] data) {
        String sha256 = sha256(data);
        List<BinaryRecord> existing = jdbc.query(
                "SELECT * FROM binary WHERE sha256 = ? AND file_name = ?",
                META_MAPPER, sha256, fileName);
        if (!existing.isEmpty()) {
            BinaryRecord record = existing.get(0);
            record.data = jdbc.queryForObject(
                    "SELECT bytes FROM binary WHERE id = ?", byte[].class, record.id);
            return record;
        }
        String id = "bin_" + sha256.substring(0, 16);
        String kind = "?";
        String machine = "?";
        String error = null;
        boolean archive = data.length >= 8 && data[0] == '!' && data[1] == '<';
        if (archive) {
            kind = "ARCHIVE";
        } else {
            try {
                var image = parser.parse(data, fileName);
                kind = Elf.typeOfObject(image.type);
                machine = Elf.machineName(image.machine);
            } catch (ElfParseException e) {
                error = e.getMessage();
            }
        }
        jdbc.update("INSERT INTO binary(id,file_name,sha256,size,kind,machine,error,uploaded_at,bytes)"
                        + " VALUES(?,?,?,?,?,?,?,?,?)",
                id, fileName, sha256, data.length, kind, machine, error,
                Instant.now().toString(), data);
        BinaryRecord record = new BinaryRecord();
        record.id = id;
        record.fileName = fileName;
        record.sha256 = sha256;
        record.size = data.length;
        record.kind = kind;
        record.machine = machine;
        record.error = error;
        record.uploadedAt = Instant.now().toString();
        record.data = data;
        return record;
    }

    public List<BinaryRecord> list() {
        return jdbc.query("SELECT * FROM binary ORDER BY uploaded_at", META_MAPPER);
    }

    public BinaryRecord get(String id) {
        List<BinaryRecord> records = jdbc.query("SELECT * FROM binary WHERE id = ?",
                (rs, n) -> {
                    BinaryRecord record = META_MAPPER.mapRow(rs, n);
                    record.data = rs.getBytes("bytes");
                    return record;
                }, id);
        return records.isEmpty() ? null : records.get(0);
    }

    static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
