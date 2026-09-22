package com.example.tribunal.model;

import java.util.List;

public record VersionDefinition(
        int versionIndex,
        int hash,
        String name,
        List<String> aliases,
        boolean base,
        boolean weak,
        boolean nonDefault
) {
}
