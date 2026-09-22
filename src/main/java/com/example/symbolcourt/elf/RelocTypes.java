package com.example.symbolcourt.elf;

import java.util.Map;

/** Best-effort human names for relocation types (display only). */
final class RelocTypes {
    private static final Map<Long, String> X86_64 = Map.ofEntries(
            Map.entry(0L, "R_X86_64_NONE"),
            Map.entry(1L, "R_X86_64_64"),
            Map.entry(2L, "R_X86_64_PC32"),
            Map.entry(3L, "R_X86_64_GOT32"),
            Map.entry(4L, "R_X86_64_PLT32"),
            Map.entry(5L, "R_X86_64_COPY"),
            Map.entry(6L, "R_X86_64_GLOB_DAT"),
            Map.entry(7L, "R_X86_64_JUMP_SLOT"),
            Map.entry(8L, "R_X86_64_RELATIVE"),
            Map.entry(9L, "R_X86_64_GOTPCREL"),
            Map.entry(10L, "R_X86_64_32"),
            Map.entry(11L, "R_X86_64_32S"),
            Map.entry(20L, "R_X86_64_COPY"));

    private RelocTypes() {
    }

    static String name(int machine, long type) {
        if (machine == 62) {
            String n = X86_64.get(type);
            return n != null ? n : "R_X86_64_" + type;
        }
        return "R_M" + machine + "_" + type;
    }
}
