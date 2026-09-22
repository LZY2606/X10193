package com.symbolcourt.model;

import com.symbolcourt.elf.ElfConstants;
import com.symbolcourt.elf.ElfFile;

import java.util.List;

final class ElfSummaries {
    private ElfSummaries() {
    }

    static ElfSummary of(ElfFile elf) {
        String type = switch (elf.getType()) {
            case ElfConstants.ET_REL -> "REL";
            case ElfConstants.ET_EXEC -> "EXEC";
            case ElfConstants.ET_DYN -> "DYN";
            default -> "OTHER(" + elf.getType() + ")";
        };
        List<String> sigs = elf.getGroups().stream()
                .filter(com.symbolcourt.elf.SectionGroup::isComdat)
                .map(com.symbolcourt.elf.SectionGroup::getSignature).toList();
        return new ElfSummary(elf.isElf64(), elf.isLittleEndian() ? "LSB" : "MSB",
                elf.getMachine(), type, elf.getSoname(), List.copyOf(elf.getNeeded()), sigs,
                elf.getStaticSymbols().size(), elf.getDynamicSymbols().size(),
                elf.getRelocations().size(), elf.getVersionDefinitions().size(),
                elf.getVersionRequirements().size());
    }
}
