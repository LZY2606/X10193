package com.example.tribunal.model;

public record VersionNeed(
        int versionIndex,
        String fileName,
        String versionName,
        int hash,
        boolean weak,
        boolean nonDefault
) {
}
