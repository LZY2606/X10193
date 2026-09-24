package court.elf;

import java.util.Map;

/** Machine-specific relocation type names; only the kinds relevant to adjudication are named. */
public final class RelocTypes {
    private RelocTypes() {}

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

    private static final Map<Integer, String> X86 = Map.ofEntries(
            Map.entry(0, "R_386_NONE"),
            Map.entry(1, "R_386_32"),
            Map.entry(2, "R_386_PC32"),
            Map.entry(3, "R_386_GOT32"),
            Map.entry(6, "R_386_GLOB_DAT"),
            Map.entry(7, "R_386_JMP_SLOT"),
            Map.entry(8, "R_386_RELATIVE"),
            Map.entry(9, "R_386_GOTOFF"),
            Map.entry(10, "R_386_GOTPC"),
            Map.entry(20, "R_386_COPY"));

    private static final Map<Integer, String> ARM = Map.ofEntries(
            Map.entry(0, "R_ARM_NONE"),
            Map.entry(2, "R_ARM_ABS32"),
            Map.entry(20, "R_ARM_COPY"),
            Map.entry(21, "R_ARM_GLOB_DAT"),
            Map.entry(22, "R_ARM_JUMP_SLOT"),
            Map.entry(23, "R_ARM_RELATIVE"));

    private static final Map<Integer, String> AARCH64 = Map.ofEntries(
            Map.entry(0, "R_AARCH64_NONE"),
            Map.entry(257, "R_AARCH64_ABS64"),
            Map.entry(1024, "R_AARCH64_COPY"),
            Map.entry(1025, "R_AARCH64_GLOB_DAT"),
            Map.entry(1026, "R_AARCH64_JUMP_SLOT"),
            Map.entry(1027, "R_AARCH64_RELATIVE"));

    private static final Map<Integer, String> RISCV = Map.ofEntries(
            Map.entry(0, "R_RISCV_NONE"),
            Map.entry(2, "R_RISCV_64"),
            Map.entry(3, "R_RISCV_RELATIVE"),
            Map.entry(4, "R_RISCV_COPY"),
            Map.entry(5, "R_RISCV_JUMP_SLOT"));

    public static String name(int machine, int type) {
        Map<Integer, String> m = switch (machine) {
            case 3 -> X86;
            case 62 -> X86_64;
            case 40 -> ARM;
            case 183 -> AARCH64;
            case 243 -> RISCV;
            default -> null;
        };
        if (m == null) return "R_" + type;
        return m.getOrDefault(type, "R_" + type);
    }

    public static boolean isCopy(int machine, int type) {
        return switch (machine) {
            case 3 -> type == 20;
            case 62 -> type == 5;
            case 40 -> type == 20;
            case 183 -> type == 1024;
            case 243 -> type == 4;
            default -> name(machine, type).contains("_COPY");
        };
    }
}
