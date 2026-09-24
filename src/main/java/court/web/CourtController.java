package court.web;

import court.engine.Policy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CourtController {

    private final CourtService service;

    public CourtController(CourtService service) {
        this.service = service;
    }

    @PostMapping(value = "/schemes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createScheme(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "label", required = false) String label,
            @RequestParam(value = "phase", required = false) String phase,
            @RequestParam(value = "protectedMode", required = false) String protectedMode,
            @RequestParam(value = "tieMode", required = false) String tieMode,
            @RequestParam(value = "weakExtract", required = false, defaultValue = "false")
                    boolean weakExtract,
            @RequestParam(value = "copyReloc", required = false, defaultValue = "true")
                    boolean copyReloc) throws IOException {
        List<CourtService.UploadedFile> uploads = new ArrayList<>();
        for (MultipartFile f : files) {
            uploads.add(new CourtService.UploadedFile(f.getOriginalFilename(), f.getBytes()));
        }
        Policy policy = Policy.gnuDefault();
        if (phase != null) policy.phase = Policy.Phase.valueOf(phase);
        if (protectedMode != null) {
            policy.protectedMode = Policy.ProtectedMode.valueOf(protectedMode);
        }
        if (tieMode != null) policy.tieMode = Policy.TieMode.valueOf(tieMode);
        policy.weakExtractsArchives = weakExtract;
        policy.copyRelocEnabled = copyReloc;
        return ResponseEntity.ok(service.runAndStore(label, uploads, policy));
    }

    @GetMapping("/schemes")
    public List<Map<String, Object>> schemes() {
        return service.listSchemes();
    }

    @GetMapping("/schemes/{id}")
    public ResponseEntity<?> scheme(@PathVariable long id) {
        Map<String, Object> r = service.loadResult(id);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/schemes/{a}/diff/{b}")
    public ResponseEntity<?> diff(@PathVariable long a, @PathVariable long b) {
        Map<String, Object> d = service.diff(a, b);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @GetMapping("/objects")
    public List<Map<String, Object>> objects() {
        return service.listObjects();
    }
}
