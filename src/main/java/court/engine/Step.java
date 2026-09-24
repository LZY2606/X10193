package court.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** One playable event in the adjudication timeline. */
public final class Step {
    public int index;
    public String kind;
    public String title;
    public String detail;
    public String unitId;
    public String symbol;
    public Map<String, Object> data = new LinkedHashMap<>();
    public Map<String, String> symbolTable;

    public Step() {
    }

    public Step(int index, String kind, String title) {
        this.index = index;
        this.kind = kind;
        this.title = title;
    }
}
