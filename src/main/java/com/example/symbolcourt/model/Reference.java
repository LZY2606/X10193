package com.example.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

public class Reference {
    public int id;
    public String from;
    public String symbol;
    public String version;          // required version, may be null
    public boolean defaultVersion = true;
    public String refKind;          // STRONG_UNDEF / WEAK_UNDEF / SELF_REF
    public String status;           // RESOLVED / AMBIGUOUS / UNRESOLVED / WEAK_NULL / COPY_RELOC / REDIRECTED / LOCAL
    public String chosen;
    public String note;
    public final List<Candidate> candidates = new ArrayList<>();
    public final List<RelocView> relocations = new ArrayList<>();
}
