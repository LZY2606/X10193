package com.symbolcourt.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CourtController {

    private final CourtService service;

    public CourtController(CourtService service) {
        this.service = service;
    }

    @GetMapping("/policies")
    public List<Map<String, String>> policies() {
        return CourtService.policies();
    }

    @GetMapping("/artifacts")
    public List<Map<String, Object>> artifacts() {
        return service.listArtifacts();
    }

    @PostMapping("/artifacts")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws Exception {
        long id = service.uploadArtifact(file.getOriginalFilename(), file.getBytes());
        return Map.of("artifactId", id, "name", file.getOriginalFilename(),
                "size", file.getSize());
    }

    @GetMapping("/schemes")
    public List<Map<String, Object>> schemes() {
        return service.listSchemes();
    }

    @PostMapping("/schemes")
    public Map<String, Object> createScheme(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "scheme"));
        String policy = body.get("policy") == null ? "gnu-ld" : String.valueOf(body.get("policy"));
        @SuppressWarnings("unchecked")
        List<Number> raw = (List<Number>) body.getOrDefault("artifactIds", List.of());
        List<Long> ids = raw.stream().map(Number::longValue).toList();
        return service.createScheme(name, policy, ids);
    }

    @GetMapping("/schemes/{id}")
    public ResponseEntity<Map<String, Object>> scheme(@PathVariable long id) {
        Map<String, Object> v = service.schemeView(id);
        return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(v);
    }

    @GetMapping("/schemes/{a}/diff/{b}")
    public Map<String, Object> diff(@PathVariable long a, @PathVariable long b) {
        return service.compare(a, b);
    }
}
