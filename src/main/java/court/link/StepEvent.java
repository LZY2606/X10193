package court.link;

import java.util.LinkedHashMap;
import java.util.Map;

/** One playback step (archive extraction / symbol table update / decision). */
public class StepEvent {
    public int index;
    public String phase;       // LOAD | SCAN | EXTRACT | COMDAT | RESOLVE | FINAL
    public String title;
    public String detail;
    public String unitId;
    public String symbol;
    public Map<String, Object> after = new LinkedHashMap<>();

    public StepEvent() {}
    public StepEvent(int index, String phase, String title, String detail) {
        this.index = index; this.phase = phase; this.title = title; this.detail = detail;
    }
}
