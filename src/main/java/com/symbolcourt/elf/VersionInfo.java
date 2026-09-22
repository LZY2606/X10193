package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

public final class VersionInfo {
    /** A single version definition (verdef) with its parent chain and aliases. */
    public record VerDef(int index, int flags, List<String> names, List<Integer> parentIndices) {
        public String primary() {
            return names.isEmpty() ? null : names.get(0);
        }

        public boolean baseName() {
            return (flags & ElfConstants.VER_FLG_BASE) != 0;
        }

        public boolean weak() {
            return (flags & ElfConstants.VER_FLG_WEAK) != 0;
        }
    }

    /** A version requirement (verneed) against a dependency file. */
    public record VerNeed(String fileName, List<VernAux> aux) {
    }

    public record VernAux(int index, int flags, String name) {
        public boolean weak() {
            return (flags & ElfConstants.VER_FLG_WEAK) != 0;
        }
    }

    private final List<VerDef> definitions = new ArrayList<>();
    private final List<VerNeed> requirements = new ArrayList<>();
    private final int[] versym;
    private final java.util.Set<Integer> hashSymbolIndices = new java.util.HashSet<>();

    public VersionInfo(int dynsymCount) {
        this.versym = new int[dynsymCount];
    }

    public List<VerDef> definitions() {
        return definitions;
    }

    public List<VerNeed> requirements() {
        return requirements;
    }

    public int[] versym() {
        return versym;
    }

    public java.util.Set<Integer> hashSymbolIndices() {
        return hashSymbolIndices;
    }
}
