package com.symbolcourt.elf;

import java.util.List;

public final class VersionDef {
    public final int index;            // version index (VER_NDX)
    public final String name;          // first verdaux name
    public final List<String> parents; // additional verdaux entries
    public final boolean base;         // vd_flags == 1 (VER_FLG_BASE)
    public final boolean weak;         // vd_flags == 2 (VER_FLG_WEAK)

    public VersionDef(int index, String name, List<String> parents, boolean base, boolean weak) {
        this.index = index;
        this.name = name;
        this.parents = parents;
        this.base = base;
        this.weak = weak;
    }
}
