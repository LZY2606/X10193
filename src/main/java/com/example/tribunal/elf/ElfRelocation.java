package com.example.tribunal.elf;

/**
 * One relocation entry. type is the machine-dependent type code.
 */
public record ElfRelocation(
        long offset,
        long addend,
        int symbolIndex,
        int type,
        String symbolTable,
        String sectionName,
        boolean rela) {

    public boolean appliesTo(int machine, int wanted) {
        return type == wanted;
    }
}
