package court.symbol.model;

import java.util.List;

public record SchemeComparison(String beforeSchemeId, String afterSchemeId,
                               List<ReferenceComparison> providerChanges,
                               List<ReferenceComparison> addressChanges,
                               List<ReferenceComparison> statusChanges) {
}
