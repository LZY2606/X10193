package court.symbol.model;

import court.symbol.elf.ElfRelocation;
import court.symbol.elf.ElfSymbol;
import court.symbol.elf.ParsedBinary;

import java.util.List;

public record Unit(String id, String sourceFile, String memberName, ParsedBinary binary, int loadOrder,
                   boolean extracted, String archiveId) {
    public String displayName() {
        return memberName == null || memberName.isEmpty() ? sourceFile : sourceFile + "(" + memberName + ")";
    }

    public boolean isShared() {
        return binary.format == ParsedBinary.Format.ELF_SHARED;
    }

    public boolean isObject() {
        return binary.format == ParsedBinary.Format.ELF_OBJECT;
    }

    public boolean isExecutable() {
        return binary.format == ParsedBinary.Format.ELF_EXECUTABLE;
    }

    public List<ElfSymbol> providers() {
        return binary.isDynamicComponent() ? binary.dynamicSymbols : binary.staticSymbols;
    }

    public List<ElfRelocation> relocationList() {
        return binary.relocations;
    }
}
