package com.example.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

public class Verdict {
    public Policy policy;
    public final List<UnitInfo> units = new ArrayList<>();
    public final List<StepEvent> steps = new ArrayList<>();
    public final List<Reference> references = new ArrayList<>();
    public final List<CopyRelocView> copyRelocations = new ArrayList<>();
    public final List<GroupOutcome> groups = new ArrayList<>();
    public final List<UnresolvedVersion> unresolvedVersions = new ArrayList<>();
    public final List<GraphNode> graphNodes = new ArrayList<>();
    public final List<GraphEdge> graphEdges = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
}
