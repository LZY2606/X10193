package com.symbolcourt.elf;

import java.util.LinkedHashMap;
import java.util.Map;

/** An ar(1) archive (static library) parsed lazily. */
public final class Archive {
    public final String sourceName;
    /** Member name -> raw member bytes (header already stripped). */
    public final Map<String, byte[]> members = new LinkedHashMap<>();

    public Archive(String sourceName) {
        this.sourceName = sourceName;
    }
}
