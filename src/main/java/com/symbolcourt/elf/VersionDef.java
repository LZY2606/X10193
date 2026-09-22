package com.symbolcourt.elf;

import java.util.List;

public class VersionDef {
    public final int ndx;
    public final int flags;
    public final String name;
    public final List<String> parents;

    public VersionDef(int ndx, int flags, String name, List<String> parents) {
        this.ndx = ndx;
        this.flags = flags;
        this.name = name;
        this.parents = parents;
    }

    public boolean baseFlag() {
        return (flags & 0x1) != 0;
    }
}
