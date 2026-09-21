package com.example.tribunal.service;

import com.example.tribunal.core.fixture.ElfFixtureBuilder;
import com.example.tribunal.core.fixture.ArchiveFixtureBuilder;
import com.example.tribunal.core.model.TribunalModels.*;
import com.example.tribunal.core.resolution.BinaryLoader;
import com.example.tribunal.core.resolution.ResolutionEngine;
import com.example.tribunal.db.SchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.*;

@Service
public class TribunalService {
    private final SchemeRepository repository;

    public TribunalService(SchemeRepository repository) {
        this.repository = repository;
    }

    public Scheme create(String name, List<MultipartFile> files, PlatformPolicy policy) {
        List<BinaryLoader.LoadedBinary> loaded = new ArrayList<>();
        List<LoadedInput> inputs = new ArrayList<>();
        BinaryLoader loader = new BinaryLoader();
        int order = 1;
        try {
            for (MultipartFile file : files) {
                BinaryLoader.LoadedBinary binary = loader.load("in-" + order, file.getOriginalFilename(), file.getBytes(), order);
                loaded.add(binary);
                inputs.add(binary.input());
                order++;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to read uploads", e);
        }
        String id = UUID.randomUUID().toString();
        Scheme scheme = new ResolutionEngine(loaded, policy == null ? PlatformPolicy.gnu() : policy, id)
                .resolve(name == null || name.isBlank() ? "未命名方案" : name, Instant.now().toString(), inputs);
        repository.save(scheme);
        return scheme;
    }

    public Scheme sample() {
        ElfFixtureBuilder mainObject = ElfFixtureBuilder.relocatable("main.o");
        int strongRef = mainObject.undefined("strong_fn", ElfFixtureBuilder.STB_GLOBAL);
        int weakRef = mainObject.undefined("weak_data", ElfFixtureBuilder.STB_WEAK);
        mainObject.relocation(".text", strongRef, ElfFixtureBuilder.R_X86_64_PC32, 0, -4);
        mainObject.relocation(".text", weakRef, ElfFixtureBuilder.R_X86_64_PC32, 4, -4);

        ElfFixtureBuilder provider = ElfFixtureBuilder.relocatable("provider.o");
        provider.defined("strong_fn", ElfFixtureBuilder.STB_GLOBAL, ElfFixtureBuilder.STT_FUNC,
                ElfFixtureBuilder.STV_DEFAULT, 1, 0x20, 16);
        provider.defined("weak_data", ElfFixtureBuilder.STB_WEAK, ElfFixtureBuilder.STT_OBJECT,
                ElfFixtureBuilder.STV_DEFAULT, 1, 0x30, 8);

        BinaryLoader loader = new BinaryLoader();
        BinaryLoader.LoadedBinary a = loader.load("in-1", "main.o", mainObject.build(), 1);
        BinaryLoader.LoadedBinary b = loader.load("in-2", "provider.o", provider.build(), 2);
        List<LoadedInput> inputs = List.of(a.input(), b.input());
        String id = UUID.randomUUID().toString();
        Scheme scheme = new ResolutionEngine(List.of(a, b), PlatformPolicy.gnu(), id)
                .resolve("示例：strong/weak", Instant.now().toString(), inputs);
        repository.save(scheme);
        return scheme;
    }

    public Scheme get(String id) { return repository.find(id); }
    public List<StoredScheme> list() { return repository.list(); }

    public SchemeComparison compare(String leftId, String rightId) {
        Scheme left = repository.find(leftId);
        Scheme right = repository.find(rightId);
        Map<String, SymbolResolution> leftMap = new LinkedHashMap<>();
        Map<String, SymbolResolution> rightMap = new LinkedHashMap<>();
        left.resolutions().forEach(r -> leftMap.put(r.key().name() + "\0" + r.key().version(), r));
        right.resolutions().forEach(r -> rightMap.put(r.key().name() + "\0" + r.key().version(), r));
        List<ReferenceChange> changes = new ArrayList<>();
        for (String keyId : new LinkedHashSet<String>() {{ addAll(leftMap.keySet()); addAll(rightMap.keySet()); }}) {
            SymbolResolution a = leftMap.get(keyId);
            SymbolResolution b = rightMap.get(keyId);
            if (a == null || b == null) {
                changes.add(new ReferenceChange((a != null ? a : b).key(), "reference-set",
                        firstRef(a != null ? a : b), provider(a), provider(b), "引用集合发生变化"));
                continue;
            }
            String pa = provider(a);
            String pb = provider(b);
            if (!Objects.equals(pa, pb)) {
                for (RelocationRef ref : b.relocations()) changes.add(new ReferenceChange(b.key(), "provider-changed",
                        ref, pa, pb, "解析提供者改变"));
            } else if (a.selected() != null && b.selected() != null && a.selected().value() != b.selected().value()) {
                for (RelocationRef ref : b.relocations()) changes.add(new ReferenceChange(b.key(), "address-only",
                        ref, pa, pb, "提供者相同，但符号值/地址变化"));
            }
        }
        int changed = (int) changes.stream().filter(c -> c.change().equals("provider-changed")).count();
        int addresses = (int) changes.stream().filter(c -> c.change().equals("address-only")).count();
        return new SchemeComparison(List.copyOf(changes), changed, addresses);
    }

    private String provider(SymbolResolution resolution) {
        return resolution.selected() == null ? resolution.outcome().name() : resolution.selected().module().display();
    }

    private RelocationRef firstRef(SymbolResolution resolution) {
        return resolution.relocations().isEmpty() ? null : resolution.relocations().get(0);
    }
}
