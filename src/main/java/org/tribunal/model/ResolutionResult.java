package org.tribunal.model;

import java.util.List;

public record ResolutionResult(List<String> orderedUnitIds, List<ElfFile> units,
                               List<DependencyEdge> dependencyEdges, List<VersionEdge> versionEdges,
                               List<Resolution> resolutions, List<ResolutionStep> steps,
                               List<String> warnings) {
}
