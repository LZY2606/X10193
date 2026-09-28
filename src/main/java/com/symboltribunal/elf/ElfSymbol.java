package com.symboltribunal.elf;

public class ElfSymbol {
    public final int index;
    public final String name;
    public final int info;
    public final int other;
    public final int shndx;
    public final long value;
    public final long size;

    public ElfSymbol(int index, String name, int info, int other, int shndx, long value, long size) {
        this.index = index;
        this.name = name;
        this.info = info;
        this.other = other;
        this.shndx = shndx;
        this.value = value;
        this.size = size;
    }

    public int bind() {
        return Elf.stBind(info);
    }

    public int type() {
        return Elf.stType(info);
    }

    public int visibility() {
        return Elf.stVisibility(other);
    }

    public boolean isUndef() {
        return shndx == Elf.SHN_UNDEF;
    }

    public boolean isCommon() {
        return shndx == Elf.SHN_COMMON || (type() == Elf.STT_COMMON && shndx == Elf.SHN_UNDEF);
    }

    public boolean isDefined() {
        return !isUndef();
    }

    public boolean isExported() {
        return bind() != Elf.STB_LOCAL && shndx != Elf.SHN_UNDEF;
    }

    public String bindingLabel() {
        return Elf.bindName(bind());
    }

    public String visibilityLabel() {
        return Elf.visibilityName(visibility());
    }
}
