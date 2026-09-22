package com.symbolcourt.elf;

import java.util.List;

public final class VersionNeed {
    public final String file;          // DT_NEEDED-style soname in vernaux
    public final List<Entry> entries;

    public VersionNeed(String file, List<Entry> entries) {
        this.file = file;
        this.entries = entries;
    }

    public static final class Entry {
        public final int index;
        public final String name;
        public final boolean weak;

        public Entry(int index, String name, boolean weak) {
            this.index = index;
            this.name = name;
            this.weak = weak;
        }
    }
}
