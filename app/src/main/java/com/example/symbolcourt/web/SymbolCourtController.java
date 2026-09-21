package com.example.symbolcourt.web;

import com.example.symbolcourt.domain.Models.*;
import com.example.symbolcourt.service.PlanComparisonService;
import com.example.symbolcourt.service.PlanService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SymbolCourtController {
    private final PlanService planService;
    private final PlanComparisonService comparisonService;

    public SymbolCourtController(PlanService planService, PlanComparisonService comparisonService) {
        this.planService = planService;
        this.comparisonService = comparisonService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "name", "符号裁决庭");
    }

    @PostMapping("/plans/analyze")
    public Plan analyze(@RequestParam("files") MultipartFile[] files,
                        @RequestParam(value = "order", required = false) String order,
                        @RequestParam(value = "name", required = false, defaultValue = "新方案") String name,
                        @RequestParam(value = "policy", required = false, defaultValue = "GNU") String policy)
            throws IOException {
        List<PlanService.StoredUpload> uploads = Arrays.stream(files)
                .map(file -> {
                    try {
                        return PlanService.StoredUpload.of(file.getOriginalFilename(), file.getBytes());
                    } catch (IOException ex) {
                        throw new IllegalArgumentException("无法读取上传文件", ex);
                    }
                }).toList();
        List<String> orderedNames = order == null || order.isBlank() ? List.of()
                : Arrays.stream(order.split("[\\n,;]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return planService.createFromUploads(name, policy, uploads, orderedNames);
    }

    @GetMapping("/plans")
    public List<Plan> plans() {
        return planService.list();
    }

    @GetMapping("/plans/{id}")
    public Plan plan(@PathVariable String id) {
        return planService.get(id);
    }

    @GetMapping("/plans/{id}/objects")
    public List<ParsedObject> objects(@PathVariable String id) {
        return planService.get(id).result().objects();
    }

    @GetMapping("/plans/{id}/dependencies")
    public List<DependencyEdge> dependencies(@PathVariable String id) {
        return planService.get(id).result().dependencyEdges();
    }

    @GetMapping("/plans/{id}/decisions")
    public List<ResolutionDecision> decisions(@PathVariable String id) {
        return planService.get(id).result().decisions();
    }

    @GetMapping("/plans/{id}/versions")
    public Map<String, Object> versions(@PathVariable String id) {
        ResolutionResult result = planService.get(id).result();
        return Map.of("nodes", result.versionNodes(), "edges", result.versionEdges());
    }

    @GetMapping("/plans/{id}/relocations")
    public List<SymbolReference> relocations(@PathVariable String id) {
        return planService.get(id).result().objects().stream()
                .flatMap(object -> object.references().stream())
                .toList();
    }

    @GetMapping("/plans/{id}/events")
    public List<PlayEvent> events(@PathVariable String id) {
        return planService.get(id).result().events();
    }

    @GetMapping("/plans/{id}/extracted")
    public List<ArchiveMember> extracted(@PathVariable String id) {
        return planService.get(id).result().extractedMembers();
    }

    @PostMapping("/plans/compare")
    public PlanComparison compare(@RequestBody CompareRequest request) {
        return comparisonService.compare(planService.get(request.leftPlanId()),
                planService.get(request.rightPlanId()));
    }

    public record CompareRequest(String leftPlanId, String rightPlanId) {
    }
}
