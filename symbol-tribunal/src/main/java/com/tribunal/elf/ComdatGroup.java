package com.tribunal.elf;

import java.util.List;

/** A section group (SHT_GROUP); COMDAT groups participate in deduplication. */
public final class ComdatGroup {
    public final int sectionIndex;       // index of the SHT_GROUP section
    public final int flags;              // GRP_COMDAT etc.
    public final String signature;       // group signature symbol name
    public final List<Integer> members;  // member section header indices

    public ComdatGroup(int sectionIndex, int flags, String signature, List<Integer> members) {
        this.sectionIndex = sectionIndex;
        this.flags = flags;
        this.signature = signature;
        this.members = members;
    }

    public boolean isComdat() {
        return (flags & ElfConstants.GRP_COMDAT) != 0;
    }
}
