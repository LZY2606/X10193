package court.symbol.engine;

import court.symbol.elf.Elf;

/** Human names for the relocation types the court understands. */
final class RelocationNames {

    private RelocationNames() {
    }

    static String name(int machine, int type) {
        if (machine == 62) {
            return switch (type) {
                case Elf.R_X86_64_COPY -> "R_X86_64_COPY";
                case Elf.R_X86_64_GLOB_DAT -> "R_X86_64_GLOB_DAT";
                case Elf.R_X86_64_JUMP_SLOT -> "R_X86_64_JUMP_SLOT";
                case Elf.R_X86_64_RELATIVE -> "R_X86_64_RELATIVE";
                default -> "R_X86_64_" + type;
            };
        }
        if (machine == 183) {
            return switch (type) {
                case Elf.R_AARCH64_COPY -> "R_AARCH64_COPY";
                case Elf.R_AARCH64_GLOB_DAT -> "R_AARCH64_GLOB_DAT";
                case Elf.R_AARCH64_JUMP_SLOT -> "R_AARCH64_JUMP_SLOT";
                case Elf.R_AARCH64_RELATIVE -> "R_AARCH64_RELATIVE";
                default -> "R_AARCH64_" + type;
            };
        }
        return "REL_" + type;
    }

    static boolean isDynamicUse(int machine, int type) {
        if (machine == 62) {
            return type == Elf.R_X86_64_GLOB_DAT || type == Elf.R_X86_64_JUMP_SLOT
                    || type == Elf.R_X86_64_COPY;
        }
        if (machine == 183) {
            return type == Elf.R_AARCH64_GLOB_DAT || type == Elf.R_AARCH64_JUMP_SLOT
                    || type == Elf.R_AARCH64_COPY;
        }
        return false;
    }
}
