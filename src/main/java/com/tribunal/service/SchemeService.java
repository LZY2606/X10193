package com.tribunal.service;

import com.tribunal.core.AdjudicationResult;
import com.tribunal.core.Adjudicator;
import com.tribunal.core.Candidate;
import com.tribunal.core.LoadedObject;
import com.tribunal.core.Policy;
import com.tribunal.core.RelocRef;
import com.tribunal.core.ResolutionEvent;
import com.tribunal.core.Verdict;
import com.tribunal.elf.ElfFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 方案（scheme）服务：每次导入/改序/改策略都生成新的不可变方案版本，
 * 旧方案数据只读、结果不漂移。
 */
@Service
public class SchemeService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public SchemeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listSchemes() {
        return jdbc.queryForList(
                "SELECT s.id, s.name, s.created_at, s.policy_json, "
                        + "(SELECT COUNT(*) FROM verdict v WHERE v.scheme_id = s.id) AS verdicts, "
                        + "(SELECT COUNT(*) FROM scheme_object o WHERE o.scheme_id = s.id) AS objects "
                        + "FROM scheme s ORDER BY s.id DESC");
    }

    @Transactional
    public long createScheme(String name, Policy policy, List<Adjudicator.InputObject> inputs) {
        AdjudicationResult result = new Adjudicator(policy).adjudicate(inputs);

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO scheme(name, created_at, policy_json) VALUES (?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, policy.toJson());
            return ps;
        }, kh);
        long schemeId = kh.getKey().longValue();

        // 原二进制只读保存（按输入顺序）
        for (Adjudicator.InputObject in : inputs) {
            jdbc.update("INSERT INTO scheme_object(scheme_id, seq, name, kind, sha256, content, corrupt, corrupt_reason, meta)"
                            + " VALUES (?,?,?,?,?,?,?,?,?)",
                    schemeId, -1, in.name, "INPUT", sha256(in.data), in.data, 0, null, null);
        }
        // 损坏隔离记录
        for (AdjudicationResult.CorruptObject c : result.corrupt) {
            jdbc.update("INSERT INTO scheme_object(scheme_id, seq, name, kind, sha256, content, corrupt, corrupt_reason, meta)"
                            + " VALUES (?,?,?,?,?,?,?,?,?)",
                    schemeId, -1, c.name, "CORRUPT", null, null, 1, c.reason, null);
        }
        // 已加载对象（含 archive 抽取成员）及其元数据
        for (LoadedObject o : result.objects) {
            byte[] content = o.fromArchive ? null : null; // 原二进制已在 INPUT 行保存
            jdbc.update("INSERT INTO scheme_object(scheme_id, seq, name, kind, sha256, content, corrupt, corrupt_reason, meta)"
                            + " VALUES (?,?,?,?,?,?,?,?,?)",
                    schemeId, o.seq, o.name,
                    o.fromArchive ? "ARCHIVE_MEMBER" : "LOADED",
                    null, content, 0, null, metaJson(o.elf));
        }
        // 裁决 + 候选 + 重定位
        for (Verdict v : result.verdicts) {
            KeyHolder vkh = new GeneratedKeyHolder();
            Long chosenValue = chosenValue(result, v);
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO verdict(scheme_id, symbol_name, version, status, chosen, chosen_value, reason, copy_reloc)"
                                + " VALUES (?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, schemeId);
                ps.setString(2, v.symbolName);
                ps.setString(3, v.version);
                ps.setString(4, v.status.name());
                ps.setString(5, v.chosen);
                if (chosenValue != null) ps.setLong(6, chosenValue); else ps.setNull(6, java.sql.Types.INTEGER);
                ps.setString(7, v.reason);
                ps.setBoolean(8, v.copyReloc);
                return ps;
            }, vkh);
            long vid = vkh.getKey().longValue();
            for (Candidate c : v.candidates) {
                jdbc.update("INSERT INTO candidate(verdict_id, object, bind, type, visibility, version, defined, common, size, value, eliminated, reason)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        vid, c.object, c.bind, c.type, c.visibility, c.version,
                        c.defined ? 1 : 0, c.common ? 1 : 0, c.size, c.value,
                        c.eliminated ? 1 : 0, c.reason);
            }
            for (RelocRef r : v.relocations) {
                jdbc.update("INSERT INTO reloc(verdict_id, object, section, reloc_offset, type, provider)"
                                + " VALUES (?,?,?,?,?,?)",
                        vid, r.object, r.section, r.offset, r.type, r.provider);
            }
        }
        for (ResolutionEvent e : result.events) {
            jdbc.update("INSERT INTO event(scheme_id, seq, kind, message) VALUES (?,?,?,?)",
                    schemeId, e.seq, e.kind.name(), e.message);
        }
        for (AdjudicationResult.Dependency d : result.dependencies) {
            jdbc.update("INSERT INTO dependency(scheme_id, from_object, to_object, kind) VALUES (?,?,?,?)",
                    schemeId, d.from, d.to, d.kind);
        }
        return schemeId;
    }

    private Long chosenValue(AdjudicationResult result, Verdict v) {
        if (v.chosen == null) return null;
        for (LoadedObject o : result.objects) {
            if (!o.name.equals(v.chosen)) continue;
            List<ElfFile.Symbol> syms = o.elf.isShared() ? o.elf.dynSymbols
                    : (o.elf.symbols.isEmpty() ? o.elf.dynSymbols : o.elf.symbols);
            for (ElfFile.Symbol s : syms) {
                if (s.isUndefined()) continue;
                if (!s.name.equals(v.symbolName)) continue;
                if (v.version != null && !v.version.equals(s.versionName)) continue;
                return s.value;
            }
        }
        return null;
    }

    private String metaJson(ElfFile f) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"type\":").append(f.type);
        if (f.soname != null) sb.append(",\"soname\":\"").append(esc(f.soname)).append("\"");
        sb.append(",\"needed\":[");
        for (int i = 0; i < f.needed.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("\"").append(esc(f.needed.get(i))).append("\"");
        }
        sb.append("],\"verdefs\":[");
        for (int i = 0; i < f.verdefs.size(); i++) {
            if (i > 0) sb.append(',');
            ElfFile.Verdef vd = f.verdefs.get(i);
            sb.append("{\"name\":\"").append(esc(vd.name)).append("\",\"index\":").append(vd.index)
                    .append(",\"aliases\":[");
            for (int j = 0; j < vd.aliases.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append("\"").append(esc(vd.aliases.get(j))).append("\"");
            }
            sb.append("]}");
        }
        sb.append("],\"verneeds\":[");
        for (int i = 0; i < f.verneeds.size(); i++) {
            if (i > 0) sb.append(',');
            ElfFile.Verneed vn = f.verneeds.get(i);
            sb.append("{\"file\":\"").append(esc(vn.file)).append("\",\"versions\":[");
            for (int j = 0; j < vn.entries.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append("\"").append(esc(vn.entries.get(j).name)).append("\"");
            }
            sb.append("]}");
        }
        sb.append("],\"groups\":[");
        for (int i = 0; i < f.groups.size(); i++) {
            if (i > 0) sb.append(',');
            ElfFile.Group g = f.groups.get(i);
            sb.append("{\"signature\":\"").append(esc(g.signature)).append("\",\"comdat\":").append(g.comdat).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> schemeDetail(long id) {
        Map<String, Object> out = new HashMap<>();
        List<Map<String, Object>> schemes = jdbc.queryForList(
                "SELECT * FROM scheme WHERE id = ?", id);
        if (schemes.isEmpty()) return null;
        out.put("scheme", schemes.get(0));
        out.put("inputs", jdbc.queryForList(
                "SELECT * FROM scheme_object WHERE scheme_id = ? AND kind IN ('INPUT','CORRUPT') ORDER BY id", id));
        List<Map<String, Object>> objects = jdbc.queryForList(
                "SELECT * FROM scheme_object WHERE scheme_id = ? AND kind IN ('LOADED','ARCHIVE_MEMBER') ORDER BY seq", id);
        for (Map<String, Object> o : objects) {
            Object meta = o.get("meta");
            if (meta != null) {
                try {
                    o.put("metaObj", mapper.readValue(meta.toString(), Map.class));
                } catch (Exception ignored) {
                    o.put("metaObj", Map.of());
                }
            } else {
                o.put("metaObj", Map.of());
            }
        }
        out.put("objects", objects);
        List<Map<String, Object>> verdicts = jdbc.queryForList(
                "SELECT * FROM verdict WHERE scheme_id = ? ORDER BY symbol_name, version", id);
        for (Map<String, Object> v : verdicts) {
            long vid = ((Number) v.get("id")).longValue();
            v.put("candidates", jdbc.queryForList(
                    "SELECT * FROM candidate WHERE verdict_id = ? ORDER BY id", vid));
            v.put("relocations", jdbc.queryForList(
                    "SELECT * FROM reloc WHERE verdict_id = ? ORDER BY id", vid));
        }
        out.put("verdicts", verdicts);
        out.put("events", jdbc.queryForList(
                "SELECT * FROM event WHERE scheme_id = ? ORDER BY seq", id));
        out.put("dependencies", jdbc.queryForList(
                "SELECT * FROM dependency WHERE scheme_id = ? ORDER BY id", id));
        return out;
    }
}
