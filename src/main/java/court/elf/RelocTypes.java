package court.elf;

import java.util.Map;

/**
 * 按机器类型还原重定位类型名，并标识与裁决有关的两类特殊重定位：
 * 可执行文件对共享对象数据符号的 R_*_COPY，以及受其影响的 JUMP_SLOT/GLOB_DAT。
 * 名称表只覆盖裁决需要的子集，未知类型给出 R_<machine>_<n>。
 */
public final class RelocTypes {
    public static final int EM_386 = 3;
    public static final int EM_PPC = 20;
    public static final int EM_ARM = 40;
    public static final int EM_X86_64 = 62;
    public static final int EM_AARCH64 = 183;
    public static final int EM_RISCV = 243;

    public static final Map<Integer, String> MACHINE_NAMES = Map.of(
            EM_386, "i386",
            EM_PPC, "PPC",
            EM_ARM, "ARM",
            EM_X86_64, "x86-64",
            EM_AARCH64, "AArch64",
            EM_RISCV, "RISC-V");

    public static String machineName(int machine) {
        return MACHINE_NAMES.getOrDefault(machine, "EM_" + machine);
    }

    public static String name(int machine, int type) {
        String n = switch (machine) {
            case EM_X86_64 -> switch (type) {
                case 1 -> "R_X86_64_64";
                case 2 -> "R_X86_64_PC32";
                case 5 -> "R_X86_64_COPY";
                case 6 -> "R_X86_64_GLOB_DAT";
                case 7 -> "R_X86_64_JUMP_SLOT";
                case 8 -> "R_X86_64_RELATIVE";
                case 9 -> "R_X86_64_GOTPCREL";
                case 10 -> "R_X86_64_32";
                case 11 -> "R_X86_64_32S";
                case 20 -> "R_X86_64_GOTPCRELX";
                case 24 -> "R_X86_64_PC64";
                default -> null;
            };
            case EM_AARCH64 -> switch (type) {
                case 257 -> "R_AARCH64_ABS64";
                case 258 -> "R_AARCH64_ABS32";
                case 1024 -> "R_AARCH64_COPY";
                case 1025 -> "R_AARCH64_GLOB_DAT";
                case 1026 -> "R_AARCH64_JUMP_SLOT";
                case 1027 -> "R_AARCH64_RELATIVE";
                default -> null;
            };
            case EM_386 -> switch (type) {
                case 1 -> "R_386_32";
                case 2 -> "R_386_PC32";
                case 10 -> "R_386_GOT32";
                default -> null;
            };
            case EM_RISCV -> switch (type) {
                case 2 -> "R_RISCV_32";
                case 3 -> "R_RISCV_64";
                case 24 -> "R_RISCV_RELATIVE";
                default -> null;
            };
            default -> null;
        };
        return n != null ? n : "R_EM" + machine + "_" + type;
    }

    public static boolean isCopy(int machine, int type) {
        return switch (machine) {
            case EM_X86_64 -> type == 5; // R_X86_64_COPY
            case EM_AARCH64 -> type == 1024;
            case EM_386 -> type == 20; // R_386_COPY
            case EM_ARM -> type == 20; // R_ARM_COPY
            case EM_RISCV -> type == 6; // R_RISCV_COPY
            case EM_PPC -> type == 19; // R_PPC_COPY
            default -> false;
        };
    }

    public static boolean isDynamicBinding(int machine, int type) {
        return switch (machine) {
            case EM_X86_64 -> type == 6 || type == 7 || type == 21 || type == 23;
            // GLOB_DAT, JUMP_SLOT, IRELATIVE, TLS_DTPMOD64 中 6/7 最常见
            case EM_AARCH64 -> type == 1025 || type == 1026;
            case EM_386 -> type == 6 || type == 7;
            case EM_ARM -> type == 21 || type == 22;
            case EM_RISCV -> type == 5;
            default -> false;
        };
    }

    private RelocTypes() {
    }
}
