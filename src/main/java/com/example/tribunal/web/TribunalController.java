package com.example.tribunal.web;

import com.example.tribunal.core.model.TribunalModels.*;
import com.example.tribunal.service.TribunalService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TribunalController {
    private final TribunalService service;

    public TribunalController(TribunalService service) {
        this.service = service;
    }

    @GetMapping("/schemes")
    public List<StoredScheme> list() {
        return service.list();
    }

    @PostMapping("/schemes")
    public Scheme create(@RequestParam(required = false) String name,
                         @RequestParam(defaultValue = "gnu") String policy,
                         @RequestParam(defaultValue = "true") boolean copyRelocation,
                         @RequestParam(defaultValue = "false") boolean copyProtected,
                         @RequestParam("files") List<MultipartFile> files) {
        PlatformPolicy platformPolicy = new PlatformPolicy(copyRelocation, copyProtected, false, policy);
        return service.create(name, files, platformPolicy);
    }

    @PostMapping("/sample")
    public Scheme sample() {
        return service.sample();
    }

    @GetMapping("/schemes/{id}")
    public Scheme get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/schemes/{id}/events")
    public List<Event> events(@PathVariable String id, @RequestParam(defaultValue = "0") int upTo) {
        List<Event> events = service.get(id).events();
        if (upTo <= 0) return events;
        return events.stream().filter(event -> event.step() <= upTo).toList();
    }

    @GetMapping("/schemes/{id}/versions")
    public VersionGraph versions(@PathVariable String id) {
        return service.get(id).versionGraph();
    }

    @GetMapping("/compare")
    public SchemeComparison compare(@RequestParam String left, @RequestParam String right) {
        return service.compare(left, right);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "application", "符号裁决庭");
    }
}
