package com.example.symbolcourt.service;

import com.example.symbolcourt.domain.Models.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlanComparisonService {
    public PlanComparison compare(Plan left, Plan right) {
        Map<String, ReferenceBinding> leftBindings = bindings(left.result());
        Map<String, ReferenceBinding> rightBindings = bindings(right.result());
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(leftBindings.keySet());
        keys.addAll(rightBindings.keySet());
        List<BindingDifference> differences = new ArrayList<>();
        List<String> addressOnly = new ArrayList<>();
        for (String key : keys) {
            ReferenceBinding old = leftBindings.get(key);
            ReferenceBinding updated = rightBindings.get(key);
            if (old == null || updated == null) {
                differences.add(new BindingDifference(old == null ? "ADDED" : "REMOVED", old, updated,
                        "引用集合发生变化"));
            } else if (!Objects.equals(old.providerId(), updated.providerId())) {
                differences.add(new BindingDifference("PROVIDER_CHANGED", old, updated,
                        "引用从 " + old.providerName() + " 改绑到 " + updated.providerName()));
            } else if (old.address() != updated.address()) {
                addressOnly.add(key);
            }
        }
        return new PlanComparison(differences.isEmpty(), List.copyOf(differences),
                List.copyOf(addressOnly));
    }

    private Map<String, ReferenceBinding> bindings(ResolutionResult result) {
        Map<String, Candidate> providerById = result.objects().stream()
                .flatMap(object -> object.symbols().stream())
                .collect(Collectors.toMap(ElfSymbol::providerId, symbol -> new Candidate(
                        symbol.providerId(), symbol.objectId(), symbol.objectName(),
                        symbol.archiveName(), symbol.memberName(), symbol.name(), symbol.versionName(),
                        symbol.versionAliases(), symbol.defaultVersion(), symbol.binding(),
                        symbol.visibility(), symbol.symbolType(), symbol.sectionName(),
                        symbol.sectionIndex(), symbol.value(), symbol.size(), 0, symbol.common(),
                        true, null, Map.of()), (a, b) -> a));
        Map<String, ReferenceBinding> bindings = new LinkedHashMap<>();
        for (ResolutionDecision decision : result.decisions()) {
            String providerId = decision.chosenProviderIds().size() == 1
                    ? decision.chosenProviderIds().get(0) : null;
            Candidate provider = providerId == null ? null : providerById.get(providerId);
            for (AffectedRelocation relocation : decision.affectedRelocations()) {
                String name = relocation.objectName()
                        + (relocation.memberName() == null ? "" : "(" + relocation.memberName() + ")")
                        + ":" + relocation.sectionName() + "+0x"
                        + Long.toHexString(relocation.offset()) + ":" + relocation.symbolName();
                bindings.put(name, new ReferenceBinding(relocation.referenceId(),
                        relocation.objectName(), relocation.memberName(), relocation.symbolName(),
                        relocation.requestedVersion(), decision.status(), providerId,
                        provider == null ? null : provider.objectName(),
                        provider == null ? 0 : provider.value(), relocation.type(), relocation.copy()));
            }
        }
        return bindings;
    }
}
