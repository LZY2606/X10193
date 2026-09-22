package com.symbolcourt.elf;

import java.util.List;

/** One verdef entry: the definition name plus its non-default aliases. */
public final class VersionDefinition {
    private final int index;
    private final int hash;
    private final int flags;
    private final String name;
    private final List<String> aliases;
    private final boolean base;

    public VersionDefinition(int index, int hash, int flags, String name, List<String> aliases) {
        this.index = index;
        this.hash = hash;
        this.flags = flags;
        this.name = name;
        this.aliases = aliases;
        this.base = (flags & ElfConstants.VER_FLG_BASE) != 0;
    }

    public int getIndex() { return index; }
    public int getHash() { return hash; }
    public int getFlags() { return flags; }
    public String getName() { return name; }
    public List<String> getAliases() { return aliases; }
    public boolean isBase() { return base; }
}
