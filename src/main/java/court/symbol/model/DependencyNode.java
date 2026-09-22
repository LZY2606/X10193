package court.symbol.model;

import java.util.List;

/** Node in the object dependency graph shown on the page. */
public record DependencyNode(String id, String label, String kind, int order,
                             boolean extracted, String archiveOf,
                             List<String> needed) {
}
