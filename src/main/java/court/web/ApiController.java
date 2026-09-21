package court.web;

import court.domain.Scheme;
import court.link.LinkPolicy;
import court.link.LinkReport;
import court.link.SchemeComparator;
import court.service.DemoFixtures;
import court.service.SchemeService;
import court.service.SchemeService.Upload;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final SchemeService schemes;
    private final DemoFixtures demo;
    private final SchemeComparator comparator;

    public ApiController(SchemeService schemes, DemoFixtures demo, SchemeComparator comparator) {
        this.schemes = schemes;
        this.demo = demo;
        this.comparator = comparator;
    }

    @GetMapping("/schemes")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Scheme s : schemes.list()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id);
            m.put("name", s.name);
            m.put("version", s.version);
            m.put("parentId", s.parentId);
            m.put("policy", s.policy);
            m.put("loadOrder", s.loadOrder);
            m.put("createdAt", s.createdAt);
            out.add(m);
        }
        return out;
    }

    @GetMapping("/schemes/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Scheme s = schemes.get(id);
        if (s == null) throw new NotFound("scheme " + id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scheme", schemeMeta(s));
        m.put("report", schemes.loadReport(s));
        return m;
    }

    @GetMapping("/schemes/{id}/report")
    public LinkReport report(@PathVariable long id) {
        Scheme s = schemes.get(id);
        if (s == null) throw new NotFound("scheme " + id);
        return schemes.loadReport(s);
    }

    @PostMapping("/schemes")
    public Map<String, Object> create(@RequestParam String name,
                                      @RequestParam(required = false) Long parentId,
                                      @RequestParam(required = false) Integer machine,
                                      @RequestParam(required = false) String archivePolicy,
                                      @RequestParam(required = false) String tiePolicy,
                                      @RequestParam(required = false) String platformName,
                                      @RequestParam(value = "files", required = false) MultipartFile[] files,
                                      @RequestParam(value = "order", required = false) List<String> order)
            throws Exception {
        LinkPolicy policy = new LinkPolicy();
        if (machine != null) policy.machine = machine;
        if (archivePolicy != null) policy.archivePolicy = archivePolicy;
        if (tiePolicy != null) policy.tiePolicy = tiePolicy;
        if (platformName != null && !platformName.isBlank()) policy.platformName = platformName;

        List<Upload> uploads = new ArrayList<>();
        if (files != null) {
            // apply explicit order if given (matches original file names), else upload order
            MultipartFile[] arranged = arrange(files, order);
            for (MultipartFile mf : arranged) {
                uploads.add(new Upload(mf.getOriginalFilename(), mf.getBytes()));
            }
        }
        Scheme s = schemes.createVersion(name, policy, uploads, parentId);
        return Map.of("id", s.id, "name", s.name, "version", s.version);
    }

    @PostMapping("/demo/{which}")
    public Map<String, Object> demo(@PathVariable String which) {
        LinkPolicy policy = LinkPolicy.x86_64();
        List<DemoFixtures.Fixture> fixtures;
        String name;
        switch (which) {
            case "B":
                policy.archivePolicy = "SINGLE_PASS";
                fixtures = demo.orderB();
                name = "演示方案B";
                break;
            case "unmet":
                fixtures = demo.unmetVersion();
                name = "未满足版本";
                break;
            case "A":
            default:
                fixtures = demo.orderA();
                name = "演示方案A";
        }
        List<Upload> ups = new ArrayList<>();
        for (DemoFixtures.Fixture f : fixtures) ups.add(new Upload(f.fileName, f.data));
        Scheme s = schemes.createVersion(name, policy, ups, null);
        return Map.of("id", s.id, "name", s.name, "version", s.version);
    }

    @PostMapping("/schemes/{id}/clone")
    public Map<String, Object> cloneVersion(@PathVariable long id,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) Integer machine,
                                            @RequestParam(required = false) String archivePolicy,
                                            @RequestParam(required = false) String tiePolicy,
                                            @RequestParam(required = false) String platformName,
                                            @RequestParam(value = "order", required = false) List<String> order) {
        Scheme src = schemes.get(id);
        if (src == null) throw new NotFound("scheme " + id);
        List<Upload> uploads = schemes.copyInputs(id);
        if (order != null && !order.isEmpty()) uploads = reorder(uploads, order);
        LinkPolicy policy = src.policy;
        if (machine != null) policy.machine = machine;
        if (archivePolicy != null) policy.archivePolicy = archivePolicy;
        if (tiePolicy != null) policy.tiePolicy = tiePolicy;
        if (platformName != null && !platformName.isBlank()) policy.platformName = platformName;
        Scheme s = schemes.createVersion(name == null || name.isBlank() ? src.name : name,
                policy, uploads, src.id);
        return Map.of("id", s.id, "name", s.name, "version", s.version, "parentId", s.parentId);
    }

    @GetMapping("/compare")
    public SchemeComparator.Diff compare(@RequestParam long a, @RequestParam long b) {
        Scheme sa = schemes.get(a);
        Scheme sb = schemes.get(b);
        if (sa == null || sb == null) throw new NotFound("missing scheme");
        return comparator.compare(schemes.loadReport(sa), schemes.loadReport(sb));
    }

    private Map<String, Object> schemeMeta(Scheme s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("name", s.name);
        m.put("version", s.version);
        m.put("parentId", s.parentId);
        m.put("policy", s.policy);
        m.put("loadOrder", s.loadOrder);
        m.put("createdAt", s.createdAt);
        return m;
    }

    private MultipartFile[] arrange(MultipartFile[] files, List<String> order) {
        if (order == null || order.isEmpty()) return files;
        List<MultipartFile> out = new ArrayList<>();
        List<MultipartFile> rest = new ArrayList<>(List.of(files));
        for (String name : order) {
            rest.stream().filter(f -> name.equals(f.getOriginalFilename())).findFirst()
                    .ifPresent(f -> { out.add(f); rest.remove(f); });
        }
        out.addAll(rest);
        return out.toArray(new MultipartFile[0]);
    }

    private List<Upload> reorder(List<Upload> uploads, List<String> order) {
        List<Upload> out = new ArrayList<>();
        List<Upload> rest = new ArrayList<>(uploads);
        for (String name : order) {
            rest.stream().filter(u -> name.equals(u.fileName)).findFirst()
                    .ifPresent(u -> { out.add(u); rest.remove(u); });
        }
        out.addAll(rest);
        return out;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(NotFound.class)
    public ResponseEntity<Map<String, String>> notFound(NotFound e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }

    static class NotFound extends RuntimeException {
        NotFound(String m) { super(m); }
    }
}
