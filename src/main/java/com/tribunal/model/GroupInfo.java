package com.tribunal.model;

import java.util.List;

public record GroupInfo(
        int sectionIndex,
        String signature,
        int flags,
        List<Integer> memberSections
) {}
