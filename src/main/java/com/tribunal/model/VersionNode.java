package com.tribunal.model;

public record VersionNode(
        int index,
        String name,
        boolean definition,
        boolean requirement,
        String parent,
        boolean nonDefault,
        boolean hidden,
        String objectName
) {}
