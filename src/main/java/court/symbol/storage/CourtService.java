package court.symbol.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import court.symbol.elf.BinaryParser;
import court.symbol.elf.ElfReadException;
import court.symbol.elf.ParsedBinary;
import court.symbol.engine.LinkEngine;
import court.symbol.engine.SchemeComparator;
import court.symbol.fixture.ArchiveFixtureBuilder;
import court.symbol.fixture.ElfFixtureBuilder;
import court.symbol.model.PlatformPolicy;
import court.symbol.model.ResolutionInput;
import court.symbol.model.Scheme;
import court.symbol.model.SchemeComparison;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class CourtService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BinaryParser parser = new BinaryParser();
    private final LinkEngine linkEngine = new LinkEngine();
    private final SchemeComparator comparator = new SchemeComparator();
    private final Map<String, ParsedBinary> memoryBinaries = new java.util.concurrent.ConcurrentHashMap<>();

    public CourtService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> importBinary(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String hash = sha256(bytes);
            String id = hash.substring(0, 16);
            String path = "data/bin-" + id;
            try {
                Files.write(Path.of(path), bytes, StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) {
            }
            ParsedBinary binary;
            String status = "OK";
            String error = null;
            try {
                binary = parser.parse(bytes, file.getOriginalFilename());
                memoryBinaries.put(id, binary);
            } catch (ElfReadException ex) {
                status = "CORRUPT";
                error = ex.getMessage();
                binary = null;
            }
            jdbcTemplate.update("""
                            insert into binaries(id, file_name, content_hash, storage_path, parse_status,
                                                parse_error, created_at)
                            values (?, ?, ?, ?, ?, ?, ?)
                            on conflict(content_hash) do nothing
                            """, id, file.getOriginalFilename(), hash, path, status, error,
                    Instant.now().toString());
            return Map.of("id", id, "fileName", file.getOriginalFilename(), "status", status,
                    "error", error == null ? "" : error);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public List<Map<String, Object>> listBinaries() {
        return jdbcTemplate.queryForList("select id, file_name, parse_status, parse_error from binaries order by created_at");
    }

    @Transactional
    public Map<String, Object> createScheme(String name, String policy, List<String> binaryIds) {
        Map<String, ParsedBinary> binaries = loadBinaries(binaryIds);
        PlatformPolicy platformPolicy = "STRICT_AMBIGUITY".equals(policy)
                ? PlatformPolicy.STRICT_AMBIGUITY : PlatformPolicy.GNU_LOAD_ORDER;
        Scheme scheme = linkEngine.resolve(new ResolutionInput(name, platformPolicy, binaryIds), binaries);
        String payload = writeJson(scheme);
        jdbcTemplate.update("""
                        insert into schemes(id, name, policy, payload, load_order, created_at)
                        values (?, ?, ?, ?, ?, ?)
                        """, scheme.id, scheme.name, scheme.policy.name(), payload,
                String.join("\n", binaryIds), Instant.now().toString());
        for (int i = 0; i < binaryIds.size(); i++) {
            jdbcTemplate.update("insert into scheme_binaries(scheme_id, binary_id, load_order) values (?, ?, ?)",
                    scheme.id, binaryIds.get(i), i);
        }
        return Map.of("id", scheme.id, "scheme", scheme);
    }

    public Object getScheme(String id) {
        String payload = jdbcTemplate.queryForObject("select payload from schemes where id = ?",
                String.class, id);
        return readJson(payload, Object.class);
    }

    public List<Map<String, Object>> listSchemes() {
        return jdbcTemplate.queryForList("select id, name, policy, created_at from schemes order by created_at");
    }

    public SchemeComparison compare(String beforeId, String afterId) {
        Scheme before = readScheme(beforeId);
        Scheme after = readScheme(afterId);
        return comparator.compare(before, after);
    }

    public Object demo() {
        ParsedBinary strong = new ElfFixtureBuilder().object()
                .section(".text", new byte[]{0, 0, 0, 0})
                .defined("strong_fn", ".text", 1, 0, 0x1000, 8)
                .undefined("weak_helper", 2)
                .reloc(".text", "weak_helper", 7, 0x1008, 0)
                .buildParsed("strong.o");
        ParsedBinary archiveMember = new ElfFixtureBuilder().object()
                .section(".text", new byte[]{0, 0, 0, 0})
                .defined("weak_helper", ".text", 2, 0, 0x2000, 8)
                .buildParsed("helper.o");
        byte[] archiveBytes = new ArchiveFixtureBuilder().member("helper.o",
                new ElfFixtureBuilder().object()
                        .section(".text", new byte[]{0, 0, 0, 0})
                        .defined("weak_helper", ".text", 2, 0, 0x2000, 8)
                        .build()).build();
        ParsedBinary archive = parser.parse(archiveBytes, "libdemo.a");
        ParsedBinary library = new ElfFixtureBuilder().shared("libdemo.so.1")
                .section(".data", new byte[]{0, 0, 0, 0})
                .dynamicDefined("versioned", ".data", 1, 0, 0x3000, 4, "LIBDEMO_1.0", true)
                .dynamicDefined("versioned", ".data", 1, 0, 0x3010, 4, "LIBDEMO_1.1", false)
                .buildParsed("libdemo.so.1");
        Map<String, ParsedBinary> binaries = Map.of("demo-strong", strong, "demo-archive", archive,
                "demo-library", library);
        Scheme scheme = linkEngine.resolve(new ResolutionInput("演示方案：strong + 惰性 archive + 版本别名",
                PlatformPolicy.GNU_LOAD_ORDER, List.of("demo-strong", "demo-archive", "demo-library")), binaries);
        return readJson(writeJson(scheme), Object.class);
    }

    private Scheme readScheme(String id) {
        return readJson(jdbcTemplate.queryForObject("select payload from schemes where id = ?", String.class, id),
                Scheme.class);
    }

    private Map<String, ParsedBinary> loadBinaries(List<String> binaryIds) {
        Map<String, ParsedBinary> result = new java.util.LinkedHashMap<>();
        for (String id : binaryIds) {
            ParsedBinary cached = memoryBinaries.get(id);
            if (cached == null) {
                Map<String, Object> row = jdbcTemplate.queryForMap(
                        "select file_name, storage_path, parse_status from binaries where id = ?", id);
                if (!"OK".equals(row.get("parse_status"))) {
                    throw new ElfReadException("二进制损坏，已隔离: " + id);
                }
                try {
                    byte[] bytes = Files.readAllBytes(Path.of((String) row.get("storage_path")));
                    cached = parser.parse(bytes, (String) row.get("file_name"));
                    memoryBinaries.put(id, cached);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
            result.put(id, cached);
        }
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
