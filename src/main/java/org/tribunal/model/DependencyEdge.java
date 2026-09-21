package org.tribunal.model;

public record DependencyEdge(String fromUnitId, String toLibrary, String edgeType) {
}
