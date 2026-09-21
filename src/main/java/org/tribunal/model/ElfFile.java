package org.tribunal.model;

import java.util.List;
import java.util.Map;

public record ElfFile(String unitId, String unitName, BinaryKind kind, int elfType, int machine,
                      ElfClass elfClass, boolean littleEndian,
                      List<SymbolDefinition> definitions, List<SymbolReference> references,
                      List<RelocationInfo> relocations, List<VersionDefinition> versionDefinitions,
                      List<VersionRequirement> versionRequirements, List<VersionEdge> versionEdges,
                      List<DependencyEdge> dependencies, List<String> comdatGroups,
                      Map<String, String> metadata) {
}
