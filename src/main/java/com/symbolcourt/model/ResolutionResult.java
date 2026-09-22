package com.symbolcourt.model;

import com.symbolcourt.elf.ElfFile;
import com.symbolcourt.elf.SectionGroup;
import com.symbolcourt.elf.VersionDefinition;
import com.symbolcourt.elf.VersionRequirement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable output of arbitrating one scheme. */
public final class ResolutionResult {
    private final Policy policy;
    private final List<LoadUnit> units = new ArrayList<>();
    private final List<ResolutionRecord> records = new ArrayList<>();
    private final List<ResolveEvent> events = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, VersionGraphNode> versionGraph = new LinkedHashMap<>();
    private final Map<String, Object> symbolTableSnapshot = new LinkedHashMap<>();

    public ResolutionResult(Policy policy) {
        this.policy = policy;
    }

    public Policy getPolicy() { return policy; }
    public List<LoadUnit> getUnits() { return units; }
    public List<ResolutionRecord> getRecords() { return records; }
    public List<ResolveEvent> getEvents() { return events; }
    public List<String> getWarnings() { return warnings; }
    public Map<String, VersionGraphNode> getVersionGraph() { return versionGraph; }
    public Map<String, Object> getSymbolTableSnapshot() { return symbolTableSnapshot; }

    /** Serializable dependency/version graph node for the page. */
    public static final class VersionGraphNode {
        private final String id;
        private final String label;
        private final String kind;
        private final List<String> edges = new ArrayList<>();
        private final Map<String, Object> meta = new LinkedHashMap<>();

        public VersionGraphNode(String id, String label, String kind) {
            this.id = id;
            this.label = label;
            this.kind = kind;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public String getKind() { return kind; }
        public List<String> getEdges() { return edges; }
        public Map<String, Object> getMeta() { return meta; }
    }
}
