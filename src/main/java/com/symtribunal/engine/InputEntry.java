package com.symtribunal.engine;

public class InputEntry {
    public String binaryId;
    public String fileName;
    /** Explicit position in the user-supplied load order; lower wins ties. */
    public int order;
    /** Whether this entry participates in lazy archive extraction semantics. */
    public boolean archive;
}
