package com.tribunal.elf;

import java.util.Map;

/** Human readable relocation type names and per-machine "copy relocation" codes. */
public final class RelocTypes {
    private RelocTypes() {
    }

    public static final int R_X86_64_COPY = 5;
    public static final int R_386_COPY = 5;
    public static final int R_ARM_COPY = 20;
    public static final int R_AARCH64_COPY = 1024;
    public static final int R_RISCV_COPY = 6;

    private static final Map<Integer, String> X86_64 = Map.ofEntries(
            Map.entry(0, "R_X86_64_NONE"),
            Map.entry(1, "R_X86_64_64"),
            Map.entry(2, "R_X86_64_PC32"),
            Map.entry(3, "R_X86_64_GOT32"),
            Map.entry(4, "R_X86_64_PLT32"),
            Map.entry(5, "R_X86_64_COPY"),
            Map.entry(6, "R_X86_64_GLOB_DAT"),
            Map.entry(7, "R_X86_64_JUMP_SLOT"),
            Map.entry(8, "R_X86_64_RELATIVE"),
            Map.entry(9, "R_X86_64_GOTPCREL"),
            Map.entry(10, "R_X86_64_32"),
            Map.entry(11, "R_X86_64_32S"),
            Map.entry(24, "R_X86_64_PC64"),
            Map.entry(37, "R_X86_64_IRELATIVE"));

    private static final Map<Integer, String> I386 = Map.ofEntries(
            Map.entry(0, "R_386_NONE"),
            Map.entry(1, "R_386_32"),
            Map.entry(2, "R_386_PC32"),
            Map.entry(3, "R_386_GOT32"),
            Map.entry(4, "R_386_PLT32"),
            Map.entry(5, "R_386_COPY"),
            Map.entry(6, "R_386_GLOB_DAT"),
            Map.entry(7, "R_386_JMP_SLOT"),
            Map.entry(8, "R_386_RELATIVE"));

    private static final Map<Integer, String> AARCH64 = Map.ofEntries(
            Map.entry(0, "R_AARCH64_NONE"),
            Map.entry(257, "R_AARCH64_ABS64"),
            Map.entry(258, "R_AARCH64_ABS32"),
            Map.entry(1024, "R_AARCH64_COPY"),
            Map.entry(1025, "R_AARCH64_GLOB_DAT"),
            Map.entry(1026, "R_AARCH64_JUMP_SLOT"),
            Map.entry(1027, "R_AARCH64_RELATIVE"));

    private static final Map<Integer, String> ARM = Map.ofEntries(
            Map.entry(0, "R_ARM_NONE"),
            Map.entry(2, "R_ARM_ABS32"),
            Map.entry(20, "R_ARM_COPY"),
            Map.entry(21, "R_ARM_GLOB_DAT"),
            Map.entry(22, "R_ARM_JUMP_SLOT"),
            Map.entry(23, "R_ARM_RELATIVE"));

    private static final Map<Integer, String> RISCV = Map.ofEntries(
            Map.entry(0, "R_RISCV_NONE"),
            Map.entry(2, "R_RISCV_32"),
            Map.entry(6, "R_RISCV_COPY"));

    public static String name(int machine, int type) {
        Map<Integer, String> table = switch (machine) {
            case Elf.EM_X86_64 -> X86_64;
            case Elf.EM_386 -> I386;
            case Elf.EM_AARCH64 -> AARCH64;
            case Elf.EM_ARM -> ARM;
            case Elf.EM_RISCV -> RISCV;
            default -> Map.of();
        };
        return table.getOrDefault(type, switch (machine) {
            case Elf.EM_X86_64 -> "R_X86_64_" + type;
            case Elf.EM_386 -> "R_386_" + type;
            case Elf.EM_AARCH64 -> "R_AARCH64_" + type;
            case Elf.EM_ARM -> "R_ARM_" + type;
            case Elf.EM_RISCV -> "R_RISCV_" + type;
            default -> "R_" + type;
        });
    }

    public static boolean isCopyType(int machine, int type) {
        return switch (machine) {
            case Elf.EM_X86_64, Elf.EM_386 -> type == 5;
            case Elf.EM_AARCH64 -> type == 1024;
            case Elf.EM_ARM -> type == 20;
            case Elf.EM_RISCV -> type == 6;
            default -> false;
        };
    }
}
