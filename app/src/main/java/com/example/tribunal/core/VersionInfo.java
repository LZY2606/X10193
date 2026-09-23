package com.example.tribunal.core;

public record VersionInfo(
        int index,
        String name,
        boolean definition,
        boolean nonDefault,
        boolean hidden,
        String neededFile
) {
}
