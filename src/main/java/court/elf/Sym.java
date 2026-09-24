package court.elf;

/** One ELF symbol entry from .symtab or .dynsym. */
public final class Sym {
    public int index;
    public String name;
    public long value;
    public long size;
    public int info;
    public int other;
    public short shndx;
    public int bind;
    public int type;
    public int visibility;
    public boolean dynamic;
    public ElfObject.Section section;
    public String versionName;
    public int rawVersionIndex;
    /** Engine bookkeeping: false when the defining COMDAT group was discarded. */
    public boolean live = true;
    public String eliminatedReason;

    public String bindName() {
        return switch (bind) {
            case 0 -> "LOCAL";
            case 1 -> "GLOBAL";
            case 2 -> "WEAK";
            case 10 -> "GNU_UNIQUE";
            default -> "BIND" + bind;
        };
    }

    public String typeName() {
        return switch (type) {
            case 0 -> "NOTYPE";
            case 1 -> "OBJECT";
            case 2 -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            case 6 -> "TLS";
            case 10 -> "IFUNC";
            default -> "TYPE" + type;
        };
    }

    public String visibilityName() {
        return switch (visibility) {
            case 0 -> "DEFAULT";
            case 1 -> "INTERNAL";
            case 2 -> "HIDDEN";
            case 3 -> "PROTECTED";
            default -> "VIS" + visibility;
        };
    }
}
