package com.symbolcourt.elf;

public final class VersionNeed {
    public final String library;
    public final String name;
    public final int other;

    public VersionNeed(String library, String name, int other) {
        this.library = library;
        this.name = name;
        this.other = other;
    }
}
