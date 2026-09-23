package com.gsb.tribunal.elf;

import java.util.List;

public record Group(String signature, boolean comdat, List<Integer> memberSectionIndexes) {
}
