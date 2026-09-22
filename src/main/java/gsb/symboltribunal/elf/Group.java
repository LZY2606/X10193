package gsb.symboltribunal.elf;

import java.util.List;

public record Group(
        String signature,
        int sectionIndex,
        boolean comdat,
        int flags,
        List<Integer> memberSections) {
}
