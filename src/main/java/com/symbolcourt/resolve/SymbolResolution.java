package com.symbolcourt.resolve;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SymbolResolution {
    public SymbolKey key;
    public String name;
    public String requestedVersion;
    public ResolutionStatus status;
    public Candidate chosen;
    public final List<CandidateEvaluation> evaluations = new ArrayList<>();
    public final Set<String> referencedBy = new LinkedHashSet<>();
    public String note;
    public boolean weakReference;
    public String unsatisfiedVersionFoundAt;

    public List<Candidate> acceptedCandidates() {
        List<Candidate> out = new ArrayList<>();
        for (CandidateEvaluation e : evaluations) if (e.accepted) out.add(e.candidate);
        return out;
    }
}
