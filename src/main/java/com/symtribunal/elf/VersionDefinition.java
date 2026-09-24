package com.symtribunal.elf;

import java.util.List;

/** One Verdef entry (an object-defined symbol version and its aliases). */
public final class VersionDefinition {
    private final int index;
    private final boolean base;
    private final boolean nonDefault;
    private final String name;
    private final List<String> aliases;

    public VersionDefinition(int index, boolean base, boolean nonDefault,
                             String name, List<String> aliases) {
        this.index = index;
        this.base = base;
        this.nonDefault = nonDefault;
        this.name = name;
        this.aliases = List.copyOf(aliases);
    }

    public int index() {
        return index;
    }

    public boolean base() {
        return base;
    }

    public boolean nonDefault() {
        return nonDefault;
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }
}
