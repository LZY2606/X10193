package court.symbol.model;

public record ReferenceComparison(String referenceKey, String symbol, String beforeProvider,
                                  String afterProvider, long beforeAddress, long afterAddress,
                                  String changeType, String detail) {
}
