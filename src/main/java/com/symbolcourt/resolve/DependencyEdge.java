package com.symbolcourt.resolve;

public final class DependencyEdge {
    public final String from;
    public final String to;
    public final String neededName;
    public final boolean satisfied;

    public DependencyEdge(String from, String to, String neededName, boolean satisfied) {
        this.from = from;
        this.to = to;
        this.neededName = neededName;
        this.satisfied = satisfied;
    }
}
