package arb.engine;

import java.util.ArrayList;
import java.util.List;

public class Resolution {
    private String reference;
    private String requestedVersion;
    private boolean defaultVersionRequest;
    private String source;
    private String status;
    private String detail;
    private String selectedProvider;
    private String selectedMember;
    private String selectedVersion;
    private long address;
    private boolean ambiguous;
    private boolean copyRelocation;
    private List<Candidate> candidates = new ArrayList<>();
    private List<RelocRef> affectedRelocations = new ArrayList<>();

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getRequestedVersion() {
        return requestedVersion;
    }

    public void setRequestedVersion(String requestedVersion) {
        this.requestedVersion = requestedVersion;
    }

    public boolean isDefaultVersionRequest() {
        return defaultVersionRequest;
    }

    public void setDefaultVersionRequest(boolean defaultVersionRequest) {
        this.defaultVersionRequest = defaultVersionRequest;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getSelectedProvider() {
        return selectedProvider;
    }

    public void setSelectedProvider(String selectedProvider) {
        this.selectedProvider = selectedProvider;
    }

    public String getSelectedMember() {
        return selectedMember;
    }

    public void setSelectedMember(String selectedMember) {
        this.selectedMember = selectedMember;
    }

    public String getSelectedVersion() {
        return selectedVersion;
    }

    public void setSelectedVersion(String selectedVersion) {
        this.selectedVersion = selectedVersion;
    }

    public long getAddress() {
        return address;
    }

    public void setAddress(long address) {
        this.address = address;
    }

    public boolean isAmbiguous() {
        return ambiguous;
    }

    public void setAmbiguous(boolean ambiguous) {
        this.ambiguous = ambiguous;
    }

    public boolean isCopyRelocation() {
        return copyRelocation;
    }

    public void setCopyRelocation(boolean copyRelocation) {
        this.copyRelocation = copyRelocation;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    public List<RelocRef> getAffectedRelocations() {
        return affectedRelocations;
    }

    public void setAffectedRelocations(List<RelocRef> affectedRelocations) {
        this.affectedRelocations = affectedRelocations;
    }
}
