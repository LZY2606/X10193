package court.engine;

import court.elf.ElfObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable adjudication output for one scheme. */
public final class LinkResult {
    public Policy policy;
    public final List<InputEntry> inputs = new ArrayList<>();
    public final List<Unit> units = new ArrayList<>();
    public final List<Resolution> resolutions = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    /** objectName -> reason; parse failure never aborts the whole adjudication. */
    public final Map<String, String> corrupt = new LinkedHashMap<>();
    public final List<DependencyEdge> edges = new ArrayList<>();
    /** Unit id -> version graph data. */
    public final Map<String, VersionGraph> versionGraphs = new LinkedHashMap<>();
    public final List<ElfObject> parsedElves = new ArrayList<>();
    /** Internal engine scratch state (not part of the JSON contract). */
    public transient final java.util.Map<String, Object> extra = new java.util.HashMap<>();

    public static final class DependencyEdge {
        public String from;
        public String to;
        public String kind; // NEEDED, ARCHIVE, LAZY_EXTRACT
        public boolean satisfied;
    }

    public static final class VersionGraph {
        public String unitId;
        public final List<VGNode> nodes = new ArrayList<>();
        public final List<VGEdge> edges = new ArrayList<>();
    }

    public static final class VGNode {
        public String id;
        public String label;
        public String kind; // DEFINITION, ALIAS, NEED_FILE, NEED_VERSION, SYMBOL
        public boolean hiddenVersion;
    }

    public static final class VGEdge {
        public String from;
        public String to;
        public String label;
    }
}
