package court.engine;

import java.util.ArrayList;
import java.util.List;

/** Adjudication result for one reference identity. */
public final class Resolution {
    public Resolution() {}
    public String key;
    public String name;
    public String version;
    public String status; // RESOLVED, UNRESOLVED, AMBIGUOUS, CONFLICT, ERROR
    public String detail;
    public Def winner;
    public String winnerAddress;
    public final List<Ref> references = new ArrayList<>();
    public final List<CandidateView> candidates = new ArrayList<>();

    public static final class CandidateView {
        public Def def;
        public boolean chosen;
        public String reason;
        public String stage;
    }
}
