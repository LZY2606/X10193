package org.tribunal.model;

import java.util.List;

public record CandidateOutcome(String providerKey, String unitId, String unitName, String binding,
                               String visibility, String versionName, boolean defaultVersion,
                               String sectionName, String groupSignature, long size, long value,
                               boolean selected, List<String> rejectionReasons) {
}
