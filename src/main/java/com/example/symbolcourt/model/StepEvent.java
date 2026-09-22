package com.example.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

/** One replayable frame of the arbitration timeline. */
public class StepEvent {
    public int seq;
    public String kind;            // EXTRACT / EXTRACT_AMBIGUOUS / COMDAT / COPY_RELOC
    public String title;
    public String detail;
    public String archive;
    public String member;
    public String signature;
    public final List<String> definedSymbols = new ArrayList<>();
    public final List<String> activeUnits = new ArrayList<>();
}
