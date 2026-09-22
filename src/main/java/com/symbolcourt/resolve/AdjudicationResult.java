package com.symbolcourt.resolve;

import com.symbolcourt.elf.VersionDef;
import com.symbolcourt.elf.VersionNeed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable output of one scheme run. */
public final class AdjudicationResult {
    public final String schemeName;
    public final String policy;
    public final List<String> loadOrder = new ArrayList<>();
    public final List<LoadedUnit> units = new ArrayList<>();
    public final List<DependencyEdge> dependencyEdges = new ArrayList<>();
    public final List<Verdict> verdicts = new ArrayList<>();
    public final List<DuplicateError> duplicateErrors = new ArrayList<>();
    public final List<PlayEvent> events = new ArrayList<>();
    public final List<ParseFailure> parseFailures = new ArrayList<>();
    public final Map<String, VersionDef> versionDefsByUnit = new LinkedHashMap<>();
    public final List<VersionNeed> allVersionNeeds = new ArrayList<>();
    public final List<String> unresolvedVersionNeeds = new ArrayList<>();

    public AdjudicationResult(String schemeName, String policy) {
        this.schemeName = schemeName;
        this.policy = policy;
    }

    public boolean hasErrors() {
        return !duplicateErrors.isEmpty()
                || verdicts.stream().anyMatch(v -> v.unresolved)
                || !unresolvedVersionNeeds.isEmpty();
    }
}
