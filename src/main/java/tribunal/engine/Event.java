package tribunal.engine;

import java.util.ArrayList;
import java.util.List;

/** 逐步播放的一步：归档抽取或符号表更新。 */
public class Event {
    public int step;
    public String phase;
    public String type;
    public String unit;
    public String member;
    public String symbol;
    public String requestedVersion;
    public String message;
    /** 该步之后符号表中可见的已定义名（含提供者）。 */
    public final List<String> definedSymbols = new ArrayList<>();
    /** 该步之后仍未满足的强引用名。 */
    public final List<String> unresolvedStrong = new ArrayList<>();
}
