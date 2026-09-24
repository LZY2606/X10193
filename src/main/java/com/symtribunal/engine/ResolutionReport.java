package com.symtribunal.engine;

import java.util.ArrayList;
import java.util.List;

/** Immutable result of one scheme. Persisted as JSON; reloading a scheme never drifts. */
public class ResolutionReport {
    public String schemeId;
    public String schemeName;
    public String policy;
    public String machine;
    public String createdAt;
    public List<UnitView> units = new ArrayList<>();
    public List<Reference> references = new ArrayList<>();
    public List<StepEvent> steps = new ArrayList<>();
    public List<VersionNode> versionGraph = new ArrayList<>();
    /** Edges of the dependency graph: {from,to,kind,label}. */
    public List<GraphEdge> graph = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public List<String> isolatedErrors = new ArrayList<>();

    public static class GraphEdge {
        public String from;
        public String to;
        public String kind;
        public String label;
    }
}
