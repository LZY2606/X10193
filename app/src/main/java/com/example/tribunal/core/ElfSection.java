package com.example.tribunal.core;

import java.util.List;

public record ElfSection(
        int index,
        String name,
        int type,
        long flags,
        long address,
        long offset,
        long size,
        int link,
        int info,
        long alignment,
        long entrySize,
        List<Integer> groupSections
) {
    public boolean isInGroup(List<Integer> sections) {
        return sections.contains(index);
    }
}
