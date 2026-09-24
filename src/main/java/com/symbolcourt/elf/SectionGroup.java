package com.symbolcourt.elf;

import java.util.List;

/** An SHT_GROUP section: COMDAT signature plus member section indices. */
public record SectionGroup(int groupSectionIndex, String signature, boolean comdat, List<Integer> memberSections) {
}
