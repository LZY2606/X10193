package gsb.tribunal.elf;

/**
 * One symbol table entry (either .symtab or .dynsym).
 * {@code dynamic} records which table the entry came from.
 */
public record ElfSymbol(
        int index,
        String name,
        long value,
        long size,
        int info,
        int other,
        int shndx,
        boolean dynamic) {

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_COMMON = 5;
    public static final int STT_TLS = 6;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    public int binding() { return info >>> 4; }
    public int type() { return info & 0xf; }
    public int visibility() { return other & 3; }

    public boolean isUndef() { return shndx == SHN_UNDEF; }
    public boolean isCommon() { return shndx == SHN_COMMON || (!dynamic && type() == STT_COMMON); }
    public boolean isDefined() { return shndx != SHN_UNDEF && shndx != SHN_ABS; }
    public boolean isLocal() { return binding() == STB_LOCAL; }
    public boolean isWeak() { return binding() == STB_WEAK; }
    public boolean isGlobal() { return binding() == STB_GLOBAL; }
    public boolean isHidden() { return visibility() == STV_HIDDEN || visibility() == STV_INTERNAL; }
    public boolean isProtected() { return visibility() == STV_PROTECTED; }

    /** GNU convention: a non-default version definition is stored with "@VERSION" embedded in the name. */
    public String baseName() {
        int at = dynamic ? name.indexOf('@') : -1;
        return at < 0 ? name : name.substring(0, at);
    }

    public String embeddedVersion() {
        if (!dynamic) return null;
        int at = name.indexOf('@');
        return at < 0 ? null : name.substring(at + 1);
    }
}
