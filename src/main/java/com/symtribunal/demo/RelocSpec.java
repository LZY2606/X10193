package com.symtribunal.demo;

public class RelocSpec {
    public String symbol;
    public int type;
    public long offset;
    public long addend;
    public boolean rela;

    public static RelocSpec of(String symbol, int type, long offset) {
        RelocSpec spec = new RelocSpec();
        spec.symbol = symbol;
        spec.type = type;
        spec.offset = offset;
        spec.rela = true;
        return spec;
    }

    public RelocSpec addend(long addend) {
        this.addend = addend;
        return this;
    }
}
