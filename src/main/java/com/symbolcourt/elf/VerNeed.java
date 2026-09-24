package com.symbolcourt.elf;

import java.util.List;

/** A version requirement (SHT_GNU_verneed entry). */
public class VerNeed {
    public final String file;

    public static class Entry {
        public final int index;
        public final String name;
        public final int flags;

        public Entry(int index, String name, int flags) {
            this.index = index;
            this.name = name;
            this.flags = flags;
        }
    }

    public final List<Entry> entries;

    public VerNeed(String file, List<Entry> entries) {
        this.file = file;
        this.entries = entries;
    }
}
