package com.example.symbolcourt.service;

import com.example.symbolcourt.domain.Models.*;
import com.example.symbolcourt.elf.ArchiveParser;
import com.example.symbolcourt.elf.ElfParser;
import com.example.symbolcourt.engine.LinkPolicy;
import com.example.symbolcourt.engine.LinkRequest;
import com.example.symbolcourt.engine.LinkResolver;
import com.example.symbolcourt.repository.BinaryRepository;
import com.example.symbolcourt.repository.PlanRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
public class PlanService {
    private final BinaryRepository binaryRepository;
    private final PlanRepository planRepository;
    private final ElfParser elfParser;
    private final ArchiveParser archiveParser;
    private final LinkResolver resolver;

    public PlanService(BinaryRepository binaryRepository, PlanRepository planRepository,
                       ElfParser elfParser, ArchiveParser archiveParser, LinkResolver resolver) {
        this.binaryRepository = binaryRepository;
        this.planRepository = planRepository;
        this.elfParser = elfParser;
        this.archiveParser = archiveParser;
        this.resolver = resolver;
    }

    public Plan createFromBinaries(String name, String policy, List<InputBinary> binaries,
                                   List<String> orderedNames) {
        List<InputBinary> ordered = orderBinaries(binaries, orderedNames);
        List<ParsedObject> explicit = new ArrayList<>();
        List<ArchiveFile> archives = new ArrayList<>();
        List<LoadOrderEntry> entries = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            InputBinary binary = ordered.get(i);
            if (archiveParser.isArchive(binary.content())) {
                ArchiveFile archive = archiveParser.parse(binary.id(), binary.displayName(),
                        binary.path(), binary.content());
                archives.add(archive);
                entries.add(new LoadOrderEntry(binary.id(), binary.displayName(), i, "ARCHIVE", null));
            } else {
                ParsedObject object = elfParser.parse(binary.id(), binary.displayName(), binary.path(),
                        binary.content());
                explicit.add(object);
                entries.add(new LoadOrderEntry(binary.id(), binary.displayName(), i, "ELF", null));
            }
        }
        LinkPolicy linkPolicy = parsePolicy(policy);
        String planId = UUID.randomUUID().toString();
        ResolutionResult result = resolver.resolve(new LinkRequest(planId, explicit, archives,
                List.copyOf(entries), linkPolicy));
        Plan plan = new Plan(planId, name, linkPolicy.name(), Instant.now().toString(),
                List.copyOf(entries), result);
        return planRepository.save(plan);
    }

    public Plan createFromUploads(String name, String policy, List<StoredUpload> uploads,
                                  List<String> orderedNames) {
        List<InputBinary> binaries = new ArrayList<>();
        for (StoredUpload upload : uploads) {
            InputBinary.Kind kind = archiveParser.isArchive(upload.content())
                    ? InputBinary.Kind.ARCHIVE : InputBinary.Kind.ELF;
            binaries.add(binaryRepository.save(upload.fileName(), upload.fileName(),
                    upload.content(), kind));
        }
        return createFromBinaries(name, policy, binaries, orderedNames);
    }

    public Plan get(String id) {
        return planRepository.findById(id);
    }

    public List<Plan> list() {
        return planRepository.findAll();
    }

    private List<InputBinary> orderBinaries(List<InputBinary> binaries, List<String> orderedNames) {
        if (orderedNames == null || orderedNames.isEmpty()) {
            return binaries;
        }
        Map<String, InputBinary> byName = new LinkedHashMap<>();
        Map<String, InputBinary> byId = new LinkedHashMap<>();
        for (InputBinary binary : binaries) {
            byName.put(binary.displayName(), binary);
            byId.put(binary.id(), binary);
        }
        List<InputBinary> result = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (String orderedName : orderedNames) {
            String key = orderedName.trim();
            InputBinary binary = byName.containsKey(key) ? byName.get(key) : byId.get(key);
            if (binary != null) {
                result.add(binary);
                used.add(binary.id());
            }
        }
        binaries.stream().filter(binary -> !used.contains(binary.id())).forEach(result::add);
        return result;
    }

    private LinkPolicy parsePolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return LinkPolicy.GNU;
        }
        try {
            return LinkPolicy.valueOf(policy);
        } catch (IllegalArgumentException ex) {
            return LinkPolicy.GNU;
        }
    }

    public record StoredUpload(String fileName, byte[] content) {
        public static StoredUpload of(String fileName, byte[] content) {
            return new StoredUpload(fileName, content);
        }
    }
}
