package court.symbol.service;

import court.symbol.model.Decision;
import court.symbol.model.Reference;
import court.symbol.model.ResolutionReport;
import court.symbol.model.SymbolCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic diff between two immutable plan versions.
 * References are matched by (source object, symbol, requested version) so a
 * mere load-order reshuffle does not masquerade as a changed provider.
 */
@Component
public class ReportComparator {

    public record RefChange(String referenceId,
                            String sourceProvider,
                            String symbol,
                            String requestedVersion,
                            String kind,    // PROVIDER_CHANGED, ADDRESS_ONLY,
                                           // RESOLUTION_CHANGED, NEW_REFERENCE,
                                           // REMOVED_REFERENCE
                            String fromProvider,
                            String toProvider,
                            long fromAddress,
                            long toAddress,
                            String fromStatus,
                            String toStatus) {
    }

    public record Diff(String planIdA, int versionA, String planIdB, int versionB,
                       List<RefChange> changes,
                       int providerChanged, int addressOnly, int resolutionChanged,
                       int newReferences, int removedReferences) {
    }

    public Diff compare(ResolutionReport a, ResolutionReport b) {
        Map<String, Decision> ma = index(a);
        Map<String, Decision> mb = index(b);
        List<RefChange> changes = new ArrayList<>();

        for (Map.Entry<String, Decision> e : ma.entrySet()) {
            Decision da = e.getValue();
            Decision db = mb.get(e.getKey());
            if (db == null) {
                changes.add(new RefChange(da.reference().id(),
                        da.reference().providerLabel(), da.reference().symbol(),
                        da.reference().version(), "REMOVED_REFERENCE",
                        name(da.winner()), null, da.simulatedAddress(), -1,
                        da.status(), null));
                continue;
            }
            pushPair(changes, da, db);
        }
        for (Map.Entry<String, Decision> e : mb.entrySet()) {
            if (!ma.containsKey(e.getKey())) {
                Decision db = e.getValue();
                changes.add(new RefChange(db.reference().id(),
                        db.reference().providerLabel(), db.reference().symbol(),
                        db.reference().version(), "NEW_REFERENCE",
                        null, name(db.winner()), -1, db.simulatedAddress(),
                        null, db.status()));
            }
        }

        int provider = count(changes, "PROVIDER_CHANGED");
        int address = count(changes, "ADDRESS_ONLY");
        int resolution = count(changes, "RESOLUTION_CHANGED");
        int added = count(changes, "NEW_REFERENCE");
        int removed = count(changes, "REMOVED_REFERENCE");
        return new Diff(a.planId(), a.version(), b.planId(), b.version(), changes,
                provider, address, resolution, added, removed);
    }

    private Map<String, Decision> index(ResolutionReport r) {
        Map<String, Decision> out = new LinkedHashMap<>();
        for (Decision d : r.decisions()) {
            Reference ref = d.reference();
            out.put(ref.providerId() + "\u0000" + ref.symbol()
                    + "\u0000" + (ref.version() == null ? "" : ref.version()), d);
        }
        return out;
    }

    private void pushPair(List<RefChange> changes, Decision da, Decision db) {
        Reference ref = da.reference();
        String pa = name(da.winner());
        String pb = name(db.winner());
        String kind;
        if (!java.util.Objects.equals(da.status(), db.status())) {
            kind = "RESOLUTION_CHANGED";
        } else if (!java.util.Objects.equals(pa, pb)) {
            kind = "PROVIDER_CHANGED";
        } else if (da.simulatedAddress() != db.simulatedAddress()) {
            kind = "ADDRESS_ONLY";
        } else {
            return;
        }
        changes.add(new RefChange(ref.id(), ref.providerLabel(), ref.symbol(),
                ref.version(), kind, pa, pb, da.simulatedAddress(),
                db.simulatedAddress(), da.status(), db.status()));
    }

    private String name(SymbolCandidate c) {
        return c == null ? null : c.providerLabel();
    }

    private int count(List<RefChange> changes, String kind) {
        return (int) changes.stream().filter(c -> c.kind().equals(kind)).count();
    }
}
