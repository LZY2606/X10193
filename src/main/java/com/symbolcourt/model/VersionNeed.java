package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

public class VersionNeed {
    public static class Entry {
        public int index;
        public String name;
        public boolean weak;
    }
    public String file;
    public List<Entry> entries = new ArrayList<>();
}
