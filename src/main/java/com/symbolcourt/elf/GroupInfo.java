package com.symbolcourt.elf;

import java.util.List;

public record GroupInfo(int sectionIndex, String signature, boolean comdat, List<Integer> memberSections) {
}
