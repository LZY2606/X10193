package com.tribunal.model;

public record ElfSymbol(
        int index,
        String name,
        long value,
        long size,
        int binding,
        int type,
        int visibility,
        int sectionIndex,
        boolean defined,
        boolean common,
        int versionIndex,
        String versionName,
        boolean defaultVersion,
        boolean hiddenVersion
) {}
