package com.symcourt.engine;

/** Target-platform policy knobs. Changing any knob yields a new scheme. */
public record PlatformPolicy(
        String name,
        boolean useLoadOrder,
        boolean lazyArchive,
        boolean copyRelocation) {

    public static PlatformPolicy linuxLike() {
        return new PlatformPolicy("linux-like", true, true, true);
    }

    public static PlatformPolicy keepAmbiguity() {
        return new PlatformPolicy("keep-ambiguity", false, true, true);
    }

    public static PlatformPolicy eagerNoCopy() {
        return new PlatformPolicy("eager-no-copy", true, false, false);
    }

    public static PlatformPolicy byName(String name) {
        return switch (name == null ? "linux-like" : name) {
            case "keep-ambiguity" -> keepAmbiguity();
            case "eager-no-copy" -> eagerNoCopy();
            default -> linuxLike();
        };
    }
}
