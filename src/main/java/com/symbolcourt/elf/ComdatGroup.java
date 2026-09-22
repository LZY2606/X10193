package com.symbolcourt.elf;

import java.util.List;

/**
 * An SHT_GROUP section. The signature is the name of the symbol named by
 * sh_info. Members with the same signature in one static link are COMDAT
 * deduplicated; only the winning group's sections survive.
 */
public final class ComdatGroup {
    public final String signature;
    public final boolean comdat; // GRP_COMDAT flag present
    public final List<Integer> memberSections;
    public final int groupSectionIndex;

    public ComdatGroup(String signature, boolean comdat, List<Integer> memberSections,
                       int groupSectionIndex) {
        this.signature = signature;
        this.comdat = comdat;
        this.memberSections = memberSections;
        this.groupSectionIndex = groupSectionIndex;
    }
}
