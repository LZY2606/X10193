package com.symbolcourt.resolve;

import com.symbolcourt.model.Candidate;
import com.symbolcourt.model.LoadUnit;
import com.symbolcourt.model.ReferenceDiff;
import com.symbolcourt.model.ResolutionRecord;
import com.symbolcourt.model.ResolutionResult;
import com.symbolcourt.model.ResolutionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic comparison of two immutable scheme versions: distinguishes a real
 * rebind (different provider unit) from a mere address/value change with the
 * same provider, plus status transitions (newly resolved, ambiguous, ...).
 */
public final class SchemeComparator {

    public List<ReferenceDiff> compare(ResolutionResult a, ResolutionResult b) {
        Map<String, ResolutionRecord> ma = index(a);
        Map<String, ResolutionRecord> mb = index(b);
        List<ReferenceDiff> diffs = new ArrayList<>();

        for (Map.Entry<String, ResolutionRecord> e : ma.entrySet()) {
            ResolutionRecord rb = mb.get(e.getKey());
            diffs.add(classify(e.getKey(), e.getValue(), rb));
        }
        for (Map.Entry<String, ResolutionRecord> e : mb.entrySet()) {
            if (!ma.containsKey(e.getKey())) {
                diffs.add(classify(e.getKey(), null, e.getValue()));
            }
        }
        return diffs;
    }

    private Map<String, ResolutionRecord> index(ResolutionResult r) {
        Map<String, ResolutionRecord> m = new LinkedHashMap<>();
        for (ResolutionRecord rec : r.getRecords()) {
            m.put(logicalKey(rec), rec);
        }
        return m;
    }

    /** Logical identity independent of ephemeral unit ids: origin name + symbol + version. */
    private String logicalKey(ResolutionRecord rec) {
        var ref = rec.getReference();
        String origin = originName(ref.getUnitId());
        return origin + "::" + ref.getSymbolName()
                + (ref.getKey().getVersion() == null ? "" : "@" + ref.getKey().getVersion());
    }

    private String originName(String unitId) {
        int hash = unitId.indexOf('#');
        return hash >= 0 ? unitId.substring(0, hash) : unitId;
    }

    private ReferenceDiff classify(String key, ResolutionRecord ra, ResolutionRecord rb) {
        if (ra == null) {
            Candidate cw = effectiveWinner(rb);
            return new ReferenceDiff(key, rb.getReference().getSymbolName(), null,
                    cw == null ? null : cw.getUnitDisplayName(), 0,
                    cw == null ? 0 : cw.getAddress(), null, rb.getStatus(),
                    ReferenceDiff.Kind.NEW_REFERENCE, "新方案中新增的引用");
        }
        if (rb == null) {
            Candidate cw = effectiveWinner(ra);
            return new ReferenceDiff(key, ra.getReference().getSymbolName(),
                    cw == null ? null : cw.getUnitDisplayName(), null,
                    cw == null ? 0 : cw.getAddress(), 0, ra.getStatus(), null,
                    ReferenceDiff.Kind.DISAPPEARED, "引用在新方案中消失");
        }
        Candidate ca = effectiveWinner(ra);
        Candidate cb = effectiveWinner(rb);
        String pa = ca == null ? null : ca.getUnitId();
        String pb = cb == null ? null : cb.getUnitId();
        String nameA = ca == null ? null : ca.getUnitDisplayName();
        String nameB = cb == null ? null : cb.getUnitDisplayName();
        long addrA = ca == null ? 0 : ca.getAddress();
        long addrB = cb == null ? 0 : cb.getAddress();

        boolean resolvedA = ra.getStatus() == ResolutionStatus.RESOLVED
                || ra.getStatus() == ResolutionStatus.AMBIGUOUS
                || ra.getStatus() == ResolutionStatus.CONFLICT;
        boolean resolvedB = rb.getStatus() == ResolutionStatus.RESOLVED
                || rb.getStatus() == ResolutionStatus.AMBIGUOUS
                || rb.getStatus() == ResolutionStatus.CONFLICT;

        if (ra.getStatus() != rb.getStatus() && (!resolvedA || !resolvedB)) {
            ReferenceDiff.Kind kind;
            if (!resolvedA && resolvedB) kind = ReferenceDiff.Kind.NEWLY_RESOLVED;
            else if (resolvedA && !resolvedB) kind = ReferenceDiff.Kind.BECAME_UNRESOLVED;
            else kind = ReferenceDiff.Kind.STATE_CHANGED;
            return new ReferenceDiff(key, ra.getReference().getSymbolName(), nameA, nameB,
                    addrA, addrB, ra.getStatus(), rb.getStatus(), kind,
                    "状态由 " + ra.getStatus() + " 变为 " + rb.getStatus());
        }
        if (ra.getReference().isCopyRelocation() != rb.getReference().isCopyRelocation()) {
            return new ReferenceDiff(key, ra.getReference().getSymbolName(), nameA, nameB,
                    addrA, addrB, ra.getStatus(), rb.getStatus(),
                    ReferenceDiff.Kind.COPY_FLIPPED, "COPY relocation 合法性发生变化");
        }
        if (ra.getStatus() != rb.getStatus()) {
            return new ReferenceDiff(key, ra.getReference().getSymbolName(), nameA, nameB,
                    addrA, addrB, ra.getStatus(), rb.getStatus(),
                    ReferenceDiff.Kind.STATE_CHANGED,
                    "裁决状态由 " + ra.getStatus() + " 变为 " + rb.getStatus());
        }
        boolean sameProvider = pa == null ? pb == null : pa.equals(pb);
        if (!sameProvider) {
            return new ReferenceDiff(key, ra.getReference().getSymbolName(), nameA, nameB,
                    addrA, addrB, ra.getStatus(), rb.getStatus(),
                    ReferenceDiff.Kind.REBOUND,
                    "提供者由 " + nameA + " 变为 " + nameB);
        }
        if (addrA != addrB) {
            return new ReferenceDiff(key, ra.getReference().getSymbolName(), nameA, nameB,
                    addrA, addrB, ra.getStatus(), rb.getStatus(),
                    ReferenceDiff.Kind.ADDRESS_CHANGED,
                    "提供者仍为 " + nameA + "，仅地址由 0x" + Long.toHexString(addrA)
                            + " 变为 0x" + Long.toHexString(addrB));
        }
        return new ReferenceDiff(key, ra.getReference().getSymbolName(), nameA, nameB,
                addrA, addrB, ra.getStatus(), rb.getStatus(),
                ReferenceDiff.Kind.SAME, "提供者与地址均未变化");
    }

    /** Winner if RESOLVED; for ambiguous/conflict use the first tied candidate for identity. */
    private Candidate effectiveWinner(ResolutionRecord rec) {
        if (rec.getWinner() != null) {
            return rec.getWinner();
        }
        return rec.getCandidates().stream()
                .filter(c -> c.getStatus() == Candidate.Status.TIED)
                .findFirst().orElse(null);
    }
}
