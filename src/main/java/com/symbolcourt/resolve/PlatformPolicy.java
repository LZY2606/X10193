package com.symbolcourt.resolve;

/**
 * Target-platform knobs that can change the outcome independent of file
 * ordering. Kept explicit so different policies form different, immutable
 * scheme versions.
 */
public final class PlatformPolicy {
    public final String name;
    /** When true, COPY relocations are generated for data access into DSOs. */
    public final boolean copyRelocations;
    /** When true, a weak definition loses to a common symbol (traditional ld). */
    public final boolean weakLosesToCommon;
    /** When true, protected definitions cannot interpose from outside. */
    public final boolean protectedNonInterposable;

    public PlatformPolicy(String name, boolean copyRelocations,
                          boolean weakLosesToCommon, boolean protectedNonInterposable) {
        this.name = name;
        this.copyRelocations = copyRelocations;
        this.weakLosesToCommon = weakLosesToCommon;
        this.protectedNonInterposable = protectedNonInterposable;
    }

    public static PlatformPolicy linuxX8664() {
        return new PlatformPolicy("linux-x86_64", true, true, true);
    }

    public static PlatformPolicy linuxAarch64() {
        return new PlatformPolicy("linux-aarch64", true, true, true);
    }

    public static PlatformPolicy noCopyReloc() {
        return new PlatformPolicy("generic-nocopy", false, true, true);
    }
}
