package court.symbol.model;

import java.util.List;

/** Version definition/need graph for one loaded object. */
public record VersionNode(String providerId,
                          String providerLabel,
                          List<VersionGraph.DefVersion> definitions,
                          List<VersionGraph.NeedVersion> needs) {
}
