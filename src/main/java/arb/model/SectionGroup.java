package arb.model;

import java.util.List;

public record SectionGroup(
        int sectionIndex,
        String signature,
        int flags,
        List<Integer> memberSections
) {
    public boolean comdat() {
        return (flags & 1) != 0;
    }
}
