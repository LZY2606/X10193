package org.tribunal.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tribunal.model.ResolutionResult;
import org.tribunal.model.Scheme;
import org.tribunal.model.SchemeComparison;
import org.tribunal.service.SchemeService;

@RestController
@RequestMapping("/api/schemes")
public class SchemeController {
    private final SchemeService schemeService;

    public SchemeController(SchemeService schemeService) {
        this.schemeService = schemeService;
    }

    @GetMapping
    public List<Scheme> list() {
        return schemeService.list();
    }

    @PostMapping
    public Scheme create(@RequestBody CreateSchemeRequest request) {
        return schemeService.create(request.label(), request.policy(), request.orderedBinaryIds());
    }

    @GetMapping("/{id}")
    public Scheme get(@PathVariable long id) {
        return schemeService.get(id);
    }

    @GetMapping("/{id}/result")
    public ResolutionResult result(@PathVariable long id) {
        return schemeService.getResult(id);
    }

    @GetMapping("/compare")
    public SchemeComparison compare(@RequestParam long a, @RequestParam long b) {
        return schemeService.compare(a, b);
    }

    public record CreateSchemeRequest(String label, String policy, List<Long> orderedBinaryIds) {
    }
}
