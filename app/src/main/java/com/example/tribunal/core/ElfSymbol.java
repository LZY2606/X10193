package com.example.tribunal.core;

public record ElfSymbol(
        int index,
        String rawName,
        String name,
        Binding binding,
        String type,
        Visibility visibility,
        long value,
        long size,
        int sectionIndex,
        boolean defined,
        boolean common,
        boolean absolute,
        int rawVersionIndex,
        String versionName,
        boolean defaultVersion,
        String versionNeededFile
) {
    public String candidateName() {
        return name == null ? "" : name;
    }

    public boolean isGlobalOrWeak() {
        return binding == Binding.GLOBAL || binding == Binding.WEAK;
    }
}
