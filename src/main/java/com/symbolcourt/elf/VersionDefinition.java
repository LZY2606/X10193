package com.symbolcourt.elf;

import java.util.List;

/** One Verdef entry; the first aux name is the version, further names are aliases. */
public record VersionDefinition(int index, boolean base, boolean hidden, List<String> names) {
    public String primary() {
        return names.isEmpty() ? "" : names.get(0);
    }
}
