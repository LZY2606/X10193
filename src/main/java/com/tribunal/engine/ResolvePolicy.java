package com.tribunal.engine;

/**
 * Target platform policy knobs. Changing any knob creates a new plan version;
 * old plan results are kept verbatim.
 *
 * @param commonOverWeak   GNU ld semantics: tentative (common) symbols beat
 *                         weak definitions from objects (default true).
 * @param singleScanArchives one-pass {@code ld} semantics disallows resolving
 *                         archive cycles via repeated scans (default false).
 * @param copyRelocations  allow COPY relocations for executable data
 *                         references into shared objects (default true).
 */
public record ResolvePolicy(boolean commonOverWeak, boolean singleScanArchives,
                            boolean copyRelocations) {
    public static ResolvePolicy gnu() {
        return new ResolvePolicy(true, false, true);
    }

    public String fingerprint() {
        return (commonOverWeak ? "c" : "-") + (singleScanArchives ? "s" : "-")
                + (copyRelocations ? "p" : "-");
    }
}
