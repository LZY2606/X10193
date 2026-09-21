package court.web;

import court.service.CourtService;
import court.service.ImportService;
import court.store.CourtRepository;
import court.store.SchemeRow;
import court.store.UploadRow;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class UploadController {

    private final ImportService importService;
    private final CourtService courtService;
    private final CourtRepository repo;

    public UploadController(ImportService importService, CourtService courtService,
                            CourtRepository repo) {
        this.importService = importService;
        this.courtService = courtService;
        this.repo = repo;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping(value = "/api/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(@RequestParam("files") MultipartFile[] files,
                         RedirectAttributes ra) throws IOException {
        List<String> ids = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f.isEmpty()) {
                continue;
            }
            ImportService.StoredUpload stored = importService.store(
                    f.getOriginalFilename(), f.getBytes());
            ids.add(stored.uploadId());
            if (stored.parseError() != null) {
                errors.add(stored.name() + ": " + stored.parseError());
            }
        }
        ra.addFlashAttribute("uploadedIds", ids);
        if (!errors.isEmpty()) {
            ra.addFlashAttribute("uploadErrors", errors);
        }
        return "redirect:/";
    }

    @GetMapping("/api/uploads")
    @ResponseBody
    public List<Map<String, Object>> listUploads() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UploadRow u : repo.listUploads()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.id);
            m.put("name", u.name);
            m.put("kind", u.kind);
            m.put("sha256", u.sha256);
            m.put("parseError", u.parseError);
            m.put("createdAt", u.createdAt);
            out.add(m);
        }
        return out;
    }

    public record CreateSchemeRequest(String name, String policy, List<String> orderedUploadIds) {
    }

    @PostMapping("/api/schemes")
    @ResponseBody
    public Map<String, Object> createScheme(@RequestBody CreateSchemeRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("方案名不能为空");
        }
        if (req.orderedUploadIds() == null || req.orderedUploadIds().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个对象");
        }
        var policy = "RTLD_GLOBAL".equals(req.policy())
                ? court.model.Policy.RTLD_GLOBAL : court.model.Policy.STATIC_LD;
        CourtService.CreatedScheme created = courtService.createScheme(
                req.name().trim(), policy, req.orderedUploadIds());
        return Map.of("schemeId", created.schemeId, "versionNo", created.versionNo);
    }

    @GetMapping("/api/schemes")
    @ResponseBody
    public List<Map<String, Object>> listSchemes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SchemeRow s : courtService.listSchemes()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id);
            m.put("name", s.name);
            m.put("versionNo", s.versionNo);
            m.put("policy", s.policy);
            m.put("createdAt", s.createdAt);
            out.add(m);
        }
        return out;
    }

    @GetMapping(value = "/api/schemes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String scheme(@PathVariable String id) {
        String json = courtService.decisionJson(id);
        if (json == null) {
            throw new NotFoundException();
        }
        return json;
    }

    @GetMapping(value = "/api/compare", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object compare(@RequestParam String from, @RequestParam String to) {
        return courtService.compare(from, to);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(NotFoundException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    @ResponseBody
    public Map<String, String> notFound() {
        return Map.of("error", "方案不存在");
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, String> bad(IllegalArgumentException e) {
        return Map.of("error", e.getMessage() == null ? "请求非法" : e.getMessage());
    }

    static class NotFoundException extends RuntimeException {
    }
}
