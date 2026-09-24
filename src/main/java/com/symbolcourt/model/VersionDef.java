package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

public class VersionDef {
    public int index;
    public String name;
    public int flags;
    public boolean base;
    public List<String> aliases = new ArrayList<>();
}
