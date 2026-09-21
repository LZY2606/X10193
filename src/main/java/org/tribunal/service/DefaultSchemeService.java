package org.tribunal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.tribunal.model.ArchiveMember;
import org.tribunal.model.ParsedBinary;
import org.tribunal.model.PlatformPolicy;
import org.tribunal.model.Resolution;
import org.tribunal.model.ResolutionResult;
import org.tribunal.model.Scheme;
import org.tribunal.model.SchemeComparison;
import org.tribunal.repository.BinaryRepository;
import org.tribunal.repository.SchemeRepository;

@Service
public class DefaultSchemeService implements SchemeService {
    private final BinaryRepository binaryRepository;
    private final SchemeRepository schemeRepository;
    private final BinaryService binaryService;
    private final LinkResolver resolver;
    private final ObjectMapper objectMapper;

    public DefaultSchemeService(BinaryRepository binaryRepository, SchemeRepository schemeRepository,
                                BinaryService binaryService, LinkResolver resolver, ObjectMapper objectMapper) {
        this.binaryRepository = binaryRepository;
        this.schemeRepository = schemeRepository;
        this.binaryService = binaryService;
        this.resolver = resolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public Scheme create(String label, String policyName, List<Long> orderedBinaryIds) {
        List<LinkResolver.LoadedBinary> loaded = new ArrayList<>();
        int ordinal = 0;
        for (Long binaryId : orderedBinaryIds) {
            ParsedBinary stored = binaryRepository.findById(binaryId)
                    .orElseThrow(() -> new IllegalArgumentException("Binary not found: " + binaryId));
            byte[] payload = binaryRepository.readPayload(binaryId);
            ParsedBinary reparsed = binaryService.parseInMemory(stored.name(), payload);
            List<ArchiveMember> members = reparsed.members() == null ? List.of() : reparsed.members();
            loaded.add(new LinkResolver.LoadedBinary(stored, reparsed.elfFile(), members, ordinal++));
        }
        ResolutionResult result = resolver.resolve(loaded, policy(policyName));
        try {
            String json = objectMapper.writeValueAsString(result);
            Scheme saved = schemeRepository.save(label == null || label.isBlank()
                    ? "scheme-" + System.currentTimeMillis() : label, policyName, orderedBinaryIds, json);
            return new Scheme(saved.id(), saved.label(), saved.policy(), orderedBinaryIds, result);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot persist immutable scheme result", ex);
        }
    }

    @Override
    public Scheme get(long id) {
        return schemeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Scheme not found: " + id));
    }

    @Override
    public ResolutionResult getResult(long id) {
        try {
            return objectMapper.readValue(schemeRepository.findResultJson(id), ResolutionResult.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read frozen scheme result", ex);
        }
    }

    @Override
    public List<Scheme> list() {
        return schemeRepository.findAllMetadata();
    }

    @Override
    public SchemeComparison compare(long schemeA, long schemeB) {
        Map<String, Resolution> a = resolutionMap(getResult(schemeA));
        Map<String, Resolution> b = resolutionMap(getResult(schemeB));
        List<SchemeComparison.ChangedProvider> changed = new ArrayList<>();
        List<SchemeComparison.AddressOnlyChange> addressOnly = new ArrayList<>();
        List<SchemeComparison.AmbiguityChange> ambiguity = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            Resolution left = a.get(key);
            Resolution right = b.get(key);
            if (left == null || right == null) {
                continue;
            }
            if (!Objects.equals(left.status(), right.status())) {
                ambiguity.add(new SchemeComparison.AmbiguityChange(key, left.status(), right.status()));
            }
            if (!Objects.equals(left.selectedProviderKeys(), right.selectedProviderKeys())) {
                changed.add(new SchemeComparison.ChangedProvider(key, left.requesterName(), left.symbol(),
                        left.requestedVersion(), left.selectedProviderKeys(), right.selectedProviderKeys(),
                        "引用绑定到不同提供者或歧义集合"));
            } else if (left.selectedProviderKeys().size() == 1) {
                Long leftAddress = addressOf(left, getResult(schemeA));
                Long rightAddress = addressOf(right, getResult(schemeB));
                if (!Objects.equals(leftAddress, rightAddress)) {
                    addressOnly.add(new SchemeComparison.AddressOnlyChange(key, left.requesterName(),
                            left.symbol(), left.selectedProviderKeys(), leftAddress, rightAddress));
                }
            }
        }
        return new SchemeComparison(changed, addressOnly,
                a.keySet().stream().filter(key -> !b.containsKey(key)).toList(),
                b.keySet().stream().filter(key -> !a.containsKey(key)).toList(), ambiguity);
    }

    private Map<String, Resolution> resolutionMap(ResolutionResult result) {
        return result.resolutions().stream()
                .collect(Collectors.toMap(resolution -> resolution.requesterId() + "\u0000"
                                + resolution.symbol() + "\u0000" + Objects.toString(resolution.requestedVersion(), ""),
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Long addressOf(Resolution resolution, ResolutionResult result) {
        String providerKey = resolution.selectedProviderKeys().get(0);
        return result.units().stream().flatMap(unit -> unit.definitions().stream())
                .filter(definition -> definition.providerKey().equals(providerKey))
                .findFirst().map(definition -> definition.value()).orElse(null);
    }

    @Override
    public PlatformPolicy policy(String name) {
        if ("strict".equalsIgnoreCase(name)) {
            return PlatformPolicy.strict();
        }
        return PlatformPolicy.gnuLike();
    }
}
