package org.symcourt.elf;

import java.util.List;

/**
 * An SHT_GROUP group. Signature comes from the group section header's
 * sh_link (symbol table index); member sections follow the COMDAT entry.
 */
public final class SectionGroup {
    public final int sectionIndex;
    public final int flags;
    public final String signature;
    public final List<Integer> memberSections;

    public SectionGroup(int sectionIndex, int flags, String signature,
                        List<Integer> memberSections) {
        this.sectionIndex = sectionIndex;
        this.flags = flags;
        this.signature = signature;
        this.memberSections = memberSections;
    }

    public boolean comdat() {
        return (flags & Elf.GRP_COMDAT) != 0;
    }
}
