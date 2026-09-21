package org.tribunal.model;

import java.util.List;
import java.util.Map;

public record ResolutionStep(int sequence, String phase, String title, String detail,
                             String unitId, String symbol, List<String> activeUnits,
                             Map<String, Object> state) {
}
