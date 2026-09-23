package com.example.tribunal.core;

public enum PlatformPolicy {
    STRICT,
    GNU_DYNAMIC,
    GNU_RELOCATABLE;

    static PlatformPolicy from(String value) {
        if (value == null || value.isBlank()) {
            return STRICT;
        }
        return PlatformPolicy.valueOf(value.trim().toUpperCase());
    }
}
