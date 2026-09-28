package org.symcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** One verdef node: an index, flags and a primary name plus aliases. */
public final class VerDefNode {
    public final int index;
    public final int flags;
    public final List<String> names = new ArrayList<>();

    public VerDefNode(int index, int flags) {
        this.index = index;
        this.flags = flags;
    }

    public String primary() {
        return names.isEmpty() ? null : names.get(0);
    }

    public boolean base() {
        return (flags & Elf.VER_FLG_BASE) != 0;
    }

    public boolean weak() {
        return (flags & Elf.VER_FLG_WEAK) != 0;
    }
}
