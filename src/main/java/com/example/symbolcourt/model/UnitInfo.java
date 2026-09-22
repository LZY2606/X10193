package com.example.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

public class UnitInfo {
    public int ordinal;
    public String name;
    public String kind;            // OBJECT / SHARED / EXECUTABLE / ARCHIVE / CORRUPT
    public String container;       // archive name for members
    public boolean active;         // currently participating
    public boolean extracted;      // lazily extracted archive member
    public String soname;
    public String parseError;
    public final List<String> needed = new ArrayList<>();
}
