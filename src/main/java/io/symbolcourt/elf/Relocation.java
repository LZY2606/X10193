package io.symbolcourt.elf;

/** A relocation entry: where the reference lives, which symbol it needs. */
public class Relocation {
    public long offset;
    public long type;
    public int symbolIndex;
    public long addend;
    public boolean rela;
    public String sectionName;
    public boolean dynamic;
    public boolean copyReloc;

    /** Human readable architecture specific relocation mnemonic. */
    public String typeName(String machine) {
        if (copyReloc) {
            return "R_COPY";
        }
        return switch (machine) {
            case "EM_X86_64" -> X86_64.name(type);
            case "EM_AARCH64" -> AArch64.name(type);
            case "EM_386" -> X86_32.name(type);
            case "EM_RISCV" -> RiscV.name(type);
            default -> "REL_" + type;
        };
    }

    private static final class X86_64 {
        static String name(long t) {
            return switch ((int) t) {
                case 0 -> "R_X86_64_NONE";
                case 1 -> "R_X86_64_64";
                case 2 -> "R_X86_64_PC32";
                case 3 -> "R_X86_64_GOT32";
                case 4 -> "R_X86_64_PLT32";
                case 5 -> "R_X86_64_COPY";
                case 6 -> "R_X86_64_GLOB_DAT";
                case 7 -> "R_X86_64_JUMP_SLOT";
                case 8 -> "R_X86_64_RELATIVE";
                case 9 -> "R_X86_64_GOTPCREL";
                case 10 -> "R_X86_64_32";
                case 11 -> "R_X86_64_32S";
                case 24 -> "R_X86_64_PC64";
                case 37 -> "R_X86_64_IRELATIVE";
                default -> "R_X86_64_" + t;
            };
        }
    }

    private static final class AArch64 {
        static String name(long t) {
            return switch ((int) t) {
                case 0 -> "R_AARCH64_NONE";
                case 257 -> "R_AARCH64_ABS64";
                case 258 -> "R_AARCH64_ABS32";
                case 1024 -> "R_AARCH64_COPY";
                case 1025 -> "R_AARCH64_GLOB_DAT";
                case 1026 -> "R_AARCH64_JUMP_SLOT";
                case 1027 -> "R_AARCH64_RELATIVE";
                case 1028 -> "R_AARCH64_TLS_DTPREL64";
                case 1030 -> "R_AARCH64_TLS_TPREL64";
                case 1031 -> "R_AARCH64_TLSDESC";
                case 1032 -> "R_AARCH64_IRELATIVE";
                default -> "R_AARCH64_" + t;
            };
        }
    }

    private static final class X86_32 {
        static String name(long t) {
            return switch ((int) t) {
                case 0 -> "R_386_NONE";
                case 1 -> "R_386_32";
                case 2 -> "R_386_PC32";
                case 5 -> "R_386_COPY";
                case 6 -> "R_386_GLOB_DAT";
                case 7 -> "R_386_JMP_SLOT";
                case 8 -> "R_386_RELATIVE";
                default -> "R_386_" + t;
            };
        }
    }

    private static final class RiscV {
        static String name(long t) {
            return switch ((int) t) {
                case 0 -> "R_RISCV_NONE";
                case 2 -> "R_RISCV_32";
                case 3 -> "R_RISCV_64";
                case 5 -> "R_RISCV_COPY";
                case 6 -> "R_RISCV_GLOB_DAT";
                case 7 -> "R_RISCV_JUMP_SLOT";
                case 8 -> "R_RISCV_RELATIVE";
                case 13 -> "R_RISCV_CALL";
                case 57 -> "R_RISCV_IRELATIVE";
                default -> "R_RISCV_" + t;
            };
        }
    }
}
