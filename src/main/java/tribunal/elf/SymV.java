package tribunal.elf;

public class SymV {
    public String name = "";
    public long value;
    public long size;
    public int bind;
    public int type;
    public int shndx;
    public int visibility;
    public int versionIndex;
    public boolean versionHidden;
    /** 来自 .symtab / .dynsym，便于区分。 */
    public boolean dynamic;
    public transient SymV resolvedVersionOf;

    public boolean isLocal() { return bind == 0; }
    public boolean isGlobal() { return bind == 1; }
    public boolean isWeak() { return bind == 2; }
    public boolean isDefined() { return shndx != 0; }
    public boolean isCommon() { return shndx == 0xfff5; }
    public boolean isAbs() { return shndx == 0xfff1; }

    public String bindName() {
        return switch (bind) { case 0 -> "LOCAL"; case 1 -> "GLOBAL"; case 2 -> "WEAK"; default -> "BIND" + bind; };
    }

    public String visibilityName() {
        return switch (vis()) { case 0 -> "DEFAULT"; case 1 -> "INTERNAL"; case 2 -> "HIDDEN"; case 3 -> "PROTECTED"; default -> "VIS" + vis(); };
    }

    public int vis() { return visibility & 3; }

    public String sectionName(ElfData elf) {
        if (shndx == 0) return "UND";
        if (shndx == 0xfff1) return "ABS";
        if (shndx == 0xfff5) return "COMMON";
        Section s = elf.section(shndx);
        return s == null ? "SEC" + shndx : (s.name == null || s.name.isEmpty() ? "SEC" + shndx : s.name);
    }
}
