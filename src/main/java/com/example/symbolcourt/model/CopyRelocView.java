package com.example.symbolcourt.model;

public class CopyRelocView {
    public String symbol;
    public String owner;       // executable unit
    public String from;        // DSO provider
    public String version;
    public long size;
    public String status;      // COPY / ERROR
    public String reason;
}
