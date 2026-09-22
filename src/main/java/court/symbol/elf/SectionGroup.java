package court.symbol.elf;

import java.util.List;

public record SectionGroup(String signature, boolean comdat, int signatureSymbolIndex, List<Integer> sections) {
}
