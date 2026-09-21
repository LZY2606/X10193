package court.link;

/** A considered provider for one verdict, including why it was kept or rejected. */
public class Candidate {
    public String unitId;
    public String displayName;
    public String version;
    public String binding;
    public String visibility;
    public String definitionClass;
    public String status;      // WINNER | ELIMINATED | TIE
    public String reason;      // elimination reason, or winning rationale
    public long predictedAddress;
    public int rank;           // precedence rank among live candidates
}
