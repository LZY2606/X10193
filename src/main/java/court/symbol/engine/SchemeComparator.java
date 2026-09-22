package court.symbol.engine;

import court.symbol.model.ResolutionDecision;
import court.symbol.model.Scheme;
import court.symbol.model.SymbolProvider;
import court.symbol.model.SymbolReference;
import court.symbol.model.ReferenceComparison;
import court.symbol.model.SchemeComparison;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchemeComparator {
    public SchemeComparison compare(Scheme before, Scheme after) {
        Map<String, Binding> beforeBindings = bindings(before);
        Map<String, Binding> afterBindings = bindings(after);
        List<ReferenceComparison> providerChanges = new ArrayList<>();
        List<ReferenceComparison> addressChanges = new ArrayList<>();
        List<ReferenceComparison> statusChanges = new ArrayList<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>(beforeBindings.keySet());
        keys.addAll(afterBindings.keySet());
        for (String key : keys) {
            Binding left = beforeBindings.get(key);
            Binding right = afterBindings.get(key);
            if (left == null || right == null || !left.status().equals(right.status())) {
                statusChanges.add(new ReferenceComparison(key, symbolName(key),
                        left == null ? "" : left.provider(), right == null ? "" : right.provider(),
                        left == null ? -1 : left.address(), right == null ? -1 : right.address(),
                        "STATUS_CHANGED",
                        "引用存在性或未解析/weak/common/歧义状态发生变化"));
            } else if (!left.provider().equals(right.provider())) {
                providerChanges.add(new ReferenceComparison(key, symbolName(key), left.provider(),
                        right.provider(), left.address(), right.address(), "PROVIDER_CHANGED",
                        "该引用绑定到了不同提供者"));
            } else if (left.address() != right.address()) {
                addressChanges.add(new ReferenceComparison(key, symbolName(key), left.provider(),
                        right.provider(), left.address(), right.address(), "ADDRESS_CHANGED",
                        "提供者相同，但符号值或加载顺序推导出的地址不同"));
            }
        }
        return new SchemeComparison(before.id, after.id, providerChanges, addressChanges, statusChanges);
    }

    private Map<String, Binding> bindings(Scheme scheme) {
        Map<String, Binding> result = new LinkedHashMap<>();
        for (ResolutionDecision decision : scheme.decisions) {
            for (SymbolReference reference : decision.references()) {
                SymbolProvider provider = decision.chosen().isEmpty() ? null : decision.chosen().get(0);
                String label = provider == null ? "（无）" : provider.label();
                long address = provider == null ? 0 : provider.symbol().value;
                result.put(stableReferenceKey(reference),
                        new Binding(decision.name(), label, address, decision.status()));
            }
        }
        return result;
    }

    private String stableReferenceKey(SymbolReference reference) {
        return reference.unit().sourceFile() + ":" + reference.relocation().sectionName + ":"
                + reference.relocation().offset + ":" + reference.name() + ":"
                + reference.requestedVersion();
    }

    private String symbolName(String key) {
        int colon = key.indexOf(':');
        int next = key.indexOf(':', colon + 1);
        int third = key.indexOf(':', next + 1);
        return key.substring(third + 1, key.indexOf(':', third + 1));
    }

    private record Binding(String symbol, String provider, long address, String status) {
    }
}
