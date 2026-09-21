package court.store;

public final class SchemeRow {
    public final String id;
    public final String name;
    public final int versionNo;
    public final String policy;
    public final String loadOrderJson;
    public final String decisionJson;
    public final String createdAt;

    public SchemeRow(String id, String name, int versionNo, String policy,
                     String loadOrderJson, String decisionJson, String createdAt) {
        this.id = id;
        this.name = name;
        this.versionNo = versionNo;
        this.policy = policy;
        this.loadOrderJson = loadOrderJson;
        this.decisionJson = decisionJson;
        this.createdAt = createdAt;
    }
}
