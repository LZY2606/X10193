package court.symbol.model;

import java.util.List;

public record ExtractionEvent(int step, String archive, String member, List<String> demandedSymbols,
                              String action, String reason) {
}
