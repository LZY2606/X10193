package com.symtribunal.web;

import com.symtribunal.engine.ComparisonResult;
import com.symtribunal.engine.ResolutionReport;
import com.symtribunal.engine.SchemeRequest;
import com.symtribunal.store.BinaryRecord;
import com.symtribunal.store.SchemeRecord;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TribunalController {

    private final TribunalService service;

    public TribunalController(TribunalService service) {
        this.service = service;
    }

    @GetMapping("/api/binary")
    public List<BinaryRecord> binaries() {
        return service.binaries();
    }

    @PostMapping("/api/binary")
    public BinaryRecord upload(@RequestParam("file") MultipartFile file) throws Exception {
        return service.upload(
                file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename(),
                file.getBytes());
    }

    @PostMapping("/api/binary/bytes")
    public BinaryRecord uploadBytes(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("fileName", "fixture"));
        byte[] data = java.util.Base64.getDecoder().decode((String) body.get("contentBase64"));
        return service.upload(name, data);
    }

    @GetMapping("/api/scheme")
    public List<SchemeRecord> schemes() {
        return service.schemes();
    }

    @PostMapping("/api/scheme")
    @ResponseStatus(HttpStatus.CREATED)
    public ResolutionReport createScheme(@RequestBody SchemeRequest request) {
        return service.runScheme(request.name, request.policy, request.machine, request.binaryIds);
    }

    @GetMapping("/api/scheme/{id}")
    public ResolutionReport scheme(@PathVariable String id) {
        ResolutionReport report = service.scheme(id);
        if (report == null) {
            throw new SchemeNotFoundException();
        }
        return report;
    }

    @GetMapping("/api/scheme/{idA}/compare/{idB}")
    public ComparisonResult compare(@PathVariable String idA, @PathVariable String idB) {
        return service.compare(idA, idB);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class SchemeNotFoundException extends RuntimeException {
    }
}
