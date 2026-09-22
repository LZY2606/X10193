package com.symcourt.elf;

import java.util.List;

public final class ElfGroup {
    public final int sectionIndex;
    public final String signature;
    public final boolean comdat;
    public final List<Integer> memberSections;

    ElfGroup(int sectionIndex, String signature, boolean comdat, List<Integer> memberSections) {
        this.sectionIndex = sectionIndex;
        this.signature = signature;
        this.comdat = comdat;
        this.memberSections = memberSections;
    }
}
