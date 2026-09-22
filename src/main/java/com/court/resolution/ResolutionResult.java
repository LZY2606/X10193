package com.court.resolution;

import com.court.model.ElfFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Full output of adjudicating one plan. */
public class ResolutionResult {
    private String planName;
    private String planVersion;
    private Policies policies;
    private final List<ElfFile> activeFiles = new ArrayList<>();
    private final List<Quarantine> quarantined = new ArrayList<>();
    private final List<Resolution> resolutions = new ArrayList<>();
    private final List<TraceEvent> timeline = new ArrayList<>();
    private final Map<String, Object> dependencyGraph = new LinkedHashMap<>();
    private final Map<String, Object> versionGraph = new LinkedHashMap<>();
    private final List<Map<String, Object>> symbolTableSnapshots = new ArrayList<>();

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getPlanVersion() { return planVersion; }
    public void setPlanVersion(String planVersion) { this.planVersion = planVersion; }
    public Policies getPolicies() { return policies; }
    public void setPolicies(Policies policies) { this.policies = policies; }
    public List<ElfFile> getActiveFiles() { return activeFiles; }
    public List<Quarantine> getQuarantined() { return quarantined; }
    public List<Resolution> getResolutions() { return resolutions; }
    public List<TraceEvent> getTimeline() { return timeline; }
    public Map<String, Object> getDependencyGraph() { return dependencyGraph; }
    public Map<String, Object> getVersionGraph() { return versionGraph; }
    public List<Map<String, Object>> getSymbolTableSnapshots() { return symbolTableSnapshots; }

    public int unresolvedCount() {
        int n = 0;
        for (Resolution r : resolutions) {
            if (r.getStatus() == Resolution.Status.UNRESOLVED
                    || r.getStatus() == Resolution.Status.VERSION_UNSATISFIED) n++;
        }
        return n;
    }

    public int ambiguousCount() {
        int n = 0;
        for (Resolution r : resolutions) if (r.getStatus() == Resolution.Status.AMBIGUOUS) n++;
        return n;
    }
}
