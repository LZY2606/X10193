package com.example.tribunal.elf;

import com.example.tribunal.model.VersionDefinition;
import com.example.tribunal.model.VersionNeed;

import java.util.ArrayList;
import java.util.List;

class VersionInfo {
    int index;
    int hash;
    String name;
    List<String> aliases = new ArrayList<>();
    boolean base;
    boolean weak;
    boolean nonDefault;
    String fileName;

    VersionInfo(int index) {
        this.index = index;
    }

    VersionDefinition toDefinition() {
        return new VersionDefinition(index, hash, name, List.copyOf(aliases), base, weak, nonDefault);
    }

    VersionNeed toNeed() {
        return new VersionNeed(index, fileName, name, hash, weak, nonDefault);
    }
}
