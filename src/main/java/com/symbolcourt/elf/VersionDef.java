package com.symbolcourt.elf;

public final class VersionDef {
    public final int ndx;
    public final String name;
    public final boolean base;
    public final boolean hidden;

    public VersionDef(int ndx, String name, boolean base, boolean hidden) {
        this.ndx = ndx;
        this.name = name;
        this.base = base;
        this.hidden = hidden;
    }
}
