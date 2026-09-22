package com.symbolcourt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symbolcourt.model.InputEntry;
import com.symbolcourt.model.Policy;
import com.symbolcourt.model.ReferenceDiff;
import com.symbolcourt.model.ResolutionResult;
import com.symbolcourt.model.Scheme;
import com.symbolcourt.resolve.ResolverEngine;
import com.symbolcourt.resolve.SchemeComparator;
import com.symbolcourt.storage.BinaryStore;
import com.symbolcourt.storage.SchemeRepository;
import com.symbolcourt.storage.SchemeRepository.StoredScheme;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CourtService {

    private final BinaryStore binaries;
    private final SchemeRepository repo;
    private final ObjectMapper mapper;
    private final FileClassifier classifier;
    private final ResolverEngine engine = new ResolverEngine();
    private final SchemeComparator comparator = new SchemeComparator();

    public CourtService(BinaryStore binaries, SchemeRepository repo, ObjectMapper mapper,
                        FileClassifier classifier) {
        this.binaries = binaries;
        this.repo = repo;
        this.mapper = mapper;
        this.classifier = classifier;
    }

    public BinaryStore.StoredBinary importBinary(byte[] data, String originalName) {
        return binaries.store(data, originalName);
    }

    public String classifyBinary(byte[] data, String name) {
        var kind = classifier.classify(data, name);
        return kind == null ? "UNKNOWN" : kind.name();
    }

    public record CreateSchemeRequest(String name, String parentId,
                                      List<OrderedFile> files, Policy policy) {
        public record OrderedFile(String fileId, String displayName, String kind, Integer ordinal) {
        }
    }

    public record SchemeView(String id, String parentId, String lineageId, String name,
                             int version, Policy policy, Instant createdAt,
                             ResolutionResult result) {
    }

    public SchemeView createScheme(CreateSchemeRequest req) {
        List<CreateSchemeRequest.OrderedFile> files = req.files() == null
                ? List.of() : req.files();
        files = new ArrayList<>(files);
        files.sort((a, b) -> Integer.compare(
                a.ordinal() == null ? 0 : a.ordinal(),
                b.ordinal() == null ? 0 : b.ordinal()));

        String parentId = req.parentId();
        StoredScheme parent = parentId == null ? null : repo.find(parentId);
        String lineageId = parent == null ? UUID.randomUUID().toString() : parent.lineageId();
        int version = repo.nextVersion(lineageId);
        String id = UUID.randomUUID().toString();
        Policy policy = req.policy() == null ? Policy.gnuDefault() : req.policy();

        List<InputEntry> entries = new ArrayList<>();
        Map<String, byte[]> blobs = new LinkedHashMap<>();
        List<SchemeRepository.FileEntry> fileEntries = new ArrayList<>();
        int ordinal = 0;
        for (CreateSchemeRequest.OrderedFile f : files) {
            StoredScheme lookup = null;
            String pathName = f.fileId();
            // Resolve storage path: binaries live under BinaryStore root by file_id.
            Path p = binaries.root().resolve(f.fileId());
            byte[] data = binaries.read(p);
            InputEntry.Kind kind;
            if (f.kind() != null) {
                kind = InputEntry.Kind.valueOf(f.kind());
            } else {
                kind = classifier.classify(data, f.displayName());
            }
            if (kind == null) {
                kind = InputEntry.Kind.OBJECT;
            }
            String unitId = f.fileId();
            entries.add(new InputEntry(ordinal, unitId,
                    f.displayName() == null ? f.fileId() : f.displayName(), kind));
            blobs.put(unitId, data);
            fileEntries.add(new SchemeRepository.FileEntry(f.fileId(),
                    f.displayName() == null ? f.fileId() : f.displayName(), kind.name()));
            ordinal++;
        }

        ResolutionResult result = engine.arbitrate(entries, blobs, policy);
        String resultJson;
        String inputJson;
        try {
            resultJson = mapper.writeValueAsString(result);
            inputJson = mapper.writeValueAsString(files);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        repo.insert(id, parentId, lineageId, req.name() == null ? "方案" : req.name(),
                version, policy, inputJson, resultJson, Instant.now());
        repo.attachFiles(id, fileEntries);
        return new SchemeView(id, parentId, lineageId, req.name(), version, policy,
                Instant.now(), result);
    }

    public SchemeView get(String id) {
        StoredScheme s = repo.find(id);
        if (s == null) {
            return null;
        }
        return new SchemeView(s.id(), s.parentId(), s.lineageId(), s.name(), s.version(),
                repo.policyOf(s), s.createdAt(), repo.resultOf(s));
    }

    public List<SchemeView> list() {
        List<SchemeView> out = new ArrayList<>();
        for (StoredScheme s : repo.recent()) {
            out.add(new SchemeView(s.id(), s.parentId(), s.lineageId(), s.name(), s.version(),
                    repo.policyOf(s), s.createdAt(), null));
        }
        return out;
    }

    public List<SchemeView> lineage(String lineageId) {
        List<SchemeView> out = new ArrayList<>();
        for (StoredScheme s : repo.lineage(lineageId)) {
            out.add(new SchemeView(s.id(), s.parentId(), s.lineageId(), s.name(), s.version(),
                    repo.policyOf(s), s.createdAt(), null));
        }
        return out;
    }

    public List<ReferenceDiff> diff(String schemeIdA, String schemeIdB) {
        ResolutionResult a = repo.resultOf(repo.find(schemeIdA));
        ResolutionResult b = repo.resultOf(repo.find(schemeIdB));
        return comparator.compare(a, b);
    }
}
