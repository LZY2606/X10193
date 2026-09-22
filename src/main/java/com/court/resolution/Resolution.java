package com.court.resolution;

import java.util.ArrayList;
import java.util.List;

/** The verdict for one reference: candidates, why losers lost, winner(s). */
public class Resolution {
    public enum Status {
        RESOLVED,
        AMBIGUOUS,
        UNRESOLVED,
        VERSION_UNSATISFIED,
        COPY_RELOCATION,
        WEAK_UNRESOLVED,
        LOCAL_SELF_BIND
    }

    private final Reference reference;
    private Status status;
    private Provider winner;
    private final List<Provider> tiedWinners = new ArrayList<>();
    private final List<Provider> candidates = new ArrayList<>();
    private final List<CandidateRejection> rejections = new ArrayList<>();
    private final List<RelocationRef> affectedRelocations = new ArrayList<>();
    private String note = "";
    private long resolvedAddress;

    public record RelocationRef(String fileLabel, String sectionDescription,
                                long offset, int typeName, String type, Long addend) { }

    public Resolution(Reference reference) { this.reference = reference; }

    public Reference getReference() { return reference; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Provider getWinner() { return winner; }
    public void setWinner(Provider winner) { this.winner = winner; }
    public List<Provider> getTiedWinners() { return tiedWinners; }
    public List<Provider> getCandidates() { return candidates; }
    public List<CandidateRejection> getRejections() { return rejections; }
    public List<RelocationRef> getAffectedRelocations() { return affectedRelocations; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getResolvedAddress() { return resolvedAddress; }
    public void setResolvedAddress(long resolvedAddress) { this.resolvedAddress = resolvedAddress; }
}
