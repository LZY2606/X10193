package com.symbolcourt.resolve;

import com.symbolcourt.model.Enums.RelocKind;

public class RelocationDecision {
    public String unit;
    public String sectionName;
    public String targetSection;
    public long offset;
    public String typeName;
    public RelocKind kind;
    public String symbolName;
    public String requestedVersion;
    public Long addend;
    public String status;
    public String provider;
    public String explanation;
    public boolean satisfied;
    public boolean versionMatched;
    public Long boundValue;
}
