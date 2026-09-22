package com.symbolcourt.model;

public final class Types {
    private Types() {}

    public enum InputKind { ELF, ARCHIVE }

    public record InputFile(String name, byte[] data, InputKind kind) {}

    /** Target platform link policy. Switching policy always yields a new scheme revision. */
    public record PlatformPolicy(
            String name,
            boolean allowProtectedInterposition,
            boolean copyRelocAgainstProtectedWarn,
            boolean allowHiddenCrossObject) {
        public static PlatformPolicy linuxX8664() {
            return new PlatformPolicy("linux-x86_64", false, false, false);
        }
        public static PlatformPolicy permissive() {
            return new PlatformPolicy("permissive", true, true, true);
        }
    }
}
