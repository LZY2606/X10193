package com.symbolcourt.web;

import com.symbolcourt.model.ReferenceDiff;
import com.symbolcourt.service.CourtService;
import com.symbolcourt.storage.BinaryStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CourtController {

    private final CourtService service;

    public CourtController(CourtService service) {
        this.service = service;
    }

    public record UploadResponse(String fileId, String originalName, long size, String kind) {
    }

    @PostMapping("/binaries")
    public UploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] data = file.getBytes();
        String original = file.getOriginalFilename();
        BinaryStore.StoredBinary stored = service.importBinary(data, original);
        String kind = service.classifyBinary(data, original);
        return new UploadResponse(stored.fileId(), stored.originalName(), stored.size(), kind);
    }

    @PostMapping("/schemes")
    public CourtService.SchemeView create(@RequestBody CourtService.CreateSchemeRequest request) {
        return service.createScheme(request);
    }

    @GetMapping("/schemes")
    public List<CourtService.SchemeView> list() {
        return service.list();
    }

    @GetMapping("/schemes/{id}")
    public ResponseEntity<CourtService.SchemeView> get(@PathVariable String id) {
        CourtService.SchemeView v = service.get(id);
        return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(v);
    }

    @GetMapping("/schemes/{id}/lineage")
    public List<CourtService.SchemeView> lineage(@PathVariable String id) {
        CourtService.SchemeView v = service.get(id);
        if (v == null) {
            return List.of();
        }
        return service.lineage(v.lineageId());
    }

    @GetMapping("/diff")
    public List<ReferenceDiff> diff(@RequestParam String a, @RequestParam String b) {
        return service.diff(a, b);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage() == null ? e.toString() : e.getMessage()));
    }
}
