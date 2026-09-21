package com.example.symbolcourt.elf;

import java.util.Map;

final class RelocationTypes {
    private RelocationTypes() {
    }

    static final Map<Integer, Map<Integer, String>> TYPES = Map.of(
            3, Map.ofEntries(Map.entry(0, "R_386_NONE"), Map.entry(1, "R_386_32"),
                    Map.entry(2, "R_386_PC32"), Map.entry(6, "R_386_GLOB_DAT"),
                    Map.entry(7, "R_386_JMP_SLOT"), Map.entry(8, "R_386_RELATIVE"),
                    Map.entry(20, "R_386_COPY")),
            62, Map.ofEntries(Map.entry(0, "R_X86_64_NONE"), Map.entry(1, "R_X86_64_64"),
                    Map.entry(2, "R_X86_64_PC32"), Map.entry(5, "R_X86_64_COPY"),
                    Map.entry(6, "R_X86_64_GLOB_DAT"), Map.entry(7, "R_X86_64_JUMP_SLOT"),
                    Map.entry(8, "R_X86_64_RELATIVE"), Map.entry(37, "R_X86_64_IRELATIVE")),
            183, Map.ofEntries(Map.entry(0, "R_AARCH64_NONE"), Map.entry(257, "R_AARCH64_ABS64"),
                    Map.entry(1024, "R_AARCH64_COPY"), Map.entry(1025, "R_AARCH64_GLOB_DAT"),
                    Map.entry(1026, "R_AARCH64_JUMP_SLOT"), Map.entry(1027, "R_AARCH64_RELATIVE")),
            243, Map.ofEntries(Map.entry(0, "R_RISCV_NONE"), Map.entry(2, "R_RISCV_32"),
                    Map.entry(3, "R_RISCV_64"), Map.entry(6, "R_RISCV_RELATIVE"),
                    Map.entry(7, "R_RISCV_COPY"), Map.entry(8, "R_RISCV_JUMP_SLOT")));

    static boolean isCopy(int machine, int type) {
        return (machine == 62 && type == 5)
                || (machine == 3 && type == 20)
                || (machine == 183 && type == 1024)
                || (machine == 243 && type == 7);
    }

    static String name(int machine, int type) {
        return TYPES.getOrDefault(machine, Map.of()).getOrDefault(type, "R_" + machine + "_" + type);
    }
}
