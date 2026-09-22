package court.symbol.model;

import java.util.List;

public record ResolutionInput(String name, PlatformPolicy policy, List<String> loadOrder) {
}
