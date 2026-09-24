package com.symbolcourt.resolve;

import com.symbolcourt.model.ElfSymbol;
import com.symbolcourt.model.Enums.Binding;

public class Candidate {
    public InputUnit unit;
    public ElfSymbol symbol;
    public SymbolKey key;
    public Binding binding;
    public boolean common;
    public boolean defined;
    public boolean hidden;
    public boolean protectedVis;
    public boolean shared;
    public long value;
    public long size;
    public String sectionName;
    public int scanPos;
    public int pass;
    public boolean fromArchiveMember;

    public String unitLabel() {
        return unit.label();
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(unit.label()).append(" : ").append(symbol.versionedName());
        sb.append(" [").append(binding);
        if (common) sb.append(", COMMON");
        if (symbol.type != null) sb.append(", ").append(symbol.type);
        if (hidden) sb.append(", HIDDEN");
        if (protectedVis) sb.append(", PROTECTED");
        if (shared) sb.append(", shared");
        sb.append("]");
        return sb.toString();
    }
}
