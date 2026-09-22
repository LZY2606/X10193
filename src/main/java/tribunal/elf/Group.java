package tribunal.elf;

import java.util.List;

public record Group(
        int sectionIndex,
        boolean comdat,
        int signatureSymbol,
        String signature,
        List<Integer> memberSections
) {}
