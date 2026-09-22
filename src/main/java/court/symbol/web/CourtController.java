package court.symbol.web;

import court.symbol.db.ObjectRepository;
import court.symbol.model.ResolutionReport;
import court.symbol.service.CourtService;
import court.symbol.service.Dtos;
import court.symbol.service.ReportComparator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CourtController {

    private final ObjectRepository objects;
    private final CourtService service;
    private final ReportComparator comparator;

    public CourtController(ObjectRepository objects, CourtService service,
                           ReportComparator comparator) {
        this.objects = objects;
        this.service = service;
        this.comparator = comparator;
    }

    @PostMapping("/objects")
    public ObjectRepository.Stored upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        return objects.store(file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/objects")
    public List<ObjectRepository.Stored> listObjects() {
        return objects.list();
    }

    @PostMapping("/plans")
    public CourtService.CreatedVersion createPlan(@RequestBody Dtos.CreatePlanRequest req) {
        return service.createVersion(req);
    }

    @GetMapping("/plans")
    public List<Dtos.PlanSummary> plans() {
        return service.plans();
    }

    @GetMapping("/plans/{planId}/versions")
    public List<Dtos.VersionSummary> versions(@PathVariable String planId) {
        return service.versions(planId);
    }

    @GetMapping("/plans/{planId}/versions/{version}")
    public ResponseEntity<ResolutionReport> report(@PathVariable String planId,
                                                   @PathVariable int version) {
        ResolutionReport r = service.report(planId, version);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/plans/{planId}/compare")
    public ReportComparator.Diff compare(@PathVariable String planId,
                                         @RequestParam int a, @RequestParam int b) {
        ResolutionReport ra = service.report(planId, a);
        ResolutionReport rb = service.report(planId, b);
        if (ra == null || rb == null) {
            throw new IllegalArgumentException("方案版本不存在");
        }
        return comparator.compare(ra, rb);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "court", "符号裁决庭");
    }
}
