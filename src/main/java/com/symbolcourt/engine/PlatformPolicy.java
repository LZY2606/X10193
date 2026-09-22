package com.symbolcourt.engine;

public record PlatformPolicy(
        String name,
        boolean sharedFirstWins,
        boolean allowMultipleDefinition,
        boolean comdatStrict,
        boolean copyRelocAgainstProtected,
        boolean commonTriggersExtraction,
        boolean rejectVersionMismatch) {

    public static PlatformPolicy gnu() {
        return new PlatformPolicy("gnu", true, false, false, true, false, true);
    }

    public static PlatformPolicy strict() {
        return new PlatformPolicy("strict", true, false, true, false, false, true);
    }

    public static PlatformPolicy legacy() {
        return new PlatformPolicy("legacy", false, true, false, true, true, false);
    }

    public static PlatformPolicy byName(String name) {
        if (name == null) {
            return gnu();
        }
        return switch (name) {
            case "strict" -> strict();
            case "legacy" -> legacy();
            default -> gnu();
        };
    }
}
