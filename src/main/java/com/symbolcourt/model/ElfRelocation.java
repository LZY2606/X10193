package com.symbolcourt.model;

import static com.symbolcourt.model.Enums.RelocKind;

public class ElfRelocation {
    public String sectionName;
    public String targetSectionName;
    public long offset;
    public int type;
    public String typeName;
    public int symIndex;
    public String symbolName;
    public Long addend;
    public boolean dynamic;
    public RelocKind kind = RelocKind.REGULAR;
}
