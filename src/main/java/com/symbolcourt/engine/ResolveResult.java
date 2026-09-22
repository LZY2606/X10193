package com.symbolcourt.engine;

import java.util.List;

public record ResolveResult(
        String schemeId,
        String policyName,
        String platformMachine,
        List<ObjectView> objects,
        List<DependencyEdge> dependencies,
        List<ResolutionEntry> entries,
        List<SymbolTableRow> symbolTable,
        List<TimelineEvent> timeline,
        List<Diagnostic> diagnostics) {
}
