package com.example.tribunal.core;

import java.util.List;

public record SectionGroup(
        int groupSectionIndex,
        String signature,
        boolean comdat,
        int signatureSymbolIndex,
        List<Integer> sectionIndexes
) {
}
