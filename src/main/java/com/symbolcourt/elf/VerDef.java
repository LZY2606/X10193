package com.symbolcourt.elf;

import java.util.List;

/** A version definition (SHT_GNU_verdef entry). names[0] is the version name, rest are aliases. */
public class VerDef {
    public final int index;
    public final int flags;
    public final List<String> names;

    public VerDef(int index, int flags, List<String> names) {
        this.index = index;
        this.flags = flags;
        this.names = names;
    }

    public String primaryName() {
        return names.isEmpty() ? "" : names.get(0);
    }
}
