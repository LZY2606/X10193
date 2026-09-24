package com.symbolcourt.resolve;

import java.util.ArrayList;
import java.util.List;

public class Step {
    public enum Kind { SCAN_OBJECT, EXTRACT_MEMBER, CHOOSE_SYMBOL, REJECT_CANDIDATE,
        COMMON_PROMOTE, COMDAT_DISCARD, MULTIPLE_DEFINITION, COPY_RELOC, RESOLVE_RELOC,
        UNSATISFIED_VERSION, CORRUPT_SKIP, SHARED_PROVIDER }

    public int index;
    public Kind kind;
    public String title;
    public String detail;
    public String unit;
    public String symbol;
    public List<String> facts = new ArrayList<>();

    public Step(int index, Kind kind, String title, String detail) {
        this.index = index;
        this.kind = kind;
        this.title = title;
        this.detail = detail;
    }
}
