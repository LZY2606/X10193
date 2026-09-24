package court.web;

import court.db.CourtRepository;
import court.engine.InputEntry;
import court.engine.LinkResult;
import court.engine.Linker;
import court.engine.Policy;
import court.engine.SchemeDiff;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourtService {

    private final CourtRepository repo;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public CourtService(CourtRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> runAndStore(String label, List<UploadedFile> files,
                                           Policy policy) {
        List<InputEntry> inputs = new ArrayList<>();
        List<String> shas = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            UploadedFile f = files.get(i);
            inputs.add(new InputEntry("in" + i, f.name(), f.data()));
        }
        LinkResult result = new Linker().run(inputs, policy);
        for (InputEntry e : inputs) {
            shas.add(repo.putObject(e.name, e.data, e.detectedKind, e.corruptReason));
        }
        String policyJson;
        String resultJson;
        try {
            policyJson = mapper.writeValueAsString(policy);
            resultJson = mapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("serialization failed", ex);
        }
        long id = repo.createScheme(label == null || label.isBlank()
                ? ("方案 #" + System.currentTimeMillis()) : label,
                policyJson, resultJson, shas);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemeId", id);
        out.put("result", result);
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadResult(long schemeId) {
        var row = repo.schemeRow(schemeId);
        if (row == null) return null;
        try {
            return mapper.readValue((String) row.get("RESULT_JSON"), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Map<String, Object>> listSchemes() {
        return repo.listSchemes();
    }

    public List<Map<String, Object>> listObjects() {
        return repo.listObjects();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> diff(long a, long b) {
        String cached = repo.diff(a, b);
        if (cached != null) {
            try {
                return mapper.readValue(cached, Map.class);
            } catch (Exception ignored) {
                // recompute below
            }
        }
        LinkResult ra = parseResult(a);
        LinkResult rb = parseResult(b);
        if (ra == null || rb == null) return null;
        Map<String, Object> diff = new SchemeDiff().compare(ra, rb);
        diff.put("schemeA", a);
        diff.put("schemeB", b);
        try {
            repo.saveDiff(a, b, mapper.writeValueAsString(diff));
        } catch (Exception ignored) {
            // caching is best-effort
        }
        return diff;
    }

    private LinkResult parseResult(long id) {
        var row = repo.schemeRow(id);
        if (row == null) return null;
        try {
            return mapper.readValue((String) row.get("RESULT_JSON"), LinkResult.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record UploadedFile(String name, byte[] data) {}
}
