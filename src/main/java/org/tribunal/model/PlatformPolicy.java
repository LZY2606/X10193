package org.tribunal.model;

public record PlatformPolicy(boolean allowCommonSymbols, boolean firstSharedWins,
                             boolean copyRelocationForExecutable, boolean strictUnsatisfiedVersion,
                             String name) {
    public static PlatformPolicy gnuLike() {
        return new PlatformPolicy(true, true, true, true, "gnu-like");
    }
    public static PlatformPolicy strict() {
        return new PlatformPolicy(false, true, true, true, "strict");
    }
}
