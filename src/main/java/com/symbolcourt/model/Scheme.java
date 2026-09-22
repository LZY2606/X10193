package com.symbolcourt.model;

import java.time.Instant;

/** Persisted immutable scheme version. */
public final class Scheme {
    private final String id;
    private final String parentId;
    private final String name;
    private final int version;
    private final Policy policy;
    private final Instant createdAt;
    private ResolutionResult result;
    private String inputSpecJson;

    public Scheme(String id, String parentId, String name, int version, Policy policy,
                  Instant createdAt, String inputSpecJson) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.version = version;
        this.policy = policy;
        this.createdAt = createdAt;
        this.inputSpecJson = inputSpecJson;
    }

    public String getId() { return id; }
    public String getParentId() { return parentId; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public Policy getPolicy() { return policy; }
    public Instant getCreatedAt() { return createdAt; }
    public ResolutionResult getResult() { return result; }
    public String getInputSpecJson() { return inputSpecJson; }

    public void setResult(ResolutionResult result) { this.result = result; }
}
