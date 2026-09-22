package court.symbol.db;

import court.symbol.ar.Archive;
import court.symbol.elf.Elf;
import court.symbol.elf.ElfException;
import court.symbol.elf.ElfFile;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;

@Repository
public class ObjectRepository {

    private final SchemaInitializer db;

    public ObjectRepository(SchemaInitializer db) {
        this.db = db;
    }

    public record Stored(String sha256, String kind, boolean reused, String note) {
    }

    /**
     * Stores raw bytes (never mutated by the court) and sniffs ELF vs ar.
     * Corrupt content is still stored (read-only preservation), but the
     * returned note explains that it will be isolated during resolution.
     */
    public Stored store(String filename, byte[] content) {
        String sha = sha256(content);
        String kind = sniff(content);
        String note = null;
        if ("ELF".equals(kind)) {
            try {
                ElfFile ef = ElfFile.parse(content);
                note = (ef.relocatable() ? "ET_REL" : ef.sharedObject() ? "ET_DYN"
                        : "ET_" + ef.header().type())
                        + " " + ef.header().machineName()
                        + ", " + ef.symbols().size() + " 符号";
            } catch (ElfException e) {
                note = "损坏 ELF, 裁决时仅隔离自身: " + e.getMessage();
            }
        } else if ("ARCHIVE".equals(kind)) {
            try {
                Archive ar = Archive.parse(content);
                note = "ar 归档, " + ar.members().size() + " 成员";
            } catch (RuntimeException e) {
                note = "损坏归档, 裁决时仅隔离自身: " + e.getMessage();
            }
        } else {
            note = "无法识别为 ELF/ar, 裁决时隔离";
        }
        boolean reused;
        try (var c = db.open(); PreparedStatement q = c.prepareStatement(
                "SELECT 1 FROM binary_object WHERE sha256=?")) {
            q.setString(1, sha);
            reused = q.executeQuery().next();
            if (!reused) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO binary_object(sha256,filename,kind,size,content,stored_at)"
                                + " VALUES(?,?,?,?,?,?)")) {
                    ins.setString(1, sha);
                    ins.setString(2, filename);
                    ins.setString(3, kind);
                    ins.setLong(4, content.length);
                    ins.setBytes(5, content);
                    ins.setLong(6, System.currentTimeMillis());
                    ins.executeUpdate();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("写入对象失败: " + e.getMessage(), e);
        }
        return new Stored(sha, kind, reused, note);
    }

    public ObjectRecord fetch(String sha) {
        try (var c = db.open(); PreparedStatement ps = c.prepareStatement(
                "SELECT filename,kind,size,content FROM binary_object WHERE sha256=?")) {
            ps.setString(1, sha);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return new ObjectRecord(sha, rs.getString(1), rs.getString(2),
                    rs.getLong(3), rs.getBytes(4));
        } catch (Exception e) {
            throw new IllegalStateException("读取对象失败: " + e.getMessage(), e);
        }
    }

    public java.util.List<Stored> list() {
        try (var c = db.open(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT sha256, kind, filename FROM binary_object ORDER BY stored_at")) {
            java.util.List<Stored> out = new java.util.ArrayList<>();
            while (rs.next()) {
                out.add(new Stored(rs.getString(1), rs.getString(2), true,
                        rs.getString(3)));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String sniff(byte[] data) {
        if (data.length >= 4 && data[0] == 0x7f && data[1] == 'E' && data[2] == 'L'
                && data[3] == 'F') {
            return "ELF";
        }
        if (data.length >= 8 && new String(data, 0, 8).equals("!<arch>\n")) {
            return "ARCHIVE";
        }
        return "UNKNOWN";
    }

    public static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
