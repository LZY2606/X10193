package court.model;

/**
 * 两个方案之间单个引用的语义差异。
 * SAME_PROVIDER / ADDRESS_ONLY（提供者未变，仅地址变化）/ PROVIDER_CHANGED（换了提供者）
 * / NEW_REF / GONE_REF / STATUS_CHANGED（如 RESOLVED<->UNRESOLVED）。
 */
public final class RefDiff {
    public final String key;
    public final String category;
    public final String fromProvider;
    public final String toProvider;
    public final String note;

    public RefDiff(String key, String category, String fromProvider, String toProvider, String note) {
        this.key = key;
        this.category = category;
        this.fromProvider = fromProvider;
        this.toProvider = toProvider;
        this.note = note;
    }
}
