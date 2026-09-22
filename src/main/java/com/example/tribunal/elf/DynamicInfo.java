package com.example.tribunal.elf;

import java.util.List;

record DynamicInfo {
    static final DynamicInfo EMPTY = new DynamicInfo(null, List.of());

    final String soname;
    final List<String> needed;

    DynamicInfo(String soname, List<String> needed) {
        this.soname = soname;
        this.needed = List.copyOf(needed);
    }
}
