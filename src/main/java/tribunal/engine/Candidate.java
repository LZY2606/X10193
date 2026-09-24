package tribunal.engine;

import java.util.ArrayList;
import java.util.List;

public class Candidate {
    /** 提供单元标签（归档成员带 a.o(member.o) 形式）。 */
    public String provider;
    public String unitHash;
    public String memberName;
    public String bind;
    public String visibility;
    public String section;
    public String version;
    public boolean versionNonDefault;
    public String kind;
    public int rank;
    public long size;
    public long value;
    public boolean extracted;
    public boolean chosen;
    /** 被淘汰的原因；获胜者为 null。 */
    public String eliminatedReason;
    public final List<String> notes = new ArrayList<>();
}
