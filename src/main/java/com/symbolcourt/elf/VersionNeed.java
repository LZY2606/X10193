package com.symbolcourt.elf;

import java.util.List;

/** One Verneed entry: a needed source file and the versions required from it. */
public record VersionNeed(int firstIndex, String fileName, List<Aux> auxiliaries) {
    public record Aux(int index, String versionName, boolean weak, boolean hidden) {
    }
}
