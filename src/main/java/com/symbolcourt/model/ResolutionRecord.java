package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

/** The full verdict for one reference: candidates, eliminations, winner, relocations. */
public final class ResolutionRecord {
    private final Reference reference;
    private final List<Candidate> candidates = new ArrayList<>();
    private Candidate winner;
    private ResolutionStatus status;
    private String explanation;
    private final List<String> affectedRelocations = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public ResolutionRecord(Reference reference) {
        this.reference = reference;
    }

    public Reference getReference() { return reference; }
    public List<Candidate> getCandidates() { return candidates; }
    public Candidate getWinner() { return winner; }
    public ResolutionStatus getStatus() { return status; }
    public String getExplanation() { return explanation; }
    public List<String> getAffectedRelocations() { return affectedRelocations; }
    public List<String> getNotes() { return notes; }

    public void setWinner(Candidate winner) { this.winner = winner; }
    public void setStatus(ResolutionStatus status) { this.status = status; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
