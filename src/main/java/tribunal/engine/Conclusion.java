package tribunal.engine;

import java.util.ArrayList;
import java.util.List;

/** 单个引用（unit + 符号名 + 请求版本）的裁决结论。 */
public class Conclusion {
    public String referenceUnit;
    public String name;
    public String requestedVersion;
    public String binding;
    public String visibility;

    public String state;
    public String winner;
    public String winnerKind;
    public long winnerAddress;
    public String reason;

    public final List<Candidate> candidates = new ArrayList<>();
    public final List<RelocView> relocations = new ArrayList<>();
}
