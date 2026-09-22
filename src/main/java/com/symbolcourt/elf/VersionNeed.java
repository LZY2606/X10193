package com.symbolcourt.elf;

import java.util.List;

public class VersionNeed {
    public final String fileName;
    public final List<NeedEntry> entries;

    public VersionNeed(String fileName, List<NeedEntry> entries) {
        this.fileName = fileName;
        this.entries = entries;
    }

    public static class NeedEntry {
        public final int hash;
        public final int flags;
        public final int other;
        public final String name;

        public NeedEntry(int hash, int flags, int other, String name) {
            this.hash = hash;
            this.flags = flags;
            this.other = other;
            this.name = name;
        }
    }
}
