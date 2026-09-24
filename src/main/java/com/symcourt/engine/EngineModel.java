package com.symcourt.engine;

import java.util.List;

/** JSON-friendly views of an adjudication outcome. */
public final class EngineModel {

    public record ObjectView(
            String name,
            String kind,
            int loadOrder,
            long base,
            boolean extracted,
            List<String> deps) { }

    public record CandidateView(
            String object,
            String binding,
            String version,
            boolean defaultVersion,
            boolean common,
            boolean chosen,
            boolean eliminated,
            boolean ambiguous,
            String reason) { }

    public record Verdict(
            String object,
            String symbol,
            String version,
            String status,
            String chosen,
            long address,
            List<CandidateView> candidates) { }

    public record RelocView(
            String object,
            String section,
            long offset,
            int type,
            String symbol,
            String provider,
            long address,
            String status) { }

    public record StepView(int seq, String kind, String message) { }

    public record VersionNode(String object, String kind, String version, String file) { }

    public record GroupEvent(String signature, String kept, String discarded) { }

    public record Result(
            List<ObjectView> objects,
            List<Verdict> verdicts,
            List<RelocView> relocs,
            List<StepView> steps,
            List<VersionNode> versionGraph,
            List<GroupEvent> groups) { }

    public record CompareEntry(
            String object,
            String symbol,
            String version,
            String change,
            String fromProvider,
            String toProvider,
            long fromAddress,
            long toAddress) { }

    private EngineModel() { }
}
