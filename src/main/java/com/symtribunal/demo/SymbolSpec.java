package com.symtribunal.demo;

/** Declarative symbol used by {@link ElfFileBuilder}. */
public class SymbolSpec {
    public String name;
    public String section;
    public int binding;
    public int type;
    public int visibility;
    public long value;
    public long size;
    public long alignment;
    public String version;
    public boolean versionHidden;
    public boolean common;
    public boolean defined;

    public static SymbolSpec defined(String name, String section, int binding, int type) {
        SymbolSpec spec = new SymbolSpec();
        spec.name = name;
        spec.section = section;
        spec.binding = binding;
        spec.type = type;
        spec.defined = true;
        spec.visibility = 0;
        return spec;
    }

    public static SymbolSpec undefined(String name, int binding) {
        SymbolSpec spec = new SymbolSpec();
        spec.name = name;
        spec.binding = binding;
        spec.type = 0;
        spec.defined = false;
        spec.visibility = 0;
        return spec;
    }

    public SymbolSpec vis(int visibility) {
        this.visibility = visibility;
        return this;
    }

    public SymbolSpec version(String version, boolean hidden) {
        this.version = version;
        this.versionHidden = hidden;
        return this;
    }

    public SymbolSpec value(long value) {
        this.value = value;
        return this;
    }

    public SymbolSpec size(long size) {
        this.size = size;
        return this;
    }

    public SymbolSpec common(long size, long alignment) {
        this.common = true;
        this.defined = true;
        this.size = size;
        this.alignment = alignment;
        return this;
    }
}
