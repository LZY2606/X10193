package com.symbolcourt.model;

/** Target-platform arbitration policy. Changing a field yields a new immutable scheme version. */
public record Policy(
        /* Lax: keep first strong definition silently; Strict (default): mark duplicate conflict; */
        boolean allowMultipleDefinition,
        /* Whether COPY relocations may bind against protected data in a shared object. */
        boolean allowCopyAgainstProtected,
        /* Whether protected definitions in a shared object remain non-interposable for the
           producer's own references (GNU ld default) or may be overridden from the executable. */
        boolean protectedNonInterposableProducer,
        /* Order in which archives are repeatedly scanned: FIXED_POINT (solve archive cycles)
           or SINGLE_PASS (classic one-sweep static link semantics). */
        ArchiveStrategy archiveStrategy,
        /* Tie policy for equally-ranked providers. AMBIGUOUS keeps all; FIRST wins silently. */
        TiePolicy tiePolicy) {

    public enum ArchiveStrategy { FIXED_POINT, SINGLE_PASS }

    public enum TiePolicy { AMBIGUOUS, FIRST }

    public static Policy gnuDefault() {
        return new Policy(false, false, true, ArchiveStrategy.FIXED_POINT, TiePolicy.AMBIGUOUS);
    }
}
