package gsb.symboltribunal.elf;

public record Sym(
        String name,
        boolean nonEmptyName,
        int bind,
        int type,
        int visibility,
        int shndx,
        long value,
        long size,
        long commonAlign,
        int versionIndex,
        boolean versionHidden,
        boolean dynamic,
        int ordinal) {

    public boolean isUndef() {
        return shndx == ElfFile.SHN_UNDEF;
    }

    public boolean isCommon() {
        return shndx == ElfFile.SHN_COMMON;
    }

    public boolean isDefined() {
        return !isUndef();
    }

    public boolean isLocal() {
        return bind == ElfFile.STB_LOCAL;
    }

    public boolean isWeak() {
        return bind == ElfFile.STB_WEAK;
    }

    public boolean isHiddenLike() {
        return visibility == ElfFile.STV_HIDDEN || visibility == ElfFile.STV_INTERNAL;
    }

    public boolean isProtected() {
        return visibility == ElfFile.STV_PROTECTED;
    }
}
