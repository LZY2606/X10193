package tribunal.engine;

/**
 * Platform policy that influences adjudication. Policies are part of the scheme
 * identity, so changing policy creates a new, independently stored scheme whose
 * results never retroactively drift.
 */
public record Policy(
        LinkMode mode,
        boolean mergeCommon,
        boolean permitFallbackToDefaultVersion,
        boolean archiveRescan,
        boolean protectedCannotInterpose,
        boolean copyRequiresDefaultVisibility
) {
    public static Policy staticDefault() {
        return new Policy(LinkMode.STATIC, true, false, true, true, true);
    }

    public static Policy dynamicDefault() {
        return new Policy(LinkMode.DYNAMIC, false, true, false, true, true);
    }
}
