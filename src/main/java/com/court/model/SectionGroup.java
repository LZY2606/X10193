package com.court.model;

import java.util.List;

public record SectionGroup(String signature, boolean comdat, List<Integer> sectionIndices) {
    public static final int GRP_COMDAT = 1;
}
