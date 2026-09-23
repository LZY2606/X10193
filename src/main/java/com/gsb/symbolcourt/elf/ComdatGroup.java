package com.gsb.symbolcourt.elf;

import java.util.List;

public record ComdatGroup(
        int groupSectionIndex,
        String signature,
        List<Integer> memberSectionIndexes,
        boolean comdat
) {
}
