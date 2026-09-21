package court.service;

import court.engine.InputEntry;
import court.engine.LinkEngine;
import court.engine.SchemeComparator;
import court.model.BadObject;
import court.model.Decision;
import court.model.Policy;
import court.model.RefDiff;
import court.store.CourtRepository;
import court.store.SchemeRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 方案版本化门面：
 *  - 同一名字每执行一次产生一个新版本（顺序/策略不同 => 新版本）；
 *  - 结果在创建时序列化为 JSON 快照，读取的永远是当时结论，旧方案不漂移；
 *  - 比较直接在两个快照上进行。
 */
@Service
public class CourtService {

    private final CourtRepository repo;
    private final ImportService importService;
    private final ObjectMapper mapper;

    public CourtService(CourtRepository repo, ImportService importService, ObjectMapper mapper) {
        this.repo = repo;
        this.importService = importService;
        this.mapper = mapper;
    }

    public static final class CreatedScheme {
        public final String schemeId;
        public final int versionNo;

        public CreatedScheme(String schemeId, int versionNo) {
            this.schemeId = schemeId;
            this.versionNo = versionNo;
        }
    }

    public CreatedScheme createScheme(String name, Policy policy, List<String> orderedUploadIds) {
        int versionNo = repo.nextVersionNo(name);
        List<InputEntry> entries = new ArrayList<>();
        List<BadObject> bad = new ArrayList<>();
        for (int i = 0; i < orderedUploadIds.size(); i++) {
            InputEntry entry = importService.toEntry(orderedUploadIds.get(i), i);
            entries.add(entry);
            bad.addAll(importService.collectBadObjects(entry));
        }
        String schemeId = "s_" + UUID.randomUUID();
        Decision decision = LinkEngine.run(schemeId, name, policy, entries, bad);
        decision.policyOrdinalVersion = versionNo;

        String decisionJson = writeJson(decision);
        String orderJson = writeJson(orderedUploadIds);
        repo.insertScheme(new SchemeRow(schemeId, name, versionNo, policy.name(),
                orderJson, decisionJson, Instant.now().toString()));
        for (int i = 0; i < orderedUploadIds.size(); i++) {
            repo.linkSchemeUpload(schemeId, orderedUploadIds.get(i), i);
        }
        return new CreatedScheme(schemeId, versionNo);
    }

    public Decision loadDecision(String schemeId) {
        SchemeRow row = repo.getScheme(schemeId);
        if (row == null) {
            return null;
        }
        return readJson(row.decisionJson, Decision.class);
    }

    public List<SchemeRow> listSchemes() {
        return repo.listSchemes();
    }

    public List<RefDiff> compare(String fromSchemeId, String toSchemeId) {
        Decision a = loadDecision(fromSchemeId);
        Decision b = loadDecision(toSchemeId);
        if (a == null || b == null) {
            throw new IllegalArgumentException("方案不存在，无法比较");
        }
        return SchemeComparator.compare(a, b);
    }

    public List<String> orderedUploads(String schemeId) {
        SchemeRow row = repo.getScheme(schemeId);
        if (row == null) {
            return List.of();
        }
        return readJson(row.loadOrderJson, new TypeReference<List<String>>() {
        });
    }

    public String decisionJson(String schemeId) {
        SchemeRow row = repo.getScheme(schemeId);
        return row == null ? null : row.decisionJson;
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }
}
