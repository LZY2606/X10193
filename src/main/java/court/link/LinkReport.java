package court.link;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable resolution result of one scheme version. */
public class LinkReport {
    public String schemeName;
    public String policySummary;
    public LinkPolicy policy;
    public final List<UnitModel> units = new ArrayList<>();
    public final List<Verdict> verdicts = new ArrayList<>();
    public final List<StepEvent> steps = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<Map<String, Object>> dependencyGraph = new ArrayList<>();
    public final List<Map<String, Object>> versionGraph = new ArrayList<>();
    public Map<String, Integer> counts = new LinkedHashMap<>();
    public boolean success = true;
}
