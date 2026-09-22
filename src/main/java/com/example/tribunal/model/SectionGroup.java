package com.example.tribunal.model;

import java.util.List;

public record SectionGroup(
        String signature,
        int flags,
        boolean comdat,
        List<Integer> memberSections
) {
}
