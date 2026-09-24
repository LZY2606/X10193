package com.symtribunal.elf;

/**
 * One symbol table entry. {@code tableKind} is "dynsym" or "symtab"; local symbols from both
 * tables are kept because they drive relocations and COMDAT signatures.
 */
public class ElfSymbol {
    public int index;
    public String tableKind;
    public String name = "";
    public long value;
    public long size;
    public int binding;
    public int type;
    public int visibility;
    public int sectionIndex;
    public boolean defined;
    public String version;
    public boolean versionHidden;

    public String kindLabel() {
        return Elf.bindingName(binding) + "/" + Elf.typeName(type) + "/" + Elf.visibilityName(visibility);
    }
}
