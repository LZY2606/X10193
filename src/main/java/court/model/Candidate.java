package court.model;

/**
 * 候选提供者及裁决轨迹：为何保留/淘汰。每个看到的定义都会出现在候选列表中，
 * winner=true 的为最终选择；rejected=true 时 rejectReason 必填。
 */
public final class Candidate {
    public final Provider provider;
    public boolean eligible;
    public String rejectReason; // 淘汰原因（版本不符、visibility、COMDAT 丢弃、未抽取…）
    public boolean winner;
    public String rankNote; // 排序层级说明（STRONG_DEF / COMMON / WEAK_DEF / SHARED ...）
    public int rank; // 数值越小越优先

    public Candidate(Provider provider) {
        this.provider = provider;
        this.eligible = true;
    }

    public static Candidate rejected(Provider p, String reason) {
        Candidate c = new Candidate(p);
        c.eligible = false;
        c.rejectReason = reason;
        return c;
    }
}
