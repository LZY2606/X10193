package court.symbol.web;

import court.symbol.model.SchemeComparison;
import court.symbol.storage.CourtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    @PostMapping("/binaries")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        return service.importBinary(file);
    }

    @GetMapping("/binaries")
    public List<Map<String, Object>> binaries() {
        return service.listBinaries();
    }

    @PostMapping("/schemes")
    public Map<String, Object> createScheme(@RequestBody CreateSchemeRequest request) {
        return service.createScheme(request.name(), request.policy(), request.binaryIds());
    }

    @GetMapping("/schemes")
    public List<Map<String, Object>> schemes() {
        return service.listSchemes();
    }

    @GetMapping("/schemes/{id}")
    public Object scheme(@PathVariable String id) {
        return service.getScheme(id);
    }

    @PostMapping("/compare")
    public SchemeComparison compare(@RequestBody CompareRequest request) {
        return service.compare(request.beforeSchemeId(), request.afterSchemeId());
    }

    @GetMapping("/demo")
    public Object demo() {
        return service.demo();
    }

    public record CreateSchemeRequest(String name, String policy, List<String> binaryIds) {
    }

    public record CompareRequest(String beforeSchemeId, String afterSchemeId) {
    }
}
