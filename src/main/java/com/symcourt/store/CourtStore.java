package com.symcourt.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** SQLite persistence. Original binaries are stored once and never mutated. */
@Component
public class CourtStore implements AutoCloseable {

    private final Connection conn;

    public CourtStore(@Value("${symcourt.db:symcourt.db}") String path) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS object(
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      name TEXT NOT NULL,
                      kind TEXT NOT NULL,
                      sha TEXT NOT NULL,
                      size INTEGER NOT NULL,
                      data BLOB,
                      error TEXT,
                      created_at TEXT NOT NULL DEFAULT (datetime('now'))
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS scheme(
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      name TEXT NOT NULL,
                      policy TEXT NOT NULL,
                      objects_json TEXT NOT NULL,
                      result_json TEXT NOT NULL,
                      created_at TEXT NOT NULL DEFAULT (datetime('now'))
                    )""");
        }
    }

    public record ObjectRow(long id, String name, String kind, String sha, long size,
                            byte[] data, String error, String createdAt) { }

    public record SchemeRow(long id, String name, String policy, String objectsJson,
                            String resultJson, String createdAt) { }

    public synchronized long insertObject(String name, String kind, String sha, byte[] data, String error) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO object(name,kind,sha,size,data,error) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, kind);
            ps.setString(3, sha);
            ps.setLong(4, data == null ? 0 : data.length);
            ps.setBytes(5, data);
            ps.setString(6, error);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized List<ObjectRow> listObjects() {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id,name,kind,sha,size,error,created_at FROM object ORDER BY id")) {
            List<ObjectRow> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new ObjectRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), null, rs.getString(6), rs.getString(7)));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized ObjectRow getObject(long id) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id,name,kind,sha,size,data,error,created_at FROM object WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ObjectRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getBytes(6), rs.getString(7), rs.getString(8));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized long insertScheme(String name, String policy, String objectsJson, String resultJson) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO scheme(name,policy,objects_json,result_json) VALUES(?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, policy);
            ps.setString(3, objectsJson);
            ps.setString(4, resultJson);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized List<SchemeRow> listSchemes() {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id,name,policy,objects_json,created_at FROM scheme ORDER BY id")) {
            List<SchemeRow> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new SchemeRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), null, rs.getString(5)));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized SchemeRow getScheme(long id) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id,name,policy,objects_json,result_json,created_at FROM scheme WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new SchemeRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
