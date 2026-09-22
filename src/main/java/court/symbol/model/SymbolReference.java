package court.symbol.model;

import court.symbol.elf.ElfRelocation;

public record SymbolReference(String id, Unit unit, ElfRelocation relocation, String name,
                              String requestedVersion, boolean defaultRequested, String requiredLibrary,
                              boolean copy) {
}
