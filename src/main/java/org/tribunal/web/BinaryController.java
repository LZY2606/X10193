package org.tribunal.web;

import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.tribunal.model.ParsedBinary;
import org.tribunal.service.BinaryService;

@RestController
@RequestMapping("/api/binaries")
public class BinaryController {
    private final BinaryService binaryService;

    public BinaryController(BinaryService binaryService) {
        this.binaryService = binaryService;
    }

    @GetMapping
    public List<ParsedBinary> list() {
        return binaryService.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedBinary upload(@RequestParam("file") MultipartFile file) throws IOException {
        return binaryService.importBinary(file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping(value = "/{id}/payload", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] payload(@PathVariable long id) {
        return binaryService.payload(id);
    }
}
