package com.symbolcourt.linker;

import com.symbolcourt.model.Reloc;

import java.util.ArrayList;
import java.util.List;

/** 一条未定义符号引用（同一对象同一符号同一版本的重定位聚合）。 */
public class Reference {
    public String objectName;
    public String memberName;
    public String symName;
    public int bind;
    public String requiredVersion;
    public boolean versionedRef;
    public boolean dynamicRef;
    public List<Candidate> candidates = new ArrayList<>();
    public Candidate winner;
    public boolean ambiguous;
    public boolean unmetVersion;
    public boolean copyRelocation;
    public boolean nullWeak;
    public Reason outcome;
    public String outcomeDetail = "";
    public List<Reloc> relocations = new ArrayList<>();
    public long simulatedAddress;

    public String sourceLabel() {
        return memberName != null ? objectName + "(" + memberName + ")" : objectName;
    }

    public String key() {
        return sourceLabel() + "::" + symName + (versionedRef ? "@" + requiredVersion : "");
    }
}
