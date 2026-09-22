package com.court.resolution;

/**
 * Target-platform policies that change how the court weighs candidates.
 * Different policies -> different plan version; old results never drift.
 */
public class Policies {
    /** How ties between equally strong candidates are treated. */
    public enum TiePolicy {
        /** Keep the ambiguity; do not use file traversal order to break it. */
        KEEP_AMBIGUOUS,
        /** Use explicit load order to resolve (classic first-definition ld style). */
        LOAD_ORDER,
        /** Like GNU ld for archives: reject unless exactly one candidate. */
        REJECT
    }

    /** Whether COPY relocations may bind an executable data symbol to a DSO. */
    public boolean allowCopyRelocation = true;

    /** Allow extraction of archive members for weak undefined references. */
    public boolean extractForWeak = false;

    /** Protected data/function symbols in a DSO cannot be interposed. */
    public boolean protectedNoInterposition = true;

    /** Common symbols may coalesce by size; when false common is treated like strong. */
    public boolean commonCoalesce = true;

    /** If two COMDAT groups carry the same signature, drop all but the first. */
    public boolean dedupeComdat = true;

    public TiePolicy tiePolicy = TiePolicy.KEEP_AMBIGUOUS;

    public String name = "default";

    public static Policies defaults() { return new Policies(); }

    public String signature() {
        return "tie=" + tiePolicy
                + "|copy=" + allowCopyRelocation
                + "|weakExtract=" + extractForWeak
                + "|prot=" + protectedNoInterposition
                + "|common=" + commonCoalesce
                + "|comdat=" + dedupeComdat;
    }
}
