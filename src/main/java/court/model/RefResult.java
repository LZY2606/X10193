package court.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个未定义引用的裁决结论：全部候选、淘汰原因、最终选择、受影响重定位。
 */
public final class RefResult {
    public final RefKey key;
    public final String binding; // 引用方绑定：WEAK / GLOBAL
    public final String refType; // FUNC / OBJECT ...
    public final List<Candidate> candidates = new ArrayList<>();
    public Provider winner;
    public String status; // RESOLVED / UNRESOLVED / AMBIGUOUS / COPY / WEAK_UNDEF_KEPT / LOCAL_BOUND
    public String detail;
    public final List<AffectedReloc> affectedRelocs = new ArrayList<>();

    public RefResult(RefKey key, String binding, String refType) {
        this.key = key;
        this.binding = binding;
        this.refType = refType;
    }
}
