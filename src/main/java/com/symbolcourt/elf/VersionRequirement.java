package com.symbolcourt.elf;

import java.util.List;

/** One verneed entry: a required shared object and the version nodes requested from it. */
public final class VersionRequirement {
    private final String fileName;
    private final List<Node> nodes;

    public VersionRequirement(String fileName, List<Node> nodes) {
        this.fileName = fileName;
        this.nodes = nodes;
    }

    public String getFileName() { return fileName; }
    public List<Node> getNodes() { return nodes; }

    public static final class Node {
        private final int index;
        private final int hash;
        private final int flags;
        private final String name;
        private final boolean weak;

        public Node(int index, int hash, int flags, String name) {
            this.index = index;
            this.hash = hash;
            this.flags = flags;
            this.name = name;
            this.weak = (flags & ElfConstants.VER_FLG_WEAK) != 0;
        }

        public int getIndex() { return index; }
        public int getHash() { return hash; }
        public int getFlags() { return flags; }
        public String getName() { return name; }
        public boolean isWeak() { return weak; }
    }
}
