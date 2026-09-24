package com.symtribunal.engine;

import java.util.ArrayList;
import java.util.List;

/** One playable step of the tribunal: archive extraction, COMDAT ruling or symbol table update. */
public class StepEvent {
    public int step;
    public String kind;
    public String title;
    public String detail;
    public String unitKey;
    public String symbol;
    public List<String> symbolTable = new ArrayList<>();
    public List<String> activeUnits = new ArrayList<>();
}
