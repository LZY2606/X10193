package arb.engine;

public class PlanStep {
    private int seq;
    private String kind;
    private String detail;
    private String object;
    private String member;
    private String symbol;

    public PlanStep() {
    }

    public PlanStep(int seq, String kind, String detail, String object, String member,
                    String symbol) {
        this.seq = seq;
        this.kind = kind;
        this.detail = detail;
        this.object = object;
        this.member = member;
        this.symbol = symbol;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getMember() {
        return member;
    }

    public void setMember(String member) {
        this.member = member;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
