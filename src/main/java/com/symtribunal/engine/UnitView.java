package com.symtribunal.engine;

import java.util.ArrayList;
import java.util.List;

public class UnitView {
    public String key;
    public String label;
    public String binaryId;
    public String fileName;
    public String member;
    public String kind;
    public String machine;
    public boolean fromArchive;
    public boolean lazyExtracted;
    public List<String> needed = new ArrayList<>();
    public List<String> provides = new ArrayList<>();
    public List<String> discardedComdats = new ArrayList<>();
    public long baseAddress;
}
