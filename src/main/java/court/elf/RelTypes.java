package court.elf;

import java.util.HashMap;
import java.util.Map;

/**
 * Decodes relocation type numbers into mnemonics per target machine, and identifies
 * the platform policy numbers used by the court (COPY / JUMP_SLOT / GLOB_DAT / RELATIVE).
 */
public final class RelTypes {
    private RelTypes() {}

    public static final int EM_386 = 3;
    public static final int EM_ARM = 40;
    public static final int EM_X86_64 = 62;
    public static final int EM_AARCH64 = 183;
    public static final int EM_RISCV = 243;

    public static final class Policy {
        public final long copy;
        public final long jumpSlot;
        public final long globDat;
        public final long relative;
        public final String label;

        public Policy(long copy, long jumpSlot, long globDat, long relative, String label) {
            this.copy = copy;
            this.jumpSlot = jumpSlot;
            this.globDat = globDat;
            this.relative = relative;
            this.label = label;
        }

        public boolean isCopy(long t) { return t == copy; }
        public boolean isJumpSlot(long t) { return t == jumpSlot; }
        public boolean isGlobDat(long t) { return t == globDat; }
        public boolean isRelative(long t) { return t == relative; }
    }

    private static final Map<Integer, Policy> POLICIES = new HashMap<>();
    private static final Map<Integer, Map<Long, String>> NAMES = new HashMap<>();

    static {
        POLICIES.put(EM_X86_64, new Policy(8, 7, 6, 37, "x86-64"));
        POLICIES.put(EM_386, new Policy(9, 7, 51, 8, "i386"));
        POLICIES.put(EM_AARCH64, new Policy(1024, 1026, 1025, 1027, "AArch64"));
        POLICIES.put(EM_ARM, new Policy(20, 22, 21, 23, "ARM"));
        POLICIES.put(EM_RISCV, new Policy(10, 7, 9, 3, "RISC-V"));

        Map<Long, String> x64 = new HashMap<>();
        x64.put(0L, "R_X86_64_NONE"); x64.put(1L, "R_X86_64_64");
        x64.put(2L, "R_X86_64_PC32"); x64.put(3L, "R_X86_64_GOT32");
        x64.put(4L, "R_X86_64_PLT32"); x64.put(6L, "R_X86_64_GLOB_DAT");
        x64.put(7L, "R_X86_64_JUMP_SLOT"); x64.put(8L, "R_X86_64_COPY");
        x64.put(9L, "R_X86_64_RELATIVE");
        NAMES.put(EM_X86_64, x64);

        Map<Long, String> arm64 = new HashMap<>();
        arm64.put(0L, "R_AARCH64_NONE"); arm64.put(257L, "R_AARCH64_ABS64");
        arm64.put(1024L, "R_AARCH64_COPY"); arm64.put(1025L, "R_AARCH64_GLOB_DAT");
        arm64.put(1026L, "R_AARCH64_JUMP_SLOT"); arm64.put(1027L, "R_AARCH64_RELATIVE");
        NAMES.put(EM_AARCH64, arm64);

        Map<Long, String> x86 = new HashMap<>();
        x86.put(0L, "R_386_NONE"); x86.put(1L, "R_386_32"); x86.put(2L, "R_386_PC32");
        x86.put(7L, "R_386_JMP_SLOT"); x86.put(8L, "R_386_RELATIVE");
        x86.put(9L, "R_386_COPY"); x86.put(51L, "R_386_GLOB_DAT");
        NAMES.put(EM_386, x86);
    }

    public static Policy policy(int machine) {
        return POLICIES.getOrDefault(machine, POLICIES.get(EM_X86_64));
    }

    public static String name(int machine, long type) {
        Map<Long, String> m = NAMES.get(machine);
        if (m != null) {
            String n = m.get(type);
            if (n != null) return n;
        }
        return "R_" + type;
    }
}
