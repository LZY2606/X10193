package org.tribunal.model;

import java.util.List;

public record SchemeComparison(List<ChangedProvider> changedProviders, List<AddressOnlyChange> addressOnly,
                               List<String> onlyInA, List<String> onlyInB, List<AmbiguityChange> ambiguityChanges) {
    public record ChangedProvider(String requestKey, String requesterName, String symbol,
                                  String requestedVersion, List<String> before, List<String> after,
                                  String detail) {}
    public record AddressOnlyChange(String requestKey, String requesterName, String symbol,
                                    List<String> provider, Long beforeAddress, Long afterAddress) {}
    public record AmbiguityChange(String requestKey, String beforeStatus, String afterStatus) {}
}
