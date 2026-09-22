package court.symbol.model;

/**
 * Platform strategy applied while adjudicating. Switching strategy (or load
 * order) creates a new immutable plan version; old plans keep their results.
 */
public record PlatformPolicy(String linkModel,      // STATIC or DYNAMIC
                             String tiePolicy,       // FIRST_IN_ORDER or REPORT_AMBIGUITY
                             boolean allowInterposition) {

    public static final String STATIC = "STATIC";
    public static final String DYNAMIC = "DYNAMIC";
    public static final String FIRST = "FIRST_IN_ORDER";
    public static final String AMBIG = "REPORT_AMBIGUITY";

    public static PlatformPolicy staticDefault() {
        return new PlatformPolicy(STATIC, FIRST, false);
    }

    public static PlatformPolicy dynamicDefault() {
        return new PlatformPolicy(DYNAMIC, FIRST, true);
    }
}
