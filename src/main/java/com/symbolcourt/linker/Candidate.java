package com.symbolcourt.linker;

import com.symbolcourt.model.Sym;

/** 一个符号的候选提供者。 */
public class Candidate {
    public String objectName;
    public String memberName;
    public String provider;
    public Sym symbol;
    public boolean staticDef;
    public boolean dynamic;
    public boolean common;
    public boolean comdatDiscarded;
    public boolean archiveExtracted;
    public boolean copyRelocation;
    public String versionName;
    public boolean versionDefault;
    public Integer visibility;
    public Integer bind;
    public int rank;
    public long simulatedAddress;
    public Reason reason;
    public String detail = "";

    public String label() {
        return memberName != null ? objectName + "(" + memberName + ")" : objectName;
    }
}
