package court.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import court.domain.Scheme;
import court.domain.StoredInput;
import court.elf.BinaryLoader;
import court.elf.LoadedFile;
import court.link.LinkEngine;
import court.link.LinkPolicy;
import court.link.LinkReport;
import court.store.SchemeRepository;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class SchemeService {

    private final SchemeRepository repo;
    private final BinaryLoader loader;
    private final LinkEngine engine;
    private final ObjectMapper mapper;

    public SchemeService(SchemeRepository repo, BinaryLoader loader, LinkEngine engine, ObjectMapper mapper) {
        this.repo = repo;
        this.loader = loader;
        this.engine = engine;
        this.mapper = mapper;
    }

    public static final class Upload {
        public String fileName;
        public byte[] data;
        public Upload(String fileName, byte[] data) { this.fileName = fileName; this.data = data; }
    }

    public Scheme createVersion(String name, LinkPolicy policy, List<Upload> uploads, Long parentId) {
        List<LoadedFile> files = new ArrayList<>();
        List<String> order = new ArrayList<>();
        StringBuilder hashes = new StringBuilder();
        for (int i = 0; i < uploads.size(); i++) {
            Upload up = uploads.get(i);
            LoadedFile lf = loader.load(i, up.fileName, up.data);
            files.add(lf);
            order.add(up.fileName);
            hashes.append(sha256(up.data)).append(' ').append(up.fileName).append('\n');
        }
        LinkReport report = engine.resolve(files, name, policy == null ? new LinkPolicy() : policy);
        String reportJson;
        try {
            reportJson = mapper.writeValueAsString(report);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize report", e);
        }
        Scheme s = new Scheme();
        s.name = name;
        s.version = repo.nextVersion(name);
        s.parentId = parentId;
        s.policy = report.policy;
        s.loadOrder = order;
        s.inputHashes = hashes.toString();
        s.id = repo.insertScheme(s, reportJson);
        for (int i = 0; i < uploads.size(); i++) {
            Upload up = uploads.get(i);
            LoadedFile lf = files.get(i);
            repo.addInput(s.id, i, up.fileName, lf.kind, up.data.length, sha256(up.data), up.data);
        }
        repo.setMeta(s.id, report.policySummary);
        return s;
    }

    public LinkReport loadReport(Scheme s) {
        try {
            return mapper.readValue(s.reportJson, LinkReport.class);
        } catch (Exception e) {
            throw new IllegalStateException("stored report unreadable", e);
        }
    }

    public List<Scheme> list() { return repo.listSchemes(); }
    public Scheme get(long id) { return repo.getScheme(id); }
    public List<StoredInput> inputs(long id) { return repo.inputs(id); }

    public List<Upload> copyInputs(long sourceId) {
        List<Upload> ups = new ArrayList<>();
        for (StoredInput in : repo.inputs(sourceId)) ups.add(new Upload(in.fileName, in.data));
        return ups;
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
