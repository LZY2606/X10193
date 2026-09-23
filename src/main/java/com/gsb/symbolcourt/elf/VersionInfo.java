package com.gsb.symbolcourt.elf;

import java.util.List;

public final class VersionInfo {
    public record VersionNode(int index, String name, boolean base, boolean weak, boolean definition) {
    }

    public record NeededVersion(String fileName, List<VersionNode> versions) {
    }

    private VersionInfo() {
    }
}
