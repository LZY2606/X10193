package gsb.symcourt.elf;

public enum ElfClass {
    ELF32(1),
    ELF64(2);

    public final int code;

    ElfClass(int code) {
        this.code = code;
    }

    public static ElfClass fromCode(int code) throws ElfException {
        for (ElfClass c : values()) {
            if (c.code == code) {
                return c;
            }
        }
        throw new ElfException("unknown EI_CLASS " + code);
    }
}
