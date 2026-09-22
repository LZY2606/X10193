package court.symbol.model;

import java.util.List;

/** Version definition/need graph for one loaded object. */
public final class VersionGraph {

    private VersionGraph() {
    }

    public record DefVersion(int index, String name, List<String> aliases,
                             boolean base, boolean nonDefault) {
    }

    public record NeedVersion(int index, String name, String fromSoname, boolean weak) {
    }
}
