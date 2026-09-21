package court.fixture;

/** 一条 RELA 重定位（x86_64）。symName 引用同表符号；type 见 R_X86_64_*。 */
public final class Rela {
    public final String symName;
    public final int type;
    public final long offset;
    public final long addend;

    public Rela(String symName, int type, long offset, long addend) {
        this.symName = symName;
        this.type = type;
        this.offset = offset;
        this.addend = addend;
    }

    public static final int R_X86_64_64 = 1;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
}
