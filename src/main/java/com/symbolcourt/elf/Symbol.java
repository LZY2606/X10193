package com.symbolcourt.elf;

public final class Symbol {
    private final int index;
    private final String name;
    private final int binding;
    private final int type;
    private final int visibility;
    private final short sectionIndex;
    private final long value;
    private final long size;
    private String versionName;
    private boolean versionDefault;
    private boolean versionHidden;
    private boolean versionWeakFlag;
    private boolean versionBaseName;

    public Symbol(int index, String name, int binding, int type, int visibility,
                  short sectionIndex, long value, long size) {
        this.index = index;
        this.name = name;
        this.binding = binding;
        this.type = type;
        this.visibility = visibility;
        this.sectionIndex = sectionIndex;
        this.value = value;
        this.size = size;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public int binding() {
        return binding;
    }

    public int type() {
        return type;
    }

    public int visibility() {
        return visibility;
    }

    public short sectionIndex() {
        return sectionIndex;
    }

    public long value() {
        return value;
    }

    public long size() {
        return size;
    }

    public String versionName() {
        return versionName;
    }

    public boolean versionDefault() {
        return versionDefault;
    }

    public boolean versionHidden() {
        return versionHidden;
    }

    public boolean versionWeakFlag() {
        return versionWeakFlag;
    }

    public boolean versionBaseName() {
        return versionBaseName;
    }

    public void setVersion(String name, boolean isDefault, boolean hidden,
                           boolean weakFlag, boolean baseName) {
        this.versionName = name;
        this.versionDefault = isDefault;
        this.versionHidden = hidden;
        this.versionWeakFlag = weakFlag;
        this.versionBaseName = baseName;
    }

    public boolean isUndefined() {
        return sectionIndex == 0 && type != ElfConstants.STT_COMMON;
    }

    public boolean isCommon() {
        return (sectionIndex & 0xffff) == (ElfConstants.SHN_COMMON & 0xffff)
                || type == ElfConstants.STT_COMMON;
    }

    public boolean isLocal() {
        return binding == ElfConstants.STB_LOCAL;
    }

    public boolean isWeak() {
        return binding == ElfConstants.STB_WEAK;
    }

    public String versionKey() {
        if (versionHidden || versionName == null) {
            return "";
        }
        return versionName;
    }

    public String qualifiedName() {
        if (versionName == null || versionName.isEmpty() || versionHidden) {
            return name;
        }
        return name + "@" + versionName + (versionDefault ? "" : " (non-default)");
    }
}
