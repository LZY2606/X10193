package com.symbolcourt.model;

import static com.symbolcourt.model.Enums.*;

public class ElfSymbol {
    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public String name;
    public Binding binding;
    public SymType type;
    public Visibility visibility;
    public int shndx = SHN_UNDEF;
    public long value;
    public long size;
    public boolean dynamic;
    public int rawVersionIndex = 1;
    public String sectionName;

    public String versionName;
    public boolean versionDefault = true;
    public boolean versionHidden;
    public String neededFrom;

    public boolean isDefined() {
        return shndx != SHN_UNDEF;
    }

    public boolean isCommon() {
        return shndx == SHN_COMMON;
    }

    public boolean isExportedVersioned() {
        if (!dynamic) return true;
        return rawVersionIndex != 0 && !versionHidden;
    }

    public boolean isScopedToObject() {
        return binding == Binding.LOCAL
                || visibility == Visibility.HIDDEN
                || visibility == Visibility.INTERNAL;
    }

    public String versionedName() {
        return versionName == null ? name : name + "@" + versionName;
    }
}
