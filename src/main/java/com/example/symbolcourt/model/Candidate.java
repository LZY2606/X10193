package com.example.symbolcourt.model;

public class Candidate {
    public String unit;
    public String kind;             // OBJECT / SHARED / EXECUTABLE
    public String bind;
    public String type;
    public String visibility;
    public String version;          // null = unversioned
    public boolean defaultVersion;
    public boolean common;
    public String value;
    public long size;
    public String status;           // CHOSEN / AMBIGUOUS / ELIMINATED
    public String reason;

    public Candidate(String unit) {
        this.unit = unit;
    }
}
