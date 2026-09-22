package com.symbolcourt.engine;

public record DependencyEdge(String fromObjectId, String toName, String kind, String requiredVersion) {
}
