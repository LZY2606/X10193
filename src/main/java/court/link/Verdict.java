package court.link;

import java.util.ArrayList;
import java.util.List;

/** The court's decision for one reference key: candidates, rejections, winner, affected relocs. */
public class Verdict {
    public String key;                 // canonical key name@version
    public String name;
    public String requestedVersion;
    public boolean weakReference;
    public boolean copyRelocation;
    public String outcome;             // RESOLVED | UNRESOLVED | AMBIGUOUS | UNMET_VERSION
    public String summary;
    public Provider winner;
    public Candidate winningCandidate;
    public final List<Candidate> candidates = new ArrayList<>();
    public final List<Reference> affectedReferences = new ArrayList<>();
    public Long predictedAddress;
}
