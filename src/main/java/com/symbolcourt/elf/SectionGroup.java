package com.symbolcourt.elf;

import java.util.List;

/** SHT_GROUP: signature symbol plus member section indices; COMDAT groups are deduplicated. */
public final class SectionGroup {
    private final String signature;
    private final boolean comdat;
    private final List<Integer> members;

    public SectionGroup(String signature, boolean comdat, List<Integer> members) {
        this.signature = signature;
        this.comdat = comdat;
        this.members = members;
    }

    public String getSignature() { return signature; }
    public boolean isComdat() { return comdat; }
    public List<Integer> getMembers() { return members; }
}
