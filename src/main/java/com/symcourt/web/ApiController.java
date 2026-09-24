package com.symcourt.web;

import com.symcourt.engine.EngineModel.CompareEntry;
import com.symcourt.store.CourtStore;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final CourtService service;

    public ApiController(CourtService service) {
        this.service = service;
    }

    public record ImportResult(String name, long id, String kind, String error) { }

    @PostMapping("/objects")
    public List<ImportResult> upload(@RequestParam("files") MultipartFile[] files) throws Exception {
        return java.util.Arrays.stream(files).map(f -> {
            try {
                CourtStore.ObjectRow row = service.importObject(f.getOriginalFilename(), f.getBytes());
                return new ImportResult(row.name(), row.id(), row.kind(), row.error());
            } catch (Exception e) {
                return new ImportResult(f.getOriginalFilename(), -1, "corrupt", e.getMessage());
            }
        }).toList();
    }

    @GetMapping("/objects")
    public List<CourtStore.ObjectRow> objects() {
        return service.listObjects();
    }

    public record SchemeRequest(String name, List<Long> objectIds, String policy) { }

    @PostMapping("/schemes")
    public ResponseEntity<?> createScheme(@RequestBody SchemeRequest req) {
        try {
            long id = service.createScheme(
                    req.name() == null || req.name().isBlank() ? "scheme" : req.name(),
                    req.objectIds(), req.policy());
            return ResponseEntity.ok(Map.of("id", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/schemes")
    public List<CourtStore.SchemeRow> schemes() {
        return service.listSchemes();
    }

    @GetMapping("/schemes/{id}")
    public ResponseEntity<CourtService.SchemeView> scheme(@PathVariable long id) {
        CourtService.SchemeView view = service.getScheme(id);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    @GetMapping("/schemes/compare")
    public List<CompareEntry> compare(@RequestParam long a, @RequestParam long b) {
        return service.compare(a, b);
    }
}
