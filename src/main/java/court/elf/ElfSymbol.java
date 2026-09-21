package court.elf;

/** One symbol table entry. {@code versionIndex} is the raw .gnu.version index. */
public class ElfSymbol {
    public int index;
    public String name;
    public long value;
    public long size;
    public int info;
    public int other;
    public int shndx;
    public boolean dynamic;
    public int versionIndex = Const.VER_NDX_GLOBAL;

    public int binding() { return info >> 4; }
    public int type() { return info & 0xf; }
    public int visibility() { return other & 0x3; }
    public boolean isUndef() { return shndx == Const.SHN_UNDEF; }
    public boolean isCommon() { return shndx == Const.SHN_COMMON || type() == Const.STT_COMMON; }
    public boolean isHiddenOrInternal() {
        int v = visibility();
        return v == Const.STV_HIDDEN || v == Const.STV_INTERNAL;
    }
    public boolean isProtected() { return visibility() == Const.STV_PROTECTED; }
}
