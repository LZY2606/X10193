package com.symbolcourt.elf;

import java.util.List;

public class GroupInfo {
    public static final int GRP_COMDAT = 1;

    public final String signature;
    public final boolean comdat;
    public final List<Integer> memberSections;

    public GroupInfo(String signature, boolean comdat, List<Integer> memberSections) {
        this.signature = signature;
        this.comdat = comdat;
        this.memberSections = memberSections;
    }
}
